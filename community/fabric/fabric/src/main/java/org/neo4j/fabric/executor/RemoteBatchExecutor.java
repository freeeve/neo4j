/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.fabric.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.Records;
import org.neo4j.fabric.stream.summary.PlanlessSummary;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.scheduler.CallableExecutor;

public class RemoteBatchExecutor {

    private final CallableExecutor executor;
    private final Function<Record, FragmentResult> fragmentExecutor;
    private final int bufferSize;
    private final int concurrency;

    public RemoteBatchExecutor(
            CallableExecutor executor,
            Function<Record, FragmentResult> fragmentExecutor,
            int bufferSize,
            int concurrency) {
        this.executor = executor;
        this.fragmentExecutor = fragmentExecutor;
        this.bufferSize = bufferSize;
        this.concurrency = concurrency;
    }

    /**
     * Concurrently executes a batch of input records.
     * The submitted batch size should not exceed {@link #batchSize()}.
     */
    FragmentResult execute(List<Record> batchInput, boolean unitInner) {
        if (batchInput.isEmpty()) {
            throw new IllegalArgumentException("batchInput is empty");
        }

        AtomicBoolean streamingAborted = new AtomicBoolean(false);
        BlockingQueue<RemoteStreamEvent> queue = new ArrayBlockingQueue<>(bufferSize);

        List<Future<FragmentResultWithInput>> futures = batchInput.stream()
                .map(record -> executor.submit(() -> {
                    var fragmentResult = fragmentExecutor.apply(record);
                    return new FragmentResultWithInput(fragmentResult, record);
                }))
                .toList();

        List<FragmentResultWithInput> allResults = getAllResults(futures);
        allResults.forEach(fragmentResultWithInput -> startStreaming(
                queue,
                streamingAborted,
                fragmentResultWithInput.fragmentResult,
                fragmentResultWithInput.inputRecord,
                unitInner));
        return new RemoteBatch(
                allResults.stream().map(FragmentResultWithInput::fragmentResult).toList(), queue, streamingAborted);
    }

    private List<FragmentResultWithInput> getAllResults(List<Future<FragmentResultWithInput>> futures) {
        List<FragmentResultWithInput> results = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (var future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                // The code submitted to the executor does not throw checked exceptions,
                // so the cast is safe
                failures.add((RuntimeException) e.getCause());
            } catch (InterruptedException e) {
                failures.add(new RuntimeException(e));
            }
        }

        if (!failures.isEmpty()) {
            handleCollectedErrors(failures);
        }

        return results;
    }

    int batchSize() {
        return concurrency;
    }

    private void startStreaming(
            BlockingQueue<RemoteStreamEvent> queue,
            AtomicBoolean streamingAborted,
            FragmentResult fragmentResult,
            Record inputRecord,
            boolean unitInner) {
        executor.execute(new RemoteStreamWorker(queue, streamingAborted, fragmentResult, inputRecord, unitInner));
    }

    private static void handleCollectedErrors(List<RuntimeException> failures) {
        if (failures.isEmpty()) {
            return;
        }

        // The reason why we collect all the errors instead of just throwing the first
        // one is the following:
        // Cypher runtime terminates a transaction when an exception is thrown.
        // This means that any other query executing in the same transaction will
        // throw 'transaction terminated' exception sooner or later.
        // That is a bit problematic, because those secondary 'transaction terminated' errors
        // are in a race with the original error towards the consumer and there is a realistic
        // chance they might make to the consumer before the original error.
        // So the point of collecting all errors is to try to find the best one, which currently
        // means not 'transaction terminated' error if such exists.
        throw failures.stream()
                .filter(e -> (e instanceof Status.HasStatus exceptionWithStatus
                        && exceptionWithStatus.status() != Status.Transaction.Terminated))
                .findAny()
                .orElseGet(failures::getFirst);
    }

    /**
     * A worker that tries to exhaust a remote stream.
     */
    private static class RemoteStreamWorker implements Runnable {

        private final BlockingQueue<RemoteStreamEvent> queue;
        private final AtomicBoolean streamingAborted;
        private final FragmentResult fragmentResult;
        private final Record inputRecord;
        private final boolean unitInner;

        private RemoteStreamWorker(
                BlockingQueue<RemoteStreamEvent> queue,
                AtomicBoolean streamingAborted,
                FragmentResult fragmentResult,
                Record inputRecord,
                boolean unitInner) {
            this.queue = queue;
            this.streamingAborted = streamingAborted;
            this.fragmentResult = fragmentResult;
            this.inputRecord = inputRecord;
            this.unitInner = unitInner;
        }

        @Override
        public void run() {
            while (!streamingAborted.get()) {
                Record record;
                try {
                    record = fragmentResult.next();
                } catch (RuntimeException e) {
                    enqueue(new RemoteStreamEvent.Failure(e));
                    return;
                }
                if (record == null) {
                    // We have either exhausted an inner stream producing records
                    // or a unit (void) inner stream.
                    // We have to produce the input record in the latter case.
                    if (unitInner) {
                        enqueue(new RemoteStreamEvent.Data(inputRecord));
                    }

                    enqueue(new RemoteStreamEvent.StreamExhausted());
                    return;
                }

                enqueue(new RemoteStreamEvent.Data(Records.join(inputRecord, record)));
            }
        }

        private void enqueue(RemoteStreamEvent event) {
            try {
                while (!queue.offer(event, 100, TimeUnit.MICROSECONDS)) {
                    if (streamingAborted.get()) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class RemoteBatch implements FragmentResult {

        private final BlockingQueue<RemoteStreamEvent> queue;
        private final AtomicBoolean streamingAborted;
        private final List<String> columns;
        // A concurrency note:
        // Since the results streams are consumed by worker threads,
        // calling this might cause concurrency issues. However,
        // we rely on the fact that reading records and invoking
        // consume is synchronized in RemoteStatementResult and
        // AutocommitRemoteStatementResult.
        private final Supplier<PlanlessSummary> combinedSummaries;
        private int activeRemoteStreams;
        private final List<RuntimeException> failures = new ArrayList<>();

        private RemoteBatch(
                List<FragmentResult> results, BlockingQueue<RemoteStreamEvent> queue, AtomicBoolean streamingAborted) {
            this.queue = queue;
            this.streamingAborted = streamingAborted;
            this.activeRemoteStreams = results.size();
            columns = results.get(0).columns();
            combinedSummaries = () -> results.stream()
                    .map(FragmentResult::consume)
                    .reduce(PlanlessSummary::merge)
                    .get();
        }

        @Override
        public List<String> columns() {
            return columns;
        }

        @Override
        public Record next() {
            while (true) {
                RemoteStreamEvent event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                switch (event) {
                    case RemoteStreamEvent.Data data -> {
                        return data.record;
                    }

                    case RemoteStreamEvent.Failure failure -> {
                        failures.add(failure.e);
                        activeRemoteStreams--;
                        if (activeRemoteStreams == 0) {
                            handleCollectedErrors();
                            return null;
                        }
                    }
                    case RemoteStreamEvent.StreamExhausted exhaustedEvent -> {
                        activeRemoteStreams--;
                        if (activeRemoteStreams == 0) {
                            handleCollectedErrors();
                            return null;
                        }
                    }
                }
            }
        }

        @Override
        public PlanlessSummary consume() {
            streamingAborted.set(true);
            return combinedSummaries.get();
        }

        @Override
        public QueryExecutionType executionType() {
            // The execution type for this type of fragment is currently
            // figured out from a plan, so we don't need to bother trying
            // to somehow combine the execution types of the remote streams.
            throw new UnsupportedOperationException();
        }

        private void handleCollectedErrors() {
            RemoteBatchExecutor.handleCollectedErrors(failures);
        }
    }

    private sealed interface RemoteStreamEvent {

        record Data(Record record) implements RemoteStreamEvent {}

        /**
         * This means that one of the remote stream has been exhausted,
         * not that the combined result stream is done.
         * The consumer of the event must count the consumed remote streams
         * (meaning fully exhausted or returning an error)
         * in order to figure out if all of them have been consumed and therefore
         * the combined result stream is done.
         */
        record StreamExhausted() implements RemoteStreamEvent {}

        /**
         * Similarly to {@link StreamExhausted}, this means that one of the remote streams
         * returned an error and should not be interpreted as that the combined result stream is done.
         * The consumer of the event must count the consumed remote streams
         * (meaning fully exhausted or returning an error)
         * in order to figure out if all of them have been consumed and therefore
         * the combined result stream is done.
         */
        record Failure(RuntimeException e) implements RemoteStreamEvent {}
    }

    private record FragmentResultWithInput(FragmentResult fragmentResult, Record inputRecord) {}
}

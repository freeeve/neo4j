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
import java.util.function.Function;
import java.util.function.Supplier;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.Records;
import org.neo4j.fabric.stream.summary.PlanlessSummary;
import org.neo4j.graphdb.QueryExecutionType;

/**
 * An executor of 'apply' (subquery) fragment.
 * It executes the subqueries targeting remote locations
 * in concurrent batches.
 * <p>
 * It does not execute 'local' subqueries concurrently.
 * There are 2 reasons for that:
 * <ol>
 *   <li>The Fabric local executor is not ready for the option of
 *     multiple queries targeting the same Kernel transaction at the same time.
 *   </li>
 *   <li>Virtual threads are used to achieve the concurrency. We are currently not sure
 *     if it is safe to let virtual threads into Kernel and Cypher runtimes, because
 *     of usage of thread locals there and it the libraries used there.
 *   </li>
 * </ol>
 * Historically, local subqueries were not executed concurrently, so this limitation is not a regression
 * (point 1. above being a proof fo that)
 */
public class ApplyExecutor implements FragmentResult {

    private final List<Supplier<PlanlessSummary>> summaries = new ArrayList<>();
    private final List<String> columns;
    private final ExtendedInput input;
    private final boolean unitInner;
    private final QueryExecutionType queryExecutionType;
    private final Function<Record, FragmentResult> fragmentExecutor;
    private final RemoteBatchExecutor remoteBatchExecutor;
    private FragmentResult currentBatch;

    public ApplyExecutor(
            List<String> columns,
            FragmentResult input,
            boolean unitInner,
            QueryExecutionType queryExecutionType,
            RemoteBatchExecutor remoteBatchExecutor,
            Function<Record, FragmentResult> fragmentExecutor,
            Function<Record, Boolean> targetsRemote) {
        this.columns = columns;
        this.input = new ExtendedInput(input, targetsRemote);
        this.unitInner = unitInner;
        this.queryExecutionType = queryExecutionType;
        this.remoteBatchExecutor = remoteBatchExecutor;
        this.fragmentExecutor = fragmentExecutor;
        summaries.add(this.input::consume);
    }

    @Override
    public List<String> columns() {
        return columns;
    }

    @Override
    public Record next() {
        if (currentBatch != null) {
            Record record = currentBatch.next();
            if (record != null) {
                return record;
            }

            currentBatch = null;
        }

        var nextInputRecord = input.peek();
        if (nextInputRecord == null) {
            return null;
        }

        if (nextInputRecord.targetsRemote) {
            currentBatch = remoteBatch();
        } else {
            var inputRecord = input.next();
            var inner = fragmentExecutor.apply(inputRecord.record);
            currentBatch = new LocalBatch(inputRecord.record, inner);
        }

        summaries.add(currentBatch::consume);
        // We have just produced a new batch, so let's go again.
        return next();
    }

    private FragmentResult remoteBatch() {
        List<Record> batchInput = new ArrayList<>();
        // Try creating a batch of an input records with target location
        // that evaluates to 'remote'
        // We either manage to crate a batch up to the max batch size,
        // or we come across an input records with target location
        // that evaluates to 'local'.
        // A third option s exhausting the input stream.
        for (int i = 0; i < remoteBatchExecutor.batchSize(); i++) {
            var inputRecordWithLocation = input.peek();
            if (inputRecordWithLocation == null) {
                break;
            }
            if (inputRecordWithLocation.targetsRemote) {
                batchInput.add(input.next().record);
            } else {
                break;
            }
        }

        return remoteBatchExecutor.execute(batchInput, unitInner);
    }

    @Override
    public PlanlessSummary consume() {
        return summaries.stream()
                .map(Supplier::get)
                .reduce(PlanlessSummary::merge)
                .orElse(null);
    }

    @Override
    public QueryExecutionType executionType() {
        return queryExecutionType;
    }

    private static class ExtendedInput {

        private final FragmentResult input;
        private final Function<Record, Boolean> targetsRemote;

        private InputRecordWithLocation buffered;
        private boolean exhausted = false;

        private ExtendedInput(FragmentResult input, Function<Record, Boolean> targetsRemote) {
            this.input = input;
            this.targetsRemote = targetsRemote;
        }

        InputRecordWithLocation peek() {
            if (buffered != null) {
                return buffered;
            }

            if (exhausted) {
                return null;
            }

            buffered = readInput();
            if (buffered == null) {
                exhausted = true;
            }
            return buffered;
        }

        InputRecordWithLocation next() {
            if (buffered != null) {
                var result = buffered;
                buffered = null;
                return result;
            }

            if (exhausted) {
                return null;
            }

            return readInput();
        }

        private InputRecordWithLocation readInput() {
            var inputRecord = input.next();
            if (inputRecord == null) {
                return null;
            }

            return new InputRecordWithLocation(inputRecord, targetsRemote.apply(inputRecord));
        }

        PlanlessSummary consume() {
            return input.consume();
        }
    }

    private class LocalBatch implements FragmentResult {

        private final Record inputRecord;
        private final FragmentResult innerResult;
        private boolean exhausted = false;

        LocalBatch(Record inputRecord, FragmentResult innerResult) {
            this.inputRecord = inputRecord;
            this.innerResult = innerResult;
        }

        @Override
        public List<String> columns() {
            return columns;
        }

        @Override
        public Record next() {
            Record innerRecord = innerResult.next();
            if (innerRecord != null) {
                return Records.join(inputRecord, innerRecord);
            } else {
                // We have either exhausted an inner stream producing records
                // or a unit (void) inner stream.
                // We have to produce the input record in the latter case.
                if (unitInner && !exhausted) {
                    exhausted = true;
                    return inputRecord;
                }

                return null;
            }
        }

        @Override
        public PlanlessSummary consume() {
            return innerResult.consume();
        }

        @Override
        public QueryExecutionType executionType() {
            return innerResult.executionType();
        }
    }

    private record InputRecordWithLocation(Record record, boolean targetsRemote) {}
}

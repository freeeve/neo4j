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
package org.neo4j.fabric.stream;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.neo4j.fabric.stream.summary.Summary;
import org.neo4j.graphdb.QueryExecutionType;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This is a temporary utility for connecting StatementResult and BlockingStatementResult
 * together in the places where the old reactive code and the new blocking code meet.
 * It will be removed when the reactive code is gone.
 */
public class Blocking2RxResultAdapter {

    public static Mono<StatementResult> adapt(Executor executor, Supplier<BlockingStatementResult> blockingOperation) {
        return blocking2Mono(executor, () -> new StatementResultImpl(blockingOperation.get(), executor));
    }

    private static <T> Mono<T> blocking2Mono(Executor executor, Supplier<T> blockingOperation) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.execute(() -> {
            try {
                var result = blockingOperation.get();
                future.complete(result);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });

        return Mono.fromFuture(future);
    }

    private static class StatementResultImpl implements StatementResult {

        private final BlockingStatementResult blockingStatementResult;
        private final Executor executor;

        private StatementResultImpl(BlockingStatementResult blockingStatementResult, Executor executor) {
            this.blockingStatementResult = blockingStatementResult;
            this.executor = executor;
        }

        @Override
        public List<String> columns() {
            return blockingStatementResult.columns();
        }

        @Override
        public Flux<Record> records() {
            return Flux.from(new RecordPublisher(executor, blockingStatementResult));
        }

        @Override
        public Mono<Summary> summary() {
            return blocking2Mono(executor, blockingStatementResult::consume);
        }

        @Override
        public Mono<QueryExecutionType> executionType() {
            return Mono.just(blockingStatementResult.executionType());
        }
    }

    private static class RecordPublisher implements Publisher<Record> {

        private final Executor executor;
        private final BlockingStatementResult blockingStatementResult;

        private RecordPublisher(Executor executor, BlockingStatementResult blockingStatementResult) {
            this.executor = executor;
            this.blockingStatementResult = blockingStatementResult;
        }

        @Override
        public void subscribe(Subscriber<? super Record> subscriber) {
            subscriber.onSubscribe(new Subscription() {

                final ReentrantLock lock = new ReentrantLock();
                long requestedCounter = 0;
                boolean cancelled = false;

                @Override
                public void request(long l) {
                    try {
                        lock.lock();
                        boolean producing = requestedCounter != 0;
                        requestedCounter += l;
                        if (!producing) {
                            executor.execute(() -> {
                                while (true) {
                                    Record record;
                                    try {
                                        record = blockingStatementResult.next();
                                    } catch (RuntimeException e) {
                                        try {
                                            lock.lock();
                                            subscriber.onError(e);
                                            break;
                                        } finally {
                                            lock.unlock();
                                        }
                                    }

                                    try {
                                        lock.lock();
                                        if (cancelled) {
                                            return;
                                        }
                                        if (record == null) {
                                            subscriber.onComplete();
                                            return;
                                        }

                                        requestedCounter--;
                                        subscriber.onNext(record);
                                        if (requestedCounter == 0) {
                                            break;
                                        }
                                    } finally {
                                        lock.unlock();
                                    }
                                }
                            });
                        }

                    } finally {
                        lock.unlock();
                    }
                }

                @Override
                public void cancel() {
                    try {
                        lock.lock();
                        cancelled = true;
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }
    }
}

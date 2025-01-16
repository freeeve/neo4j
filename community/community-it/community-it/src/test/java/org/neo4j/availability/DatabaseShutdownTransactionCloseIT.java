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
package org.neo4j.availability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TransactionTerminatedException;
import org.neo4j.graphdb.event.TransactionData;
import org.neo4j.graphdb.event.TransactionEventListenerAdapter;
import org.neo4j.kernel.database.DatabaseMonitors;
import org.neo4j.kernel.impl.api.ShutdownTransactionMonitor;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.Inject;

@DbmsExtension
public class DatabaseShutdownTransactionCloseIT {

    @Inject
    DatabaseManagementService managementService;

    @Inject
    GraphDatabaseService databaseService;

    @Inject
    DatabaseMonitors databaseMonitors;

    @Test
    void ableToCloseTransactionAfterShutdownStarted() throws InterruptedException {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        CountDownLatch transactionCompleted = new CountDownLatch(1);
        CountDownLatch continueTransactionLatch = new CountDownLatch(1);

        databaseMonitors.addMonitorListener(new ShutdownTransactionMonitor() {

            @Override
            public void awaitActiveTransactionClose() {
                continueTransactionLatch.countDown();
                try {
                    transactionCompleted.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var shutdownFuture = executor.submit(() -> {
                try {
                    shutdownLatch.await();
                    managementService.shutdown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // we still should be able to commit our transaction that was running during the shutdown but was
            // able to complete during the shutdown timeout
            try {
                assertDoesNotThrow(() -> {
                    try (var tx = databaseService.beginTx()) {
                        tx.createNode();

                        // notify shutdown to proceed
                        shutdownLatch.countDown();
                        // wait until we will be actually waiting for closed transaction as part of shutdown
                        continueTransactionLatch.await();

                        tx.commit();
                    }
                });
            } finally {
                transactionCompleted.countDown();
            }

            assertDoesNotThrow(() -> shutdownFuture.get());
        }
    }

    @Test
    void terminateTransactionOnShutdown() throws InterruptedException {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        CountDownLatch transactionCompleted = new CountDownLatch(1);
        CountDownLatch continueTransactionLatch = new CountDownLatch(1);

        databaseMonitors.addMonitorListener(new ShutdownTransactionMonitor() {

            @Override
            public void awaitTerminatedTransactionClose() {
                continueTransactionLatch.countDown();
                try {
                    transactionCompleted.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var shutdownFuture = executor.submit(() -> {
                try {
                    shutdownLatch.await();
                    managementService.shutdown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // here we are non reacting transaction that will be terminated
            try {
                assertThatThrownBy(() -> {
                            try (var tx = databaseService.beginTx()) {
                                tx.createNode();

                                // notify shutdown to proceed
                                shutdownLatch.countDown();
                                // wait until we will be actually waiting for closed transaction as part of shutdown
                                continueTransactionLatch.await();

                                tx.commit();
                            }
                        })
                        .rootCause()
                        .isInstanceOf(TransactionTerminatedException.class);
            } finally {
                transactionCompleted.countDown();
            }
            assertDoesNotThrow(() -> shutdownFuture.get());
        }
    }

    @Test
    void awaitClosingWithMarkedAsTerminatedTransactionOnShutdown() throws InterruptedException {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        CountDownLatch continueTransactionLatch = new CountDownLatch(1);
        AtomicBoolean listenerEnabled = new AtomicBoolean();

        databaseMonitors.addMonitorListener(new ShutdownTransactionMonitor() {

            @Override
            public void awaitClosingTransactionClose() {
                // this test only notifies closing transaction there is no need to wait for completion since we should
                // be waiting indefinitely for that
                continueTransactionLatch.countDown();
            }
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var shutdownFuture = executor.submit(() -> {
                try {
                    shutdownLatch.await();
                    managementService.shutdown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            managementService.registerTransactionEventListener(
                    databaseService.databaseName(), new TransactionEventListenerAdapter<Void>() {
                        @Override
                        public Void beforeCommit(
                                TransactionData data, Transaction transaction, GraphDatabaseService databaseService)
                                throws Exception {
                            if (listenerEnabled.get()) {
                                continueTransactionLatch.await();
                            }
                            return null;
                        }
                    });
            // in this state transactions are closing but in fact they will fail to do anything in the end
            assertThatThrownBy(() -> {
                        try (var tx = databaseService.beginTx()) {
                            tx.createNode();

                            // notify shutdown to proceed
                            shutdownLatch.countDown();
                            // enable listener that will block until database will wait for closing transactions
                            listenerEnabled.set(true);
                            tx.commit();
                        }
                    })
                    .rootCause()
                    .isInstanceOf(TransactionTerminatedException.class);

            assertDoesNotThrow(() -> shutdownFuture.get());
        }
    }

    @Test
    void awaitClosingAndNotMarkedAsTerminatedTransactionOnShutdown() throws InterruptedException {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        CountDownLatch continueTransactionLatch = new CountDownLatch(1);
        AtomicBoolean listenerEnabled = new AtomicBoolean();

        databaseMonitors.addMonitorListener(new ShutdownTransactionMonitor() {

            @Override
            public void awaitClosingTransactionClose() {
                // this test only notifies closing transaction there is no need to wait for completion since we should
                // be waiting indefinitely for that
                continueTransactionLatch.countDown();
            }
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var shutdownFuture = executor.submit(() -> {
                try {
                    shutdownLatch.await();
                    managementService.shutdown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            managementService.registerTransactionEventListener(
                    databaseService.databaseName(), new TransactionEventListenerAdapter<Void>() {

                        @Override
                        public void afterCommit(
                                TransactionData data, Void state, GraphDatabaseService databaseService) {
                            if (listenerEnabled.get()) {
                                try {
                                    continueTransactionLatch.await();
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    });

            assertDoesNotThrow(() -> {
                try (var tx = databaseService.beginTx()) {
                    tx.createNode();

                    // notify shutdown to proceed
                    shutdownLatch.countDown();
                    // enable listener that will block until database will wait for closing transactions
                    listenerEnabled.set(true);
                    tx.commit();
                }
            });

            assertDoesNotThrow(() -> shutdownFuture.get());
        }
    }
}

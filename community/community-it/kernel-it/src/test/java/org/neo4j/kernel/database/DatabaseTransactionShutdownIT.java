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
package org.neo4j.kernel.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.shutdown_terminated_transaction_wait_timeout;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.configuration.GraphDatabaseSettings.shutdown_transaction_end_timeout;
import static org.neo4j.logging.AssertableLogProvider.Level.WARN;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.DatabaseShutdownException;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.TransactionTerminatedException;
import org.neo4j.graphdb.TransientTransactionFailureException;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.availability.AvailabilityGuard;
import org.neo4j.kernel.availability.AvailabilityListener;
import org.neo4j.kernel.impl.api.KernelTransactions;
import org.neo4j.kernel.impl.api.ShutdownTransactionMonitor;
import org.neo4j.kernel.impl.coreapi.TransactionImpl;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;
import org.neo4j.test.OtherThreadExecutor;
import org.neo4j.test.Race;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;
import org.neo4j.time.Clocks;
import org.neo4j.time.FakeClock;
import org.neo4j.time.SystemNanoClock;

@TestDirectoryExtension
class DatabaseTransactionShutdownIT {
    private static final String UNCLEAN_SHUTDOWN_MSG = "Failed to close all transactions. Shutdown may be unclean.";

    @Inject
    TestDirectory directory;

    DatabaseManagementService dbms;
    GraphDatabaseAPI db;
    AssertableLogProvider logProvider = new AssertableLogProvider();

    void setUp(SystemNanoClock clock) {
        dbms = new TestDatabaseManagementServiceBuilder(directory.homePath())
                .setConfig(shutdown_transaction_end_timeout, Duration.ofMillis(0))
                .setConfig(shutdown_terminated_transaction_wait_timeout, Duration.ofMillis(1))
                .setClock(clock)
                .setInternalLogProvider(logProvider)
                .build();
        db = (GraphDatabaseAPI) dbms.database(DEFAULT_DATABASE_NAME);
    }

    @AfterEach
    void tearDown() {
        shutdownDbms();
    }

    @Test
    void shouldWaitForTransactionToDetectTerminationOnShutdown() throws Exception {
        FakeClock clock = Clocks.fakeClock();
        setUp(clock);
        // Given
        Duration waitTime = db.getDependencyResolver()
                .resolveDependency(Config.class)
                .get(shutdown_terminated_transaction_wait_timeout);

        var transactionLatch = new CountDownLatch(1);
        var databaseMonitors = db.getDependencyResolver().resolveDependency(DatabaseMonitors.class);
        databaseMonitors.addMonitorListener(new ShutdownTransactionMonitor() {

            @Override
            public void awaitTerminatedTransactionClose() {
                transactionLatch.countDown();
            }
        });

        try (OtherThreadExecutor executor =
                new OtherThreadExecutor("shouldWaitForTransactionToDetectTerminationOnShutdown")) {
            Transaction tx = db.beginTx();
            KernelTransaction ktx = ((TransactionImpl) tx).kernelTransaction();
            // When
            tx.createNode();
            Future<Object> shutdownFuture = executor.executeDontWait(this::shutdownDbms);
            transactionLatch.await();

            // Then
            assertThat(ktx.getTerminationMark()).isNotEmpty();
            assertThatThrownBy(tx::createNode).isInstanceOf(TransactionTerminatedException.class);

            // Forward the clock for shutdown to continue, ignoring the non-closed transaction
            while (!shutdownFuture.isDone()) {
                clock.forward(waitTime.plusMillis(1));
            }

            // Shutdown will continue, ignoring the non-closed transaction
            shutdownFuture.get();

            // Close throws as dbms is shut down
            assertThatThrownBy(tx::close)
                    .isInstanceOf(TransientTransactionFailureException.class)
                    .hasMessageContaining("The database is not currently available to serve your request");
        }
    }

    @Test
    void shouldWaitForTransactionToDetectTerminationAndCloseOnShutdown() throws Exception {
        setUp(Clocks.fakeClock());
        // Given
        var transactionLatch = new CountDownLatch(1);
        var databaseMonitors = db.getDependencyResolver().resolveDependency(DatabaseMonitors.class);
        databaseMonitors.addMonitorListener(new ShutdownTransactionMonitor() {

            @Override
            public void awaitTerminatedTransactionClose() {
                transactionLatch.countDown();
            }
        });

        try (OtherThreadExecutor executor =
                new OtherThreadExecutor("waitForTransactionToDetectTerminationAndCloseOnShutdown")) {
            Future<Object> shutdownFuture;
            Transaction tx = db.beginTx();
            KernelTransaction ktx = ((TransactionImpl) tx).kernelTransaction();
            // When
            tx.createNode();
            shutdownFuture = executor.executeDontWait(this::shutdownDbms);
            transactionLatch.await();
            assertThat(ktx.getTerminationMark()).isNotEmpty();

            // Transaction is terminated. Let's close it.
            tx.close();
            // Shutdown then continues. Note: we don't forward the clock
            shutdownFuture.get();
        }
    }

    @Test
    void shouldNotAllowNewTransactionsAfterUnavailable() throws Exception {
        setUp(Clocks.nanoClock());
        DependencyResolver dep = db.getDependencyResolver();
        KernelTransactions ktxs = dep.resolveDependency(KernelTransactions.class);
        dep.resolveDependency(AvailabilityGuard.class).addListener(new AvailabilityListener() {
            @Override
            public void unavailable() {
                ktxs.unblockNewTransactions();
            }
        });

        try (OtherThreadExecutor executor = new OtherThreadExecutor("notAllowNewTransactionsAfterUnavailable")) {
            Future<RuntimeException> future;
            ktxs.blockNewTransactions();
            future = executor.executeDontWait(() -> {
                try (Transaction tx = db.beginTx()) {
                } catch (RuntimeException e) {
                    return e;
                }
                return null;
            });
            executor.waitUntilWaiting(details -> details.isAt(KernelTransactions.class, "newKernelTransaction"));
            shutdownDbms();
            assertThat(future.get()).isInstanceOf(DatabaseShutdownException.class);
        }
    }

    @Test
    void transactionCloseReleaseIdsOnShutdown() {
        setUp(Clocks.nanoClock());

        try (Transaction transaction = db.beginTx()) {
            transaction.createNode();
            transaction.createNode();
            transaction.createNode();

            shutdownDbms();
            assertThatThrownBy(transaction::close)
                    .isInstanceOf(TransientTransactionFailureException.class)
                    .hasMessageContaining("The database is not currently available to serve your request")
                    .rootCause()
                    .isInstanceOf(DatabaseShutdownException.class);
        }
    }

    @Test
    void shouldLogUncleanShutdownOnLeakedTransaction() {
        setUp(Clocks.nanoClock());
        try (Transaction leakedTx = db.beginTx()) {
            dbms.shutdown();
            LogAssertions.assertThat(logProvider)
                    .forClass(KernelTransactions.class)
                    .forLevel(WARN)
                    .containsMessages(UNCLEAN_SHUTDOWN_MSG);
            assertThatThrownBy(leakedTx::close)
                    .isInstanceOf(TransientTransactionFailureException.class)
                    .hasMessageContaining("The database is not currently available to serve your request");
        }
    }

    @Test
    void shouldNotLeakedTransactionsOnShutdownRace() throws Throwable {
        setUp(Clocks.nanoClock());
        DependencyResolver dep = db.getDependencyResolver();
        KernelTransactions ktxs = dep.resolveDependency(KernelTransactions.class);
        Race race = new Race();
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean done = new AtomicBoolean(false);
        race.addContestants(threads, () -> {
            latch.countDown();
            while (!done.get()) {
                try (Transaction tx = db.beginTx()) {
                } catch (DatabaseShutdownException
                        | TransactionFailureException
                        | TransientTransactionFailureException ignored) {
                }
            }
        });
        Race.Async future = null;
        try {
            future = race.goAsync();
            latch.await();
            dbms.shutdown();
            assertThat(ktxs.haveActiveTransaction()).isFalse();
            LogAssertions.assertThat(logProvider)
                    .forClass(Database.class)
                    .forLevel(WARN)
                    .doesNotContainMessage(UNCLEAN_SHUTDOWN_MSG);
        } finally {
            done.set(true);
            if (future != null) {
                future.await(1, TimeUnit.MINUTES);
            }
        }
    }

    private Void shutdownDbms() {
        if (dbms != null) {
            dbms.shutdown();
            dbms = null;
        }
        return null;
    }
}

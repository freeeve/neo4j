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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.neo4j.cypher.internal.javacompat.InternalQueryExecutionEngine;
import org.neo4j.cypher.internal.preparser.FullyParsedQuery;
import org.neo4j.fabric.FabricDatabaseManager;
import org.neo4j.fabric.bookmark.LocalBookmark;
import org.neo4j.fabric.bookmark.LocalGraphTransactionIdTracker;
import org.neo4j.fabric.bookmark.TransactionBookmarkManager;
import org.neo4j.fabric.config.FabricConfig;
import org.neo4j.fabric.executor.QueryStatementLifecycles.StatementLifecycle;
import org.neo4j.fabric.stream.QueryInput;
import org.neo4j.fabric.stream.StatementResult;
import org.neo4j.fabric.transaction.FabricTransactionInfo;
import org.neo4j.fabric.transaction.TransactionMode;
import org.neo4j.fabric.transaction.parent.CompoundTransaction;
import org.neo4j.graphdb.TransactionFailureHelper;
import org.neo4j.internal.kernel.api.connectioninfo.RoutingInfo;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.availability.UnavailableException;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.impl.query.TransactionalContextFactory;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.monitoring.ExceptionHandlerService;
import org.neo4j.values.virtual.MapValue;

public class FabricLocalExecutor {
    private final FabricConfig config;
    private final FabricDatabaseManager dbms;
    private final LocalGraphTransactionIdTracker transactionIdTracker;

    public FabricLocalExecutor(
            FabricConfig config, FabricDatabaseManager dbms, LocalGraphTransactionIdTracker transactionIdTracker) {
        this.config = config;
        this.dbms = dbms;
        this.transactionIdTracker = transactionIdTracker;
    }

    public LocalTransactionContext startTransactionContext(
            CompoundTransaction<SingleDbTransaction> parentTransaction,
            FabricTransactionInfo transactionInfo,
            TransactionBookmarkManager bookmarkManager) {
        return new LocalTransactionContext(parentTransaction, transactionInfo, bookmarkManager);
    }

    public class LocalTransactionContext implements AutoCloseable {
        private final Map<UUID, KernelTxWrapper> kernelTransactions = new ConcurrentHashMap<>();
        private final Set<InternalTransaction> internalTransactions = ConcurrentHashMap.newKeySet();

        private final CompoundTransaction<SingleDbTransaction> parentTransaction;
        private final FabricTransactionInfo transactionInfo;
        private final TransactionBookmarkManager bookmarkManager;

        private LocalTransactionContext(
                CompoundTransaction<SingleDbTransaction> parentTransaction,
                FabricTransactionInfo transactionInfo,
                TransactionBookmarkManager bookmarkManager) {
            this.parentTransaction = parentTransaction;
            this.transactionInfo = transactionInfo;
            this.bookmarkManager = bookmarkManager;
        }

        public StatementResult run(
                Location.Local location,
                TransactionMode transactionMode,
                StatementLifecycle parentLifecycle,
                FullyParsedQuery query,
                MapValue params,
                QueryInput input,
                ExecutionOptions executionOptions,
                Boolean targetsComposite) {
            var kernelTransaction = getOrCreateTx(location, transactionMode, targetsComposite);
            return kernelTransaction.run(query, params, input, parentLifecycle, executionOptions);
        }

        public StatementResult runInAutocommitTransaction(
                Location.Local location,
                StatementLifecycle parentLifecycle,
                FullyParsedQuery query,
                MapValue params,
                QueryInput input,
                ExecutionOptions executionOptions) {
            var databaseFacade = getDatabaseFacade(location);
            bookmarkManager
                    .getBookmarkForLocal(location)
                    .ifPresent(bookmark -> transactionIdTracker.awaitGraphUpToDate(location, bookmark.transactionId()));
            var kernelTransaction = beginKernelTx(databaseFacade);

            var kernelResult = kernelTransaction.run(query, params, input, parentLifecycle, executionOptions);
            var result = new AutocommitLocalStatementResult(
                    kernelResult, kernelTransaction, bookmarkManager, transactionIdTracker, location);
            parentTransaction.registerAutocommitQuery(result);
            return result;
        }

        @Override
        public void close() {}

        public Set<InternalTransaction> getInternalTransactions() {
            return internalTransactions;
        }

        public FabricKernelTransaction getOrCreateTx(
                Location.Local location, TransactionMode transactionMode, Boolean targetsComposite) {
            var existingTx = kernelTransactions.get(location.getUuid());
            if (!targetsComposite && existingTx != null) {
                maybeUpgradeToWritingTransaction(existingTx, transactionMode);
                return existingTx.fabricKernelTransaction;
            }

            // it is important to try to get the facade before handling bookmarks
            // Unlike the bookmark logic, this will fail gracefully if the database is not available
            var databaseFacade = getDatabaseFacade(location);

            bookmarkManager
                    .getBookmarkForLocal(location)
                    .ifPresent(bookmark -> transactionIdTracker.awaitGraphUpToDate(location, bookmark.transactionId()));
            return kernelTransactions.computeIfAbsent(
                            location.getUuid(),
                            dbUuid -> parentTransaction.registerNewChildTransaction(location, transactionMode, () -> {
                                var tx = beginKernelTx(databaseFacade);
                                return new KernelTxWrapper(tx, bookmarkManager, location);
                            }))
                    .fabricKernelTransaction;
        }

        private void maybeUpgradeToWritingTransaction(KernelTxWrapper tx, TransactionMode transactionMode) {
            if (transactionMode == TransactionMode.DEFINITELY_WRITE) {
                parentTransaction.upgradeToWritingTransaction(tx);
            }
        }

        private FabricKernelTransaction beginKernelTx(GraphDatabaseAPI databaseFacade) {
            var dependencyResolver = databaseFacade.getDependencyResolver();
            var executionEngine = dependencyResolver.resolveDependency(InternalQueryExecutionEngine.class);

            var internalTransaction = beginInternalTransaction(databaseFacade, transactionInfo);

            var transactionalContextFactory = dependencyResolver.resolveDependency(TransactionalContextFactory.class);

            return new FabricKernelTransaction(
                    executionEngine, transactionalContextFactory, internalTransaction, config, transactionInfo);
        }

        private GraphDatabaseAPI getDatabaseFacade(Location.Local location) {
            try {
                var dbName = location.getDatabaseName();
                var facade = dbms.getDatabaseFacade(dbName);
                if (!Objects.equals(facade.databaseId().databaseId().uuid(), location.getUuid())) {
                    throw FabricException.databaseLocationChanged(dbName);
                }
                return facade;
            } catch (UnavailableException e) {
                throw new FabricException(e, Status.General.DatabaseUnavailable, e);
            }
        }

        private InternalTransaction beginInternalTransaction(
                GraphDatabaseAPI databaseAPI, FabricTransactionInfo transactionInfo) {
            KernelTransaction.Type kernelTransactionType = getKernelTransactionType(transactionInfo);
            var accessMode =
                    switch (transactionInfo.getAccessMode()) {
                        case WRITE -> RoutingInfo.AccessMode.WRITE;
                        case READ -> RoutingInfo.AccessMode.READ;
                    };
            var routingInfo = new RoutingInfo(
                    accessMode, transactionInfo.getRoutingContext().getParameters());

            InternalTransaction internalTransaction = databaseAPI.beginTransaction(
                    kernelTransactionType,
                    transactionInfo.getLoginContext(),
                    transactionInfo.getClientConnectionInfo(),
                    routingInfo,
                    transactionInfo.getBookmarks(),
                    transactionInfo.getTxTimeout().toMillis(),
                    TimeUnit.MILLISECONDS,
                    this::reportTermination,
                    this::transformTerminalOperationError);

            if (transactionInfo.getTxMetadata() != null) {
                internalTransaction.setMetaData(transactionInfo.getTxMetadata());
            }

            internalTransactions.add(internalTransaction);

            return internalTransaction;
        }

        private KernelTransaction.Type getKernelTransactionType(FabricTransactionInfo fabricTransactionInfo) {
            if (fabricTransactionInfo.isImplicitTransaction()) {
                return KernelTransaction.Type.IMPLICIT;
            }

            return KernelTransaction.Type.EXPLICIT;
        }

        private RuntimeException transformTerminalOperationError(
                Exception e, Log log, ExceptionHandlerService exceptionHandlerService) {
            // The main purpose of this is mapping of checked exceptions
            // while preserving status codes
            if (e instanceof Status.HasStatus) {
                if (e instanceof RuntimeException runtimeException) {
                    return runtimeException;
                }
                return FabricException.translateLocalError((Exception & Status.HasStatus) e);
            }

            // We don't know what operation is being executed,
            // so it is not possible to come up with a reasonable status code here.
            // The error is wrapped into a generic one
            // and a proper status code will be added later.

            // GQL status code 25N02 points to the debug log for more information, so let's make sure people will
            // actually find more info there.
            log.error(e.getMessage(), e);
            exceptionHandlerService.raiseException(e.getMessage(), e);
            throw TransactionFailureHelper.genericFailure(e);
        }

        private void reportTermination(Status status) {
            // Cypher runtime introduced a new interesting feature some time ago.
            // When an exception is thrown during a query execution, the transaction is terminated.
            // This is quite unfortunate for composite queries, because when an exception happens
            // in one local fragment, we don't want to terminate all the involved transactions before
            // the original exception is propagated through the composite execution pipeline.
            // In other words, we don't want to tear down the entire composite execution pipeline when
            // and exception happens in during a fragment execution.
            // So we filter termination reasons that we send 'up' to the parent transaction now.
            // Considering only statuses from 'Transaction' category should cover all the cases
            // when reporting termination 'up' makes sense
            if (Arrays.stream(Status.Transaction.values()).anyMatch(transactionStatus -> status == transactionStatus)) {
                parentTransaction.childTransactionTerminated(status);
            }
        }
    }

    private class KernelTxWrapper implements SingleDbTransaction {

        private final FabricKernelTransaction fabricKernelTransaction;
        private final TransactionBookmarkManager bookmarkManager;
        private final Location.Local location;

        KernelTxWrapper(
                FabricKernelTransaction fabricKernelTransaction,
                TransactionBookmarkManager bookmarkManager,
                Location.Local location) {
            this.fabricKernelTransaction = fabricKernelTransaction;
            this.bookmarkManager = bookmarkManager;
            this.location = location;
        }

        @Override
        public void commit() {
            fabricKernelTransaction.commit();
            transactionIdTracker
                    .getTransactionId(location)
                    .ifPresent(transactionId ->
                            bookmarkManager.localTransactionCommitted(location, new LocalBookmark(transactionId)));
        }

        @Override
        public void rollback() {
            fabricKernelTransaction.rollback();
        }

        @Override
        public void terminate(Status reason) {
            fabricKernelTransaction.terminate(reason);
        }

        @Override
        public Location location() {
            return location;
        }
    }
}

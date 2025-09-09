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
package org.neo4j.router.impl.transaction.database;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.neo4j.common.DependencyResolver;
import org.neo4j.dbms.database.DatabaseContextProvider;
import org.neo4j.fabric.bookmark.LocalGraphTransactionIdTracker;
import org.neo4j.fabric.bookmark.TransactionBookmarkManager;
import org.neo4j.fabric.executor.Location;
import org.neo4j.graphdb.TransactionFailureHelper;
import org.neo4j.internal.kernel.api.connectioninfo.RoutingInfo;
import org.neo4j.kernel.GraphDatabaseQueryService;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.availability.UnavailableException;
import org.neo4j.kernel.database.DatabaseReference;
import org.neo4j.kernel.database.DatabaseReferenceImpl;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.impl.factory.KernelTransactionFactory;
import org.neo4j.kernel.impl.query.ConstituentTransactionFactory;
import org.neo4j.kernel.impl.query.Neo4jTransactionalContextFactory;
import org.neo4j.kernel.impl.query.QueryExecutionEngine;
import org.neo4j.kernel.impl.query.TransactionalContext;
import org.neo4j.kernel.impl.query.TransactionalContextFactory;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.monitoring.ExceptionHandlerService;
import org.neo4j.router.QueryRouterException;
import org.neo4j.router.transaction.DatabaseTransaction;
import org.neo4j.router.transaction.DatabaseTransactionFactory;
import org.neo4j.router.transaction.TransactionInfo;

public class LocalDatabaseTransactionFactory implements DatabaseTransactionFactory<Location.Local> {
    protected final DatabaseContextProvider<?> databaseContextProvider;
    protected final LocalGraphTransactionIdTracker transactionIdTracker;

    public LocalDatabaseTransactionFactory(
            DatabaseContextProvider<?> databaseContextProvider, LocalGraphTransactionIdTracker transactionIdTracker) {
        this.databaseContextProvider = databaseContextProvider;
        this.transactionIdTracker = transactionIdTracker;
    }

    @Override
    public DatabaseTransaction beginTransaction(
            Location.Local location,
            TransactionInfo transactionInfo,
            TransactionBookmarkManager bookmarkManager,
            Consumer<Status> terminationCallback,
            ConstituentTransactionFactory constituentTransactionFactory) {

        var resolvedReference = location.databaseReference();
        // If we are in the local DB and we see a VirtualSPD reference, we need to resolve it to the graph shard and
        // that will always exist here because VirtualSPDs are only created on the same servers as graph shards.
        var externalShardAccess = true;
        if (location.databaseReference() instanceof DatabaseReferenceImpl.VirtualSPD) {
            resolvedReference = ((DatabaseReferenceImpl.VirtualSPD) location.databaseReference()).graphShard();
            externalShardAccess = false;
        }

        var databaseContext = databaseContextProvider
                .getDatabaseContext(resolvedReference.databaseId())
                .orElseThrow(databaseUnavailable(location.getDatabaseName()));

        var databaseApi = databaseContext.databaseFacade();
        var resolver = databaseContext.dependencies();

        try {
            databaseContext.database().getDatabaseAvailabilityGuard().assertDatabaseAvailable();
        } catch (UnavailableException e) {
            throw QueryRouterException.wrapError(e);
        }

        var queryExecutionEngine = resolver.resolveDependency(QueryExecutionEngine.class);
        TransactionalContextFactory transactionalContextFactory =
                getTransactionalContextFactory(location, resolver, dbMode(resolvedReference));

        bookmarkManager
                .getBookmarkForLocal(location)
                .ifPresent(bookmark -> transactionIdTracker.awaitGraphUpToDate(location, bookmark.transactionId()));

        InternalTransaction internalTransaction =
                beginInternalTransaction(databaseApi, transactionInfo, terminationCallback, externalShardAccess);

        return new LocalDatabaseTransaction(
                location,
                transactionInfo,
                internalTransaction,
                transactionalContextFactory,
                queryExecutionEngine,
                bookmarkManager,
                transactionIdTracker,
                constituentTransactionFactory);
    }

    protected TransactionalContextFactory getTransactionalContextFactory(
            Location.Local location, DependencyResolver resolver, TransactionalContext.DatabaseMode dbMode) {
        return Neo4jTransactionalContextFactory.create(
                resolver.provideDependency(GraphDatabaseQueryService.class),
                resolver.resolveDependency(KernelTransactionFactory.class),
                dbMode);
    }

    protected InternalTransaction beginInternalTransaction(
            GraphDatabaseAPI databaseApi,
            TransactionInfo transactionInfo,
            Consumer<Status> terminationCallback,
            boolean externalShardAccess) {
        var accessMode =
                switch (transactionInfo.accessMode()) {
                    case WRITE -> RoutingInfo.AccessMode.WRITE;
                    case READ -> RoutingInfo.AccessMode.READ;
                };
        var routingInfo =
                new RoutingInfo(accessMode, transactionInfo.routingContext().getParameters());
        var loginContext = externalShardAccess
                ? transactionInfo.loginContext().withExternalShardAccess()
                : transactionInfo.loginContext();
        InternalTransaction internalTransaction = databaseApi.beginTransaction(
                transactionInfo.type(),
                loginContext,
                transactionInfo.clientInfo(),
                routingInfo,
                transactionInfo.bookmarks(),
                transactionInfo.txTimeout().toMillis(),
                TimeUnit.MILLISECONDS,
                terminationCallback,
                this::transformTerminalOperationError);

        internalTransaction.setMetaData(transactionInfo.txMetadata());

        return internalTransaction;
    }

    private RuntimeException transformTerminalOperationError(
            Exception e, Log log, ExceptionHandlerService exceptionHandlerService) {
        // The main purpose of this is mapping of checked exceptions
        // while preserving status codes
        if (e instanceof Status.HasStatus se) {
            if (e instanceof RuntimeException re) {
                return re;
            }
            return QueryRouterException.wrapError((Throwable & Status.HasStatus) se);
        }

        // We don't know what operation is being executed,
        // so it is not possible to come up with a reasonable status code here.
        // The error is wrapped into a generic one
        // and a proper status code will be added later.

        // GQL status code 25N02 points to the debug log for more information, so let's make sure people will actually
        // find more info there.
        log.error(e.getMessage(), e);
        exceptionHandlerService.raiseException(e.getMessage(), e);
        throw TransactionFailureHelper.genericFailure(e);
    }

    protected static Supplier<QueryRouterException> databaseUnavailable(String databaseNameRaw) {
        return () -> {
            var unavailableException = UnavailableException.databaseUnavailable(
                    databaseNameRaw, String.format("Database %s not available", databaseNameRaw));
            return new QueryRouterException(
                    unavailableException.gqlStatusObject(),
                    unavailableException.status(),
                    unavailableException.legacyMessage(),
                    unavailableException);
        };
    }

    private TransactionalContext.DatabaseMode dbMode(DatabaseReference reference) {
        if (reference instanceof DatabaseReferenceImpl.GraphShard) {
            return TransactionalContext.DatabaseMode.SHARDED;
        } else {
            return TransactionalContext.DatabaseMode.SINGLE;
        }
    }
}

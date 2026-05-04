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
package org.neo4j.dbms.database;

import java.util.Optional;
import java.util.OptionalLong;
import org.neo4j.kernel.database.DatabaseId;
import org.neo4j.storageengine.StoreFileClosedException;
import org.neo4j.storageengine.api.ExternalStoreId;
import org.neo4j.storageengine.api.LogMetadataProvider;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.storageengine.api.StoreIdProvider;
import org.neo4j.storageengine.api.TransactionIdStore;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DefaultDatabaseDetailsExtrasProvider {
    public static final long COMMITTED_TX_ID_NOT_AVAILABLE = -1;

    private final DatabaseContextProvider<?> databaseContextProvider;

    public DefaultDatabaseDetailsExtrasProvider(DatabaseContextProvider<?> databaseContextProvider) {
        this.databaseContextProvider = databaseContextProvider;
    }

    public DatabaseDetailsExtras extraDetails(DatabaseId databaseId, TopologyInfoService.RequestedExtras detailsLevel) {
        if (detailsLevel.txInfo() || detailsLevel.storeInfo()) {
            var lastCommittedTxId = OptionalLong.empty();
            var lastAppendIndex = OptionalLong.empty();
            var storeId = Optional.<StoreId>empty();
            var externalStoreId = Optional.<ExternalStoreId>empty();
            var context = databaseContextProvider
                    .getDatabaseContext(databaseId)
                    .filter(databaseContext -> databaseContext.database().isStarted());
            if (detailsLevel.txInfo()) {
                lastCommittedTxId = fetchLastCommittedTxId(context);
                lastAppendIndex = fetchLastAppendIndex(context);
            }
            if (detailsLevel.storeInfo()) {
                storeId = fetchStoreId(context);
                externalStoreId = fetchExternalStoreId(context);
            }
            return new DatabaseDetailsExtras(lastCommittedTxId, lastAppendIndex, storeId, externalStoreId);
        }
        return DatabaseDetailsExtras.EMPTY;
    }

    private static OptionalLong fetchLastAppendIndex(Optional<? extends DatabaseContext> context) {
        var provider = context.map(DatabaseContext::dependencies)
                .flatMap(dependencyResolver -> dependencyResolver.resolveOptionalDependency(LogMetadataProvider.class));
        return provider.map(logMetadataProvider -> OptionalLong.of(
                        logMetadataProvider.getLastCommittedBatch().appendIndex()))
                .orElseGet(OptionalLong::empty);
    }

    private static OptionalLong fetchLastCommittedTxId(Optional<? extends DatabaseContext> context) {
        var store = context.map(DatabaseContext::dependencies)
                .flatMap(dependencyResolver -> dependencyResolver.resolveOptionalDependency(TransactionIdStore.class));
        if (store.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(store.get().getLastCommittedTransactionId());
        } catch (StoreFileClosedException e) {
            return OptionalLong.empty();
        }
    }

    private static Optional<StoreId> fetchStoreId(Optional<? extends DatabaseContext> context) {
        return context.flatMap(c -> {
            try {
                return Optional.of(c.database().getStoreId());
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<ExternalStoreId> fetchExternalStoreId(Optional<? extends DatabaseContext> context) {
        return context.map(DatabaseContext::dependencies)
                .flatMap(dependencyResolver -> dependencyResolver.resolveOptionalDependency(StoreIdProvider.class))
                .flatMap(storeIdProvider -> {
                    try {
                        return Optional.of(storeIdProvider.getExternalStoreId());
                    } catch (StoreFileClosedException e) {
                        return Optional.empty();
                    }
                });
    }
}

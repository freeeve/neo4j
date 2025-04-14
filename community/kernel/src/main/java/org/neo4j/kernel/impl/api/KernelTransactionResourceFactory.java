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
package org.neo4j.kernel.impl.api;

import org.neo4j.collection.Dependencies;
import org.neo4j.configuration.Config;
import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.EntityLocks;
import org.neo4j.internal.kernel.api.QueryContext;
import org.neo4j.internal.kernel.api.SchemaRead;
import org.neo4j.internal.kernel.api.TokenRead;
import org.neo4j.internal.schema.SchemaState;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.AssertOpen;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.kernel.impl.api.index.IndexingService;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.kernel.impl.newapi.DefaultPooledCursors;
import org.neo4j.kernel.impl.newapi.KernelProcedures;
import org.neo4j.kernel.impl.newapi.KernelRead;
import org.neo4j.logging.LogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.cursor.StoreCursors;

public interface KernelTransactionResourceFactory {
    KernelRead createKernelRead(
            StorageReader storageReader,
            TokenRead tokenRead,
            CursorFactory cursorFactory,
            StoreCursors storeCursors,
            EntityLocks entityLocks,
            QueryContext queryContext,
            TxStateHolder txStateHolder,
            SchemaRead schemaRead,
            IndexingService indexingService,
            MemoryTracker memoryTracker,
            boolean multiVersioned,
            AssertOpen assertOpen,
            AccessModeProvider accessModeProvider,
            boolean parallel,
            LogProvider logProvider);

    SchemaRead createSchemaRead(
            SchemaState schemaState,
            IndexStatisticsStore indexStatisticsStore,
            StorageReader storageReader,
            EntityLocks entityLocks,
            TxStateHolder txStateHolder,
            IndexingService indexingService,
            AssertOpen assertOpen,
            AccessModeProvider accessModeProvider);

    DefaultPooledCursors createCursors(
            StorageReader storageReader,
            StoreCursors transactionalCursors,
            Config config,
            StorageEngineIndexingBehaviour indexingBehaviour,
            boolean multiVersioned);

    KernelProcedures.ForTransactionScope createProcedures(
            KernelTransactionImplementation ktx, Dependencies databaseDependencies, AssertOpen assertOpen);
}

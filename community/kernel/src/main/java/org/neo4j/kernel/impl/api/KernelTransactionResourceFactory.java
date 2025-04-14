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

import java.util.List;
import java.util.function.Supplier;
import org.neo4j.collection.Dependencies;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.Config;
import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.EntityLocks;
import org.neo4j.internal.kernel.api.IndexMonitor;
import org.neo4j.internal.kernel.api.QueryContext;
import org.neo4j.internal.kernel.api.SchemaRead;
import org.neo4j.internal.kernel.api.TokenRead;
import org.neo4j.internal.kernel.api.security.SecurityAuthorizationHandler;
import org.neo4j.internal.schema.SchemaState;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.AssertOpen;
import org.neo4j.kernel.api.ExecutionContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.procedure.ProcedureView;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.kernel.impl.api.index.IndexingService;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.kernel.impl.api.parallel.ExecutionContextCursorTracer;
import org.neo4j.kernel.impl.api.parallel.ExecutionContextProcedureKernelTransaction;
import org.neo4j.kernel.impl.locking.LockManager;
import org.neo4j.kernel.impl.newapi.DefaultPooledCursors;
import org.neo4j.kernel.impl.newapi.KernelProcedures;
import org.neo4j.kernel.impl.newapi.KernelRead;
import org.neo4j.lock.LockTracer;
import org.neo4j.logging.LogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.StorageLocks;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.values.ElementIdMapper;

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

    KernelProcedures.ForThreadExecutionContextScope createProcedures(
            ExecutionContext executionContext,
            DependencyResolver databaseDependencies,
            OverridableSecurityContext overridableSecurityContext,
            ExecutionContextProcedureKernelTransaction kernelTransaction,
            SecurityAuthorizationHandler securityAuthorizationHandler,
            Supplier<ClockContext> clockContextSupplier,
            ProcedureView procedureView);

    ExecutionContext createExecutionContext(
            DefaultPooledCursors cursors,
            CursorContext context,
            OverridableSecurityContext overridableSecurityContext,
            ExecutionContextCursorTracer cursorTracer,
            CursorContext ktxContext,
            TokenRead tokenRead,
            StoreCursors storageCursors,
            IndexMonitor monitor,
            MemoryTracker contextTracker,
            SecurityAuthorizationHandler securityAuthorizationHandler,
            StorageReader storageReader,
            SchemaState schemaState,
            IndexingService indexingService,
            IndexStatisticsStore indexStatisticsStore,
            Dependencies databaseDependencies,
            StorageLocks storageLocks,
            LockManager.Client lockClient,
            LockTracer lockTracer,
            ElementIdMapper elementIdMapper,
            KernelTransaction ktx,
            Supplier<ClockContext> clockContextSupplier,
            List<AutoCloseable> otherResources,
            ProcedureView procedureView,
            boolean multiVersioned,
            LogProvider logProvider);
}

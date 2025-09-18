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
package org.neo4j.kernel.impl.index.schema;

import static java.util.Objects.requireNonNull;
import static org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector.immediate;
import static org.neo4j.kernel.database.Database.initialSchemaRulesLoader;
import static org.neo4j.kernel.impl.api.TransactionVisibilityProvider.EMPTY_VISIBILITY_PROVIDER;
import static org.neo4j.kernel.impl.locking.LockManager.NO_LOCKS_LOCK_MANAGER;
import static org.neo4j.lock.LockService.NO_LOCK_SERVICE;
import static org.neo4j.scheduler.Group.INDEX_POPULATION;
import static org.neo4j.scheduler.Group.INDEX_POPULATION_WORK;

import java.io.IOException;
import java.util.List;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.primitive.ObjectFloatMaps;
import org.neo4j.batchimport.api.IndexesLifecycleManager;
import org.neo4j.collection.Dependencies;
import org.neo4j.common.Subject;
import org.neo4j.dbms.database.readonly.DatabaseReadOnlyChecker;
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.HostedOnMode;
import org.neo4j.index.internal.gbptree.GroupingRecoveryCleanupWorkCollector;
import org.neo4j.internal.kernel.api.IndexMonitor;
import org.neo4j.internal.kernel.api.InternalIndexState;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotFoundKernelException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.impl.muninn.VersionStorage;
import org.neo4j.kernel.api.exceptions.index.IndexPopulationFailedKernelException;
import org.neo4j.kernel.api.index.KernelSchemaLifecycleContext;
import org.neo4j.kernel.impl.api.DatabaseSchemaState;
import org.neo4j.kernel.impl.api.index.IndexProxy;
import org.neo4j.kernel.impl.api.index.IndexingService;
import org.neo4j.kernel.impl.api.index.IndexingServiceFactory;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.kernel.impl.index.DatabaseIndexStats;
import org.neo4j.kernel.impl.transaction.state.StaticIndexProviderMapFactory;
import org.neo4j.kernel.impl.transaction.state.storeview.FullScanStoreView;
import org.neo4j.kernel.impl.transaction.state.storeview.IndexStoreViewFactory;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.Lifespan;
import org.neo4j.monitoring.Monitors;
import org.neo4j.time.Clocks;
import org.neo4j.util.VisibleForTesting;

public class KernelIndexesLifecycleManager implements IndexesLifecycleManager {

    private static final float ZERO = 0.0f;

    private final KernelSchemaLifecycleContext context;
    private final Lifespan lifespan;
    private final IndexingService indexingService;

    public KernelIndexesLifecycleManager(KernelSchemaLifecycleContext context) throws IOException {
        this.context = requireNonNull(context);

        // need to create the lifecycle in the NONE state as the scheduler has already been started
        // adding an already started lifecycle component and then calling start then fails
        this.lifespan = Lifespan.createWithNoneState();
        this.indexingService = createIndexingService(lifespan, context);
        lifespan.start(); // this will call life.init automatically
    }

    @Override
    public IndexDescriptor completeConfiguration(IndexDescriptor index) {
        return indexingService.completeConfiguration(index);
    }

    @Override
    public void drop(DropListener dropListener, List<IndexDescriptor> indexDescriptors) {
        final var descriptorCount = indexDescriptors.size();
        if (descriptorCount == 0) {
            return;
        }

        var dropOk = 0;
        var dropFailed = 0;
        for (var descriptor : indexDescriptors) {
            try {
                indexingService.dropIndex(descriptor);
                if (dropListener.onDrop(descriptor)) {
                    dropOk++;
                } else {
                    dropFailed++;
                }
            } catch (RuntimeException ex) {
                dropListener.onDropFailed(descriptor, ex);
                dropFailed++;
            }
        }

        dropListener.onDropCompleted(dropOk, dropFailed);
    }

    @Override
    public void create(CreationListener creationListener, List<IndexDescriptor> indexDescriptors) throws IOException {
        if (indexDescriptors.isEmpty()) {
            return;
        }

        indexingService.createIndexes(
                Subject.SYSTEM,
                CursorContext.NULL_CONTEXT,
                indexDescriptors.stream()
                        .map(indexingService::completeConfiguration)
                        .toArray(IndexDescriptor[]::new));

        var noErrors = true;
        final var progressTracker = ObjectFloatMaps.mutable.<IndexDescriptor>empty();
        final var descriptorsToCreate = Sets.mutable.ofAll(indexDescriptors);
        final var tentatives = Sets.mutable.<IndexProxy>empty();
        while (!descriptorsToCreate.isEmpty()) {
            for (var indexProxy : indexingService.getIndexProxies()) {
                final var descriptor = indexProxy.getDescriptor();
                if (!descriptorsToCreate.contains(descriptor)) {
                    // skip over indexes from before create call or those recently done
                    continue;
                }

                final var state = indexProxy.getState();
                if (state == InternalIndexState.FAILED) {
                    noErrors = false;
                    descriptorsToCreate.remove(reportIndexPopulationFailure(indexProxy, creationListener));
                } else {
                    final var latestProgress =
                            indexProxy.getIndexPopulationProgress().getProgress();
                    progressTracker.updateValue(descriptor, 0.0f, lastProgress -> {
                        if (lastProgress > ZERO) {
                            final var delta = latestProgress - lastProgress;
                            if (delta > ZERO) {
                                creationListener.onUpdate(descriptor, delta);
                            }
                        } else if (latestProgress > ZERO) {
                            // already had some updates on first pass through the loop so update here too
                            creationListener.onUpdate(descriptor, latestProgress);
                        }

                        return latestProgress;
                    });

                    if (state == InternalIndexState.ONLINE) {
                        descriptorsToCreate.remove(descriptor);
                    } else if (latestProgress == 1.0f) {
                        // some proxies are 'tentative' and stay at POPULATING even though they are actually done
                        tentatives.add(indexProxy);
                    }
                }
            }

            if (tentatives.isEmpty()) {
                sleepIgnoreInterrupt();
            } else {
                for (var indexProxy : tentatives) {
                    try {
                        indexingService.activateIndex(indexProxy.getDescriptor());
                    } catch (IndexPopulationFailedKernelException | IndexNotFoundKernelException ex) {
                        noErrors = false;
                        descriptorsToCreate.remove(reportIndexPopulationFailure(indexProxy, creationListener));
                    }
                }

                tentatives.clear();
            }
        }

        creationListener.onCreationCompleted(noErrors);
        if (noErrors) {
            try (var creationContext = context.contextFactory().create("Indexing flushing");
                    var flushEvent = context.pageCacheTracer().beginDatabaseFlush()) {
                indexingService.checkpoint(flushEvent, creationContext);
                creationListener.onCheckpointingCompleted();
            }
        }
    }

    @Override
    public void close() {
        lifespan.close();
    }

    @VisibleForTesting
    protected IndexingService createIndexingService(LifeSupport life, KernelSchemaLifecycleContext context)
            throws IOException {
        final var clock = Clocks.nanoClock();
        final var readOnlyChecker = DatabaseReadOnlyChecker.writable();
        final var logService = context.logService();
        final var logProvider = logService.getInternalLogProvider();
        final var jobScheduler = context.jobScheduler();
        final var databaseLayout = context.databaseLayout();
        final var config = context.config();
        final var pageCache = context.pageCache();
        final var fileSystem = context.fileSystem();
        final var tokenHolders = context.tokenHolders();
        final var contextFactory = context.contextFactory();
        final var pageCacheTracer = context.pageCacheTracer();
        final var storageEngine = context.storageEngine();

        final var cleanupCollector = life.add(new GroupingRecoveryCleanupWorkCollector(
                jobScheduler, INDEX_POPULATION, INDEX_POPULATION_WORK, databaseLayout.getDatabaseName()));

        final var indexDependencies = new Dependencies();
        indexDependencies.satisfyDependencies(VersionStorage.EMPTY_STORAGE);

        final var indexProviderMap = life.add(StaticIndexProviderMapFactory.create(
                life,
                config,
                pageCache,
                fileSystem,
                logService,
                new Monitors(),
                readOnlyChecker,
                HostedOnMode.SINGLE,
                cleanupCollector,
                databaseLayout,
                tokenHolders,
                jobScheduler,
                contextFactory,
                pageCacheTracer,
                indexDependencies));

        final var fullScanStoreView = new FullScanStoreView(NO_LOCK_SERVICE, storageEngine, config, jobScheduler);
        final var indexStoreViewFactory = new IndexStoreViewFactory(
                config, storageEngine, NO_LOCKS_LOCK_MANAGER, fullScanStoreView, NO_LOCK_SERVICE, logProvider);

        final var indexStatisticsStore = life.add(new IndexStatisticsStore(
                pageCache,
                fileSystem,
                databaseLayout.indexStatisticsStore(),
                immediate(),
                false,
                databaseLayout.getDatabaseName(),
                contextFactory,
                pageCacheTracer,
                storageEngine.getOpenOptions()));

        return life.add(IndexingServiceFactory.createIndexingService(
                storageEngine,
                config,
                jobScheduler,
                indexProviderMap,
                indexStoreViewFactory,
                tokenHolders,
                context.elementIdMapper(),
                initialSchemaRulesLoader(storageEngine),
                logService.getInternalLogProvider(),
                IndexMonitor.NO_MONITOR,
                new DatabaseSchemaState(logProvider),
                indexStatisticsStore,
                new DatabaseIndexStats(),
                contextFactory,
                context.memoryTracker(),
                databaseLayout.getDatabaseName(),
                readOnlyChecker,
                clock,
                context.kernelVersionProvider(),
                fileSystem,
                EMPTY_VISIBILITY_PROVIDER));
    }

    private IndexDescriptor reportIndexPopulationFailure(IndexProxy indexProxy, CreationListener creationListener) {
        final var descriptor = indexProxy.getDescriptor();
        creationListener.onFailure(
                descriptor,
                indexProxy
                        .getPopulationFailure()
                        .asIndexPopulationFailure(
                                descriptor.schema(), descriptor.userDescription(context.tokenHolders())));
        return descriptor;
    }

    private static void sleepIgnoreInterrupt() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Ignore interrupted exceptions here.
        }
    }
}

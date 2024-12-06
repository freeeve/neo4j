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
package org.neo4j.internal.batchimport;

import static org.neo4j.configuration.GraphDatabaseInternalSettings.index_populator_block_size;
import static org.neo4j.internal.batchimport.IncrementalBatchImportUtil.moveIndex;
import static org.neo4j.internal.kernel.api.IndexQueryConstraints.unconstrained;
import static org.neo4j.internal.kernel.api.PropertyIndexQuery.allEntries;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import org.eclipse.collections.api.block.function.primitive.LongToLongFunction;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.configuration.Config;
import org.neo4j.internal.helpers.progress.ProgressListener;
import org.neo4j.internal.kernel.api.QueryContext;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotApplicableKernelException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.memory.ByteBufferFactory;
import org.neo4j.io.memory.UnsafeDirectByteBufferAllocator;
import org.neo4j.io.pagecache.tracing.FileFlushEvent;
import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.api.index.IndexEntryConflictHandler;
import org.neo4j.kernel.api.index.IndexPopulator;
import org.neo4j.kernel.impl.api.index.IndexProviderMap;
import org.neo4j.kernel.impl.api.index.IndexSamplingConfig;
import org.neo4j.kernel.impl.api.index.IndexUpdateMode;
import org.neo4j.kernel.impl.api.index.PhaseTracker;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.kernel.impl.index.schema.IndexUsageTracking;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.storageengine.api.schema.SimpleEntityValueClient;
import org.neo4j.values.ElementIdMapper;
import org.neo4j.values.storable.Value;

/**
 * Logic for building indexes during import. The idea is to have an {@link IndexPopulator index populator}
 * for each index that sees updates during import and let each thread (typically doing graph data updates and
 * generating index updates while doing so) add its share of updates to the index populators.
 */
public class ImportIndexBuilder implements Closeable {
    private final FileSystemAbstraction fileSystem;
    private final IndexProviderMap indexProviderMap;
    private final IndexProviderMap tempIndexes;
    private final TokenNameLookup tokenNameLookup;
    private final ImmutableSet<OpenOption> openOptions;
    private final PopulationWorkJobScheduler workScheduler;
    private final LongToLongFunction indexedEntityIdConverter;
    private final LongToLongFunction entityIdFromIndexIdConverter;
    private final Configuration configuration;
    private final IndexStatisticsStore indexStatisticsStore;
    private final Map<IndexDescriptor, IndexBuilder> indexBuilders = new ConcurrentHashMap<>();
    private final Lock builderConstructionLock = new ReentrantLock();
    private final ByteBufferFactory bufferFactory;
    private final MutableLongSet violatingEntities = LongSets.mutable.empty().asSynchronized();
    private final StorageEngineIndexingBehaviour indexingBehaviour;
    private final boolean incrementalIndexing;
    private final Set<IndexDescriptor> excludedIndexes;

    /**
     * @param incrementalIndexing {@code true} if build/merge is split and right now we're doing build.
     */
    public ImportIndexBuilder(
            FileSystemAbstraction fileSystem,
            IndexProviderMap indexProviderMap,
            IndexProviderMap tempIndexes,
            TokenNameLookup tokenNameLookup,
            ImmutableSet<OpenOption> openOptions,
            PopulationWorkJobScheduler workScheduler,
            LongToLongFunction indexedEntityIdConverter,
            LongToLongFunction entityIdFromIndexIdConverter,
            Configuration configuration,
            IndexStatisticsStore indexStatisticsStore,
            StorageEngineIndexingBehaviour indexingBehaviour,
            boolean incrementalIndexing,
            Set<IndexDescriptor> excludedIndexes) {
        this.fileSystem = fileSystem;
        this.indexProviderMap = indexProviderMap;
        this.tempIndexes = tempIndexes;
        this.tokenNameLookup = tokenNameLookup;
        this.openOptions = openOptions;
        this.workScheduler = workScheduler;
        this.indexedEntityIdConverter = indexedEntityIdConverter;
        this.entityIdFromIndexIdConverter = entityIdFromIndexIdConverter;
        this.configuration = configuration;
        this.indexStatisticsStore = indexStatisticsStore;
        this.indexingBehaviour = indexingBehaviour;
        this.incrementalIndexing = incrementalIndexing;
        this.excludedIndexes = excludedIndexes;
        this.bufferFactory = new ByteBufferFactory(
                UnsafeDirectByteBufferAllocator::new,
                Config.defaults().get(index_populator_block_size).intValue());
    }

    public void add(IndexEntryUpdate indexUpdate) {
        if (!excludedIndexes.contains(indexUpdate.indexKey())) {
            var builder = getIndexBuilder(indexUpdate.indexKey());
            builder.add(convertEntityId(indexUpdate));
        }
    }

    /**
     * Used to update uniqueness indexes and must be applied directly because the result
     * (whether conflicting or not) must be known as a result.
     * @return {@code true} if the index update was applied w/o problems, otherwise {@code false}.
     */
    public boolean addDirect(IndexEntryUpdate indexUpdate) {
        if (!excludedIndexes.contains(indexUpdate.indexKey())) {
            var builder = getIndexBuilder(indexUpdate.indexKey());
            return builder.addDirect(convertEntityId(indexUpdate));
        }
        return true;
    }

    private IndexEntryUpdate convertEntityId(IndexEntryUpdate indexUpdate) {
        long entityId = indexUpdate.getEntityId();
        long convertedEntityId = indexedEntityIdConverter.applyAsLong(entityId);
        return entityId != convertedEntityId ? indexUpdate.withEntityId(convertedEntityId) : indexUpdate;
    }

    private IndexBuilder getIndexBuilder(IndexDescriptor index) {
        var builder = indexBuilders.get(index);
        if (builder == null) {
            builderConstructionLock.lock();
            try {
                builder = indexBuilders.get(index);
                if (builder == null) {
                    var populator = constructIndexPopulator(index);
                    var accessor = constructIndexAccessor(index);
                    builder = new IndexBuilder(populator, accessor);
                    indexBuilders.put(index, builder);
                }
            } finally {
                builderConstructionLock.unlock();
            }
        }
        return builder;
    }

    private IndexAccessor constructIndexAccessor(IndexDescriptor index) {
        var indexProvider = indexProviderMap.lookup(index.getIndexProvider());
        try {
            var completedIndex = indexProvider.completeConfiguration(index, indexingBehaviour);
            return indexProvider.getOnlineAccessor(
                    completedIndex,
                    new IndexSamplingConfig(Config.defaults()),
                    tokenNameLookup,
                    ElementIdMapper.PLACEHOLDER,
                    openOptions,
                    indexingBehaviour);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private IndexPopulator constructIndexPopulator(IndexDescriptor index) {
        var indexProvider = tempIndexes.lookup(index.getIndexProvider());
        var populator = indexProvider.getPopulator(
                index,
                new IndexSamplingConfig(Config.defaults()),
                bufferFactory,
                EmptyMemoryTracker.INSTANCE,
                tokenNameLookup,
                ElementIdMapper.PLACEHOLDER,
                openOptions,
                indexingBehaviour);
        try {
            populator.create();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return populator;
    }

    /**
     * Schedules "scanCompleted" calls to any index populations that are part of this ID mapper,
     * such that they can be scheduled with "scanCompleted" calls to other index populations.
     */
    private void completeBuild(Collector collector, Consumer<Runnable> scheduler) {
        for (var population : indexBuilders.entrySet()) {
            // Complete the population of the increment index
            var builder = population.getValue();
            builder.flush();
            var populator = builder.populator;
            scheduler.accept(() -> {
                var conflictHandler = new RecordingIndexEntryConflictHandler(
                        collector,
                        violatingEntities,
                        population.getKey(),
                        tokenNameLookup,
                        entityIdFromIndexIdConverter);
                boolean successful = false;
                try {
                    populator.scanCompleted(PhaseTracker.nullInstance, workScheduler, conflictHandler, NULL_CONTEXT);
                    indexStatisticsStore.setSampleStats(population.getKey().getId(), populator.sample(NULL_CONTEXT));
                    successful = true;
                } catch (IndexEntryConflictException e) {
                    // Should not happen
                    throw new RuntimeException(e);
                } finally {
                    populator.close(successful, NULL_CONTEXT);
                }
            });
        }
    }

    /**
     * @return a list of entity IDs that violated constraints during complete (e.g. merging of indexes).
     */
    public LongSet validate(Collector collector) throws IOException {
        // Merge increments into copied-target-indexes and skip (and remember) those that violate constraints
        try (var scheduler = new BuildCompletionScheduler(workScheduler.jobScheduler())) {
            completeBuild(collector, scheduler);
        }

        var indexSamplingConfig = new IndexSamplingConfig(Config.defaults());
        for (var population : indexBuilders.entrySet()) {
            var descriptor = population.getKey();
            var builder = population.getValue();
            var conflictHandler = new RecordingIndexEntryConflictHandler(
                    collector, violatingEntities, descriptor, tokenNameLookup, entityIdFromIndexIdConverter);
            // For constraint indexes checking violations
            if (descriptor.isUnique() && !isEmpty(builder.accessor)) {
                // Validate uniqueness, since it's a constraint index
                try (var builtIncrementIndex = tempIndexes
                        .lookup(descriptor.getIndexProvider())
                        .getOnlineAccessor(
                                descriptor,
                                indexSamplingConfig,
                                tokenNameLookup,
                                ElementIdMapper.PLACEHOLDER,
                                openOptions,
                                indexingBehaviour)) {
                    builder.accessor.validate(
                            builtIncrementIndex,
                            true,
                            conflictHandler,
                            configuration.maxNumberOfWorkerThreads(),
                            workScheduler.jobScheduler());
                }
            }
        }
        return violatingEntities;
    }

    public void writeToTarget(LongPredicate skippedEntityIds) {
        try {
            // When all violations are known then merge all increment indexes
            LongPredicate filter = skippedEntityIds == null && violatingEntities.isEmpty()
                    ? null
                    : indexEntityId -> (skippedEntityIds == null || !skippedEntityIds.test(indexEntityId))
                            && !violatingEntities.contains(entityIdFromIndexIdConverter.applyAsLong(indexEntityId));
            var indexSamplingConfig = new IndexSamplingConfig(Config.defaults());
            for (var population : indexBuilders.entrySet()) {
                var descriptor = population.getKey();
                var builder = population.getValue();
                boolean targetIndexEmpty = isEmpty(builder.accessor);
                if (targetIndexEmpty && filter == null) {
                    builder.close();
                    moveIndex(fileSystem, tempIndexes, indexProviderMap, descriptor);
                } else {
                    try (var builtIncrementIndex = tempIndexes
                            .lookup(descriptor.getIndexProvider())
                            .getOnlineAccessor(
                                    descriptor,
                                    indexSamplingConfig,
                                    tokenNameLookup,
                                    ElementIdMapper.PLACEHOLDER,
                                    openOptions,
                                    indexingBehaviour)) {
                        builder.accessor.insertFrom(
                                builtIncrementIndex,
                                null,
                                false,
                                IndexEntryConflictHandler.THROW,
                                filter,
                                configuration.maxNumberOfWorkerThreads(),
                                workScheduler.jobScheduler(),
                                ProgressListener.NONE);
                        builder.accessor.force(FileFlushEvent.NULL, NULL_CONTEXT);
                    }
                }
            }
        } catch (IndexEntryConflictException e) {
            // This will not be thrown, but the method is declared to throw it so just catch it here
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isEmpty(IndexAccessor accessor) {
        try (var reader = accessor.newValueReader(IndexUsageTracking.NO_USAGE_TRACKING);
                var client = new SimpleEntityValueClient()) {
            reader.query(client, QueryContext.NULL_CONTEXT, NULL_CONTEXT, unconstrained(), allEntries());
            return !client.next();
        } catch (IndexNotApplicableKernelException e) {
            throw new RuntimeException(e);
        }
    }

    public LongSet affectedIndexes() {
        var ids = LongSets.mutable.empty();
        indexBuilders.keySet().stream().map(IndexDescriptor::getId).forEach(ids::add);
        return ids;
    }

    @Override
    public void close() throws IOException {
        List<AutoCloseable> toClose = new ArrayList<>(indexBuilders.values());
        toClose.add(bufferFactory);
        IOUtils.closeAll(toClose);
    }

    private record RecordingIndexEntryConflictHandler(
            Collector badCollector,
            MutableLongSet violatingEntities,
            IndexDescriptor descriptor,
            TokenNameLookup tokenNameLookup,
            LongToLongFunction entityIdFromIndexIdConverter)
            implements IndexEntryConflictHandler {

        @Override
        public IndexEntryConflictAction indexEntryConflict(long firstEntityId, long otherEntityId, Value[] values) {
            long realId = entityIdFromIndexIdConverter.applyAsLong(otherEntityId);
            violatingEntities.add(realId);
            badCollector.collectEntityViolatingConstraint(
                    null,
                    realId,
                    asPropertyMap(descriptor, values),
                    descriptor.userDescription(tokenNameLookup),
                    descriptor.schema().entityType());
            return IndexEntryConflictAction.DELETE;
        }

        private Map<String, Object> asPropertyMap(IndexDescriptor descriptor, Value[] values) {
            var properties = new HashMap<String, Object>();
            var propertyIds = descriptor.schema().getPropertyIds();
            for (var i = 0; i < propertyIds.length; i++) {
                properties.put(tokenNameLookup.propertyKeyGetName(propertyIds[i]), values[i].asObjectCopy());
            }
            return properties;
        }
    }

    private static class IndexBuilder implements AutoCloseable {
        private final IndexPopulator populator;
        private final List<IndexUpdatesBatch> allChanges = new CopyOnWriteArrayList<>();
        private final ThreadLocal<IndexUpdatesBatch> changes;
        private final IndexAccessor accessor;

        IndexBuilder(IndexPopulator populator, IndexAccessor accessor) {
            this.populator = populator;
            this.changes = ThreadLocal.withInitial(() -> {
                var indexUpdatesBatch = new IndexUpdatesBatch(accessor);
                allChanges.add(indexUpdatesBatch);
                return indexUpdatesBatch;
            });
            this.accessor = accessor;
        }

        void add(IndexEntryUpdate indexUpdate) {
            if (indexUpdate.updateMode() == UpdateMode.ADDED) {
                try {
                    populator.add(List.of(indexUpdate), NULL_CONTEXT);
                    populator.includeSample(indexUpdate);
                } catch (IndexEntryConflictException e) {
                    throw new RuntimeException(e);
                }
            } else {
                changes.get().add(indexUpdate);
            }
        }

        boolean addDirect(IndexEntryUpdate indexUpdate) {
            assert indexUpdate.updateMode() == UpdateMode.ADDED || indexUpdate.updateMode() == UpdateMode.REMOVED;
            try (var updater = accessor.newUpdater(IndexUpdateMode.DIRECT, NULL_CONTEXT, true)) {
                updater.process(indexUpdate);
            } catch (IndexEntryConflictException e) {
                return false;
            }
            return true;
        }

        void flush() {
            for (IndexUpdatesBatch batch : allChanges) {
                batch.flushChanges();
            }
        }

        @Override
        public void close() {
            accessor.close();
        }
    }

    private static class IndexUpdatesBatch {
        private static final int BATCH_SIZE = 100;

        private final List<IndexEntryUpdate> changes = new ArrayList<>();
        private final IndexAccessor accessor;

        IndexUpdatesBatch(IndexAccessor accessor) {
            this.accessor = accessor;
        }

        void add(IndexEntryUpdate indexUpdate) {
            changes.add(indexUpdate);
            if (changes.size() == BATCH_SIZE) {
                flushChanges();
            }
        }

        private void flushChanges() {
            try (var updater = accessor.newUpdater(IndexUpdateMode.ONLINE, NULL_CONTEXT, true)) {
                for (var change : changes) {
                    updater.process(change);
                }
                changes.clear();
            } catch (IndexEntryConflictException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

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

import static org.neo4j.configuration.GraphDatabaseInternalSettings.index_population_batch_max_byte_size;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.index_populator_block_size;
import static org.neo4j.internal.batchimport.IncrementalBatchImportUtil.moveIndex;
import static org.neo4j.internal.kernel.api.IndexQueryConstraints.unconstrained;
import static org.neo4j.internal.kernel.api.PropertyIndexQuery.allEntries;
import static org.neo4j.io.async.AsyncBlockAccessor.EMPTY_ASYNC_BLOCK_ACCESSOR;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.eclipse.collections.api.block.function.primitive.LongToLongFunction;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.configuration.Config;
import org.neo4j.internal.batchimport.cache.idmapping.IdMapper;
import org.neo4j.internal.helpers.progress.ProgressListener;
import org.neo4j.internal.helpers.progress.ProgressMonitorFactory;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
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
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.storageengine.api.schema.SimpleEntityValueClient;
import org.neo4j.values.ElementIdMapper;
import org.neo4j.values.storable.Value;

/**
 * Logic for building indexes during import. The idea is to have an {@link IndexPopulator index populator}
 * for each index that sees updates during import and let each thread (typically doing graph data updates and
 * generating index updates while doing so) add its share of updates to the index populators.
 */
public class ImportIndexBuilder implements Closeable {
    static final int BATCH_SIZE = 2_000;

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
    private final StorageEngineIndexingBehaviour indexingBehaviour;
    private final Predicate<IndexDescriptor> excludedIndexes;
    private final IndexSamplingConfig indexSamplingConfig;
    private final long maxBatchByteSize;
    private final IndexPopulator.Configuration indexPopulatorConfiguration;

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
            Predicate<IndexDescriptor> excludedIndexes,
            Config config,
            IndexPopulator.Configuration indexPopulatorConfiguration) {
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
        this.excludedIndexes = excludedIndexes;
        this.bufferFactory = new ByteBufferFactory(
                UnsafeDirectByteBufferAllocator::new,
                config.get(index_populator_block_size).intValue());
        this.maxBatchByteSize = config.get(index_population_batch_max_byte_size);
        this.indexPopulatorConfiguration = indexPopulatorConfiguration;
        this.indexSamplingConfig = new IndexSamplingConfig(Config.defaults());
    }

    public void add(IndexEntryUpdate indexUpdate) {
        final var indexKey = indexUpdate.indexKey();
        if (!excludedIndexes.test(indexKey)) {
            getIndexBuilder(indexKey).add(convertEntityId(indexUpdate));
        }
    }

    /**
     * Used to update uniqueness indexes and must be applied directly because the result
     * (whether conflicting or not) must be known as a result.
     * @return {@code true} if the index update was applied w/o problems, otherwise {@code false}.
     */
    public boolean addDirect(IndexEntryUpdate indexUpdate) {
        final var indexKey = indexUpdate.indexKey();
        if (!excludedIndexes.test(indexKey)) {
            return getIndexBuilder(indexKey).addDirect(convertEntityId(indexUpdate));
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
                    builder = new IndexBuilder(populator, accessor, maxBatchByteSize);
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
                    indexSamplingConfig,
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
                indexSamplingConfig,
                bufferFactory,
                EmptyMemoryTracker.INSTANCE,
                tokenNameLookup,
                ElementIdMapper.PLACEHOLDER,
                openOptions,
                indexingBehaviour,
                indexPopulatorConfiguration);
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
    private void completeBuild(
            MutableLongSet violatingEntityIds,
            Collector collector,
            Consumer<Runnable> scheduler,
            ProgressListener progress) {
        for (var population : indexBuilders.entrySet()) {
            // Complete the population of the increment index
            var builder = population.getValue();
            builder.flushAll();
            var populator = builder.populator;
            scheduler.accept(() -> {
                var conflictHandler = new RecordingIndexEntryConflictHandler(
                        collector,
                        violatingEntityIds,
                        population.getKey(),
                        tokenNameLookup,
                        entityIdFromIndexIdConverter);
                boolean successful = false;
                try {
                    populator.scanCompleted(
                            new PhaseTracker() {
                                @Override
                                public void enterPhase(Phase phase) {
                                    if (phase == Phase.BUILD || phase == Phase.APPLY_EXTERNAL) {
                                        progress.add(1);
                                    }
                                }

                                @Override
                                public void registerTime(Phase phase, long millis) {}

                                @Override
                                public void stop() {}
                            },
                            workScheduler,
                            conflictHandler,
                            NULL_CONTEXT);
                    indexStatisticsStore.setSampleStats(population.getKey().getId(), populator.sample(NULL_CONTEXT));
                    successful = true;
                    progress.add(1);
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
    public LongSet validate(Collector collector, ProgressMonitorFactory progressMonitorFactory) throws IOException {
        int totalProgressGoal = 0;
        for (var indexDescriptor : indexBuilders.keySet()) {
            totalProgressGoal += 2; // 1 for merge and 1 for building the tree
            if (indexDescriptor.isUnique()) {
                totalProgressGoal++; // for uniqueness validation
            }
        }
        try (var progress = progressMonitorFactory.singlePart("Validate indexes", totalProgressGoal)) {
            // Merge increments into copied-target-indexes and skip (and remember) those that violate constraints
            var violatingEntityIds = LongSets.mutable.empty().asSynchronized();
            try (var scheduler = new BuildCompletionScheduler(workScheduler.jobScheduler())) {
                completeBuild(violatingEntityIds, collector, scheduler, progress);
            }

            for (var population : indexBuilders.entrySet()) {
                var descriptor = population.getKey();
                var builder = population.getValue();
                var conflictHandler = new RecordingIndexEntryConflictHandler(
                        collector, violatingEntityIds, descriptor, tokenNameLookup, entityIdFromIndexIdConverter);
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
                    progress.add(1);
                }
            }
            return violatingEntityIds;
        }
    }

    /**
     * Writes new data (from "temp" indexes) into the target indexes. After this call all index data
     * will exist in the target {@link IndexAccessor} for each index.
     *
     * @param violatingIdMapperEntityIds entity IDs found by the
     * {@link IdMapper} to be duplicates.
     * @param otherViolatingEntityIds entity IDs found by other indexes to be duplicates, e.g. from
     * {@link #validate(Collector, ProgressMonitorFactory)}.
     * @param progressMonitorFactory for progress reporting.
     */
    public void writeToTarget(
            LongPredicate violatingIdMapperEntityIds,
            LongSet otherViolatingEntityIds,
            ProgressMonitorFactory progressMonitorFactory) {
        try (var progress = progressMonitorFactory.singlePart("Write indexes into target", indexBuilders.size())) {
            // When all violations are known then merge all increment indexes
            LongPredicate filter = violatingIdMapperEntityIds == null && otherViolatingEntityIds.isEmpty()
                    ? null
                    : indexEntityId ->
                            (violatingIdMapperEntityIds == null || !violatingIdMapperEntityIds.test(indexEntityId))
                                    && !otherViolatingEntityIds.contains(
                                            entityIdFromIndexIdConverter.applyAsLong(indexEntityId));
            for (var population : new HashMap<>(indexBuilders).entrySet()) {
                var descriptor = population.getKey();
                var builder = population.getValue();
                boolean targetIndexEmpty = isEmpty(builder.accessor);
                if (targetIndexEmpty && filter == null) {
                    // Close the index to be able to move it
                    builder.close();
                    moveIndex(fileSystem, tempIndexes, indexProviderMap, descriptor);
                    // Re-open the index so that it may accept removals or other updates afterward
                    indexBuilders.put(
                            descriptor, new IndexBuilder(null, constructIndexAccessor(descriptor), maxBatchByteSize));
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
                    }
                }
                progress.add(1);
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

    public Optional<IndexAccessor> openIndexAccessor(long indexId) throws IOException {
        var descriptor = indexDescriptor(indexId);
        if (descriptor.isEmpty()) {
            return Optional.empty();
        }
        var accessor = tempIndexes
                .lookup(descriptor.get().getIndexProvider())
                .getOnlineAccessor(
                        descriptor.get(),
                        indexSamplingConfig,
                        tokenNameLookup,
                        ElementIdMapper.PLACEHOLDER,
                        openOptions,
                        indexingBehaviour);
        return Optional.of(accessor);
    }

    public Optional<IndexDescriptor> indexDescriptor(long indexId) {
        return indexBuilders.keySet().stream()
                .filter(index -> index.getId() == indexId)
                .findFirst();
    }

    @Override
    public void close() throws IOException {
        List<AutoCloseable> toClose = new ArrayList<>(indexBuilders.values());
        toClose.add(bufferFactory);
        IOUtils.closeAll(toClose);
    }

    public static Map<String, Object> asPropertyMap(
            IndexDescriptor descriptor, Value[] values, TokenNameLookup tokenNameLookup) {
        var properties = new HashMap<String, Object>();
        var propertyIds = descriptor.schema().getPropertyIds();
        for (var i = 0; i < propertyIds.length; i++) {
            properties.put(tokenNameLookup.propertyKeyGetName(propertyIds[i]), values[i].asObjectCopy());
        }
        return properties;
    }

    /**
     * Flushes all changes and additions made by this thread to all its affected indexes.
     */
    public void flushOnSchemaMonitorClose() {
        for (var indexBuilder : indexBuilders.values()) {
            indexBuilder.flush();
        }
    }

    /**
     * @return {@code true} if there are no updates that would violate uniqueness.
     */
    public boolean checkUniqueness(ValueIndexEntryUpdate[] checks) {
        for (var check : checks) {
            try (var reader = getIndexBuilder(check.indexKey())
                            .accessor
                            .newValueReader(IndexUsageTracking.NO_USAGE_TRACKING);
                    var client = new SimpleEntityValueClient()) {
                reader.query(
                        client,
                        QueryContext.NULL_CONTEXT,
                        NULL_CONTEXT,
                        IndexQueryConstraints.unconstrained(),
                        PropertyIndexQuery.exact(0, check.values()[0]));
                if (client.next()) {
                    return false;
                }
            } catch (IndexNotApplicableKernelException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
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
                    asPropertyMap(descriptor, values, tokenNameLookup),
                    descriptor.userDescription(tokenNameLookup),
                    descriptor.schema().entityType());
            return IndexEntryConflictAction.DELETE;
        }
    }

    private static class IndexBuilder implements AutoCloseable {
        private final IndexPopulator populator;
        private final List<IndexUpdatesBatch> allChanges = new CopyOnWriteArrayList<>();
        private final ThreadLocal<IndexUpdatesBatch> changes;
        private final IndexAccessor accessor;

        IndexBuilder(IndexPopulator populator, IndexAccessor accessor, long maxByteSize) {
            this.populator = populator;
            this.changes = ThreadLocal.withInitial(() -> {
                var indexUpdatesBatch = new IndexUpdatesBatch(populator, accessor, maxByteSize);
                allChanges.add(indexUpdatesBatch);
                return indexUpdatesBatch;
            });
            this.accessor = accessor;
        }

        void add(IndexEntryUpdate indexUpdate) {
            changes.get().add(indexUpdate);
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
            changes.get().flush();
        }

        boolean flushAll() {
            boolean hasChanges = false;
            for (IndexUpdatesBatch batch : allChanges) {
                hasChanges |= batch.flush();
            }
            return hasChanges;
        }

        @Override
        public void close() {
            flushAll();
            accessor.force(FileFlushEvent.NULL, EMPTY_ASYNC_BLOCK_ACCESSOR, NULL_CONTEXT);
            accessor.close();
        }
    }

    private static class IndexUpdatesBatch {
        private final List<IndexEntryUpdate> changes = new ArrayList<>();
        private List<IndexEntryUpdate> additions = new ArrayList<>(BATCH_SIZE);
        private final IndexPopulator populator;
        private final IndexAccessor accessor;
        private final long maxByteSize;
        private long additionsByteSize;
        private long changesByteSize;

        IndexUpdatesBatch(IndexPopulator populator, IndexAccessor accessor, long maxByteSize) {
            this.populator = populator;
            this.accessor = accessor;
            this.maxByteSize = maxByteSize;
        }

        void add(IndexEntryUpdate indexUpdate) {
            if (indexUpdate.updateMode() == UpdateMode.ADDED) {
                additionsByteSize += indexUpdate.roughSizeOfUpdate();
                additions.add(indexUpdate);
                populator.includeSample(indexUpdate);
                if (additions.size() == BATCH_SIZE) {
                    flushAdditions();
                }
            } else {
                changesByteSize += indexUpdate.roughSizeOfUpdate();
                changes.add(indexUpdate);
                if (changes.size() == BATCH_SIZE) {
                    flushChanges();
                }
            }

            if (additionsByteSize + changesByteSize >= maxByteSize) {
                // Typically for small or "normally" sized values this won't trigger, but for large values
                // it's good to have a fail-safe so that not too much memory is spent on holding onto these
                // large values.
                flush();
            }
        }

        private boolean flush() {
            boolean hasChanges = false;
            hasChanges |= flushAdditions();
            hasChanges |= flushChanges();
            return hasChanges;
        }

        private boolean flushAdditions() {
            try {
                if (!additions.isEmpty()) {
                    populator.add(additions, NULL_CONTEXT);
                    additions = new ArrayList<>(BATCH_SIZE);
                    additionsByteSize = 0;
                    return true;
                }
                return false;
            } catch (IndexEntryConflictException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean flushChanges() {
            if (changes.isEmpty()) {
                return false;
            }

            try (var updater = accessor.newUpdater(IndexUpdateMode.ONLINE, NULL_CONTEXT, true)) {
                for (var change : changes) {
                    updater.process(change);
                }
                changes.clear();
                changesByteSize = 0;
                return true;
            } catch (IndexEntryConflictException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

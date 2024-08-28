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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.OpenOption;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.collections.api.block.function.primitive.LongToLongFunction;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.common.EntityType;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.SchemaCache;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.impl.api.index.IndexProviderMap;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

/**
 * uniqueness constraint index (prepare copies these):
 * - neo4j/schema/index/range-1.0/3
 * - copy to neo4j-incremental-12345/schema/index/range-1.0/3
 * - build: neo4j-incremental-12345/temp-schema/index/range-1.0/3
 *    - validate(): merge neo4j-incremental-12345/temp-schema/index/range-1.0/3 --> neo4j-incremental-12345/schema/index/range-1.0/3
 * - merge: move neo4j-incremental-12345/schema/index/range-1.0/3 --> neo4j/schema/index/range-1.0/3
 *
 * non-uniqueness constraint index (prepare does not copy these):
 * - neo4j/schema/index/range-1.0/3
 * - build: neo4j-incremental-12345/temp-schema/index/range-1.0/3
 *    - validate(): move neo4j-incremental-12345/temp-schema/index/range-1.0/3 --> neo4j-incremental-12345/schema/index/range-1.0/3
 * - merge: merge neo4j-incremental-12345/schema/index/range-1.0/3 --> neo4j/schema/index/range-1.0/3
 */
public class OtherAffectedSchemaMonitors implements Supplier<SchemaMonitor>, Closeable {
    private final SchemaCache schemaCache;
    private final EntityType entityType;
    private final LongToLongFunction indexedEntityIdConverter;
    private final ImportPropertyConstraintEnforcer propertyExistenceConstraints;
    private final ImportIndexBuilder indexBuilder;

    public OtherAffectedSchemaMonitors(
            FileSystemAbstraction fileSystem,
            IndexProviderMap indexProviderMap,
            IndexProviderMap tempIndexes,
            SchemaCache schemaCache,
            TokenNameLookup tokenNameLookup,
            EntityType entityType,
            ImmutableSet<OpenOption> openOptions,
            PopulationWorkJobScheduler workScheduler,
            LongToLongFunction indexedEntityIdConverter,
            LongToLongFunction entityIdFromIndexIdConverter,
            Configuration configuration,
            IndexStatisticsStore indexStatisticsStore,
            StorageEngineIndexingBehaviour indexingBehaviour,
            boolean incrementalIndexing) {
        this.schemaCache = schemaCache;
        this.entityType = entityType;
        this.indexedEntityIdConverter = indexedEntityIdConverter;
        this.propertyExistenceConstraints = new ImportPropertyConstraintEnforcer(schemaCache, entityType);
        this.indexBuilder = new ImportIndexBuilder(
                fileSystem,
                indexProviderMap,
                tempIndexes,
                tokenNameLookup,
                openOptions,
                workScheduler,
                // We'll do this conversion ourselves when constructing the updates
                id -> id,
                entityIdFromIndexIdConverter,
                configuration,
                indexStatisticsStore,
                indexingBehaviour,
                incrementalIndexing);
    }

    /**
     * Will be invoked once for each worker, i.e. this should create a new monitor used by a single thread.
     */
    @Override
    public SchemaMonitor get() {
        return new OtherAffectedSchemaMonitor();
    }

    /**
     * Schedules "scanCompleted" calls to any index populations that are part of this ID mapper,
     * such that they can be scheduled with "scanCompleted" calls to other index populations.
     */
    public void completeBuild(Collector collector, Consumer<Runnable> scheduler) {
        indexBuilder.completeBuild(collector, scheduler);
    }

    /**
     * @return a list of entity IDs that violated constraints during complete (e.g. merging of indexes).
     */
    public LongSet validate(LongSet skippedEntityIds, Collector collector) {
        return indexBuilder.validate(skippedEntityIds, collector);
    }

    @Override
    public void close() throws IOException {
        indexBuilder.close();
    }

    public LongSet affectedIndexes() {
        return indexBuilder.affectedIndexes();
    }

    private class OtherAffectedSchemaMonitor implements SchemaMonitor {
        private final MutableIntList entityTokens = IntLists.mutable.empty();
        private final MutableIntObjectMap<Value> properties = IntObjectMaps.mutable.empty();

        @Override
        public void property(int propertyKeyId, Object value) {
            if (value instanceof Value propValue) {
                properties.put(propertyKeyId, propValue);
            } else {
                properties.put(propertyKeyId, Values.of(value));
            }
        }

        @Override
        public void entityToken(int entityTokenId) {
            entityTokens.add(entityTokenId);
        }

        @Override
        public void entityTokens(int[] entityTokenIds) {
            entityTokens.addAll(entityTokenIds);
        }

        @Override
        public boolean endOfEntity(long entityId, ViolationVisitor violationVisitor) {
            try {
                entityTokens.sortThis();
                boolean propertyExistenceOk = checkPropertyExistenceConstraints(entityId, violationVisitor);
                if (propertyExistenceOk) {
                    generateIndexUpdatesForAffectedIndexes(entityId);
                }
                return propertyExistenceOk;
            } finally {
                entityTokens.clear();
                properties.clear();
            }
        }

        private void generateIndexUpdatesForAffectedIndexes(long entityId) {
            // TODO might be a bit expensive?
            var propertyKeyTokens = properties.keySet().toSortedArray();
            var indexes = schemaCache.getValueIndexesRelatedTo(
                    entityTokens.toArray(), EMPTY_INT_ARRAY, propertyKeyTokens, true, entityType);
            for (var index : indexes) {
                indexBuilder.add(constructIndexUpdate(entityId, index));
            }
        }

        private IndexEntryUpdate<IndexDescriptor> constructIndexUpdate(long entityId, IndexDescriptor index) {
            var propertyIds = index.schema().getPropertyIds();
            Value[] values = new Value[propertyIds.length];
            for (int i = 0; i < propertyIds.length; i++) {
                values[i] = properties.get(propertyIds[i]);
            }
            return IndexEntryUpdate.add(indexedEntityIdConverter.applyAsLong(entityId), index, values);
        }

        private boolean checkPropertyExistenceConstraints(long entityId, ViolationVisitor violationVisitor) {
            if (!propertyExistenceConstraints.isEmpty()) {
                var entityTokensIterator = entityTokens.intIterator();
                while (entityTokensIterator.hasNext()) {
                    var mandatoryPropertyKeys =
                            propertyExistenceConstraints.mandatoryPropertyKeys(entityTokensIterator.next());
                    if (mandatoryPropertyKeys != null) {
                        if (!properties.keySet().containsAll(mandatoryPropertyKeys)) {
                            violationVisitor.accept(
                                    entityId, entityTokens, properties, "a property existence constraint");
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}

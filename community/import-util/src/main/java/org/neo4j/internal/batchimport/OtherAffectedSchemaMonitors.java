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

import java.io.IOException;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.eclipse.collections.api.block.function.primitive.LongToLongFunction;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.input.ApplicationMode;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.common.EntityType;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.configuration.Config;
import org.neo4j.internal.helpers.progress.ProgressMonitorFactory;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.SchemaCache;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.internal.schema.constraints.TypeRepresentation;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.impl.api.index.IndexProviderMap;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

/**
 * uniqueness constraint index (prepare copies these):
 * - neo4j/schema/index/range-1.0/3
 * - copy to neo4j-incremental-12345/schema/index/range-1.0/3
 * - build: neo4j-incremental-12345/temp-schema/index/range-1.0/3
 *    - validate(): merge neo4j-incremental-12345/temp-schema/index/range-1.0/3 --> neo4j-incremental-12345/schema/index/range-1.0/3
 * - merge: move neo4j-incremental-12345/schema/index/range-1.0/3 --> neo4j/schema/index/range-1.0/3
 *<p>
 * non-uniqueness constraint index (prepare does not copy these):
 * - neo4j/schema/index/range-1.0/3
 * - build: neo4j-incremental-12345/temp-schema/index/range-1.0/3
 *    - validate(): move neo4j-incremental-12345/temp-schema/index/range-1.0/3 --> neo4j-incremental-12345/schema/index/range-1.0/3
 * - merge: merge neo4j-incremental-12345/schema/index/range-1.0/3 --> neo4j/schema/index/range-1.0/3
 *<p>
 * Regarding index update, and especially uniqueness indexes, this class focuses on the ability to make decisions
 * based on the data given to it - (for updates) not existing data of the entity. Almost all checks and
 * generated updates can be made based on the new data (delta for updates), and for the rest there's
 * {@link SchemaMonitor#indexUpdate(IndexEntryUpdate)}.
 */
public class OtherAffectedSchemaMonitors implements SchemaMonitors {
    private final SchemaCache schemaCache;
    private final EntityType entityType;
    private final LongToLongFunction indexedEntityIdConverter;
    private final boolean generateNonUniqueIndexUpdates;
    private final ImportPropertyConstraintEnforcer propertyConstraints;
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
            boolean generateNonUniqueIndexUpdates,
            Predicate<IndexDescriptor> excludedIndexes,
            Config config) {
        this.schemaCache = schemaCache;
        this.entityType = entityType;
        this.indexedEntityIdConverter = indexedEntityIdConverter;
        this.generateNonUniqueIndexUpdates = generateNonUniqueIndexUpdates;
        this.propertyConstraints = new ImportPropertyConstraintEnforcer(schemaCache, entityType);
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
                excludedIndexes,
                config);
    }

    /**
     * Will be invoked once for each worker, i.e. this should create a new monitor used by a single thread.
     */
    @Override
    public SchemaMonitor get() {
        return new OtherAffectedSchemaMonitor();
    }

    /**
     * @return a list of entity IDs that violated constraints during complete (e.g. merging of indexes).
     */
    @Override
    public LongSet validate(Collector collector, ProgressMonitorFactory progressMonitorFactory) throws IOException {
        return indexBuilder.validate(collector, progressMonitorFactory);
    }

    @Override
    public void writeToTarget(
            LongPredicate violatingIdMapperEntityIds,
            LongSet otherViolatingEntityIds,
            ProgressMonitorFactory progressMonitorFactory) {
        indexBuilder.writeToTarget(violatingIdMapperEntityIds, otherViolatingEntityIds, progressMonitorFactory);
    }

    @Override
    public void close() throws IOException {
        indexBuilder.close();
    }

    @Override
    public LongSet affectedIndexes() {
        return indexBuilder.affectedIndexes();
    }

    @Override
    public Optional<IndexAccessor> openIndexAccessor(long indexId) throws IOException {
        return indexBuilder.openIndexAccessor(indexId);
    }

    @Override
    public Optional<IndexDescriptor> indexDescriptor(long indexId) {
        return indexBuilder.indexDescriptor(indexId);
    }

    private class OtherAffectedSchemaMonitor implements SchemaMonitor {
        private final MutableIntList entityTokens = IntLists.mutable.empty();
        private final MutableIntList existingEntityTokens = IntLists.mutable.empty();
        private final MutableIntSet removedEntityTokens = IntSets.mutable.empty();
        private final MutableIntObjectMap<Value> properties = IntObjectMaps.mutable.empty();
        private final MutableIntSet removedProperties = IntSets.mutable.empty();
        private final MutableIntSet identifierPropertyKeys = IntSets.mutable.empty();
        private ApplicationMode mode;

        @Override
        public void applicationMode(ApplicationMode mode) {
            this.mode = mode;
        }

        @Override
        public void property(int propertyKeyId, Object value, boolean identifier) {
            var propertyValue = value instanceof Value v ? v : Values.of(value);
            properties.put(propertyKeyId, propertyValue);
            if (identifier) {
                identifierPropertyKeys.add(propertyKeyId);
            }
        }

        @Override
        public void removedProperty(int propertyKeyId) {
            removedProperties.add(propertyKeyId);
        }

        @Override
        public void existingEntityTokens(int[] entityTokens) {
            existingEntityTokens.addAll(entityTokens);
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
        public void removedEntityTokens(int[] entityTokens) {
            removedEntityTokens.addAll(entityTokens);
        }

        @Override
        public boolean endOfEntity(
                long entityId,
                ExistingPropertyKeysLookup existingPropertyKeysLookup,
                ViolationVisitor violationVisitor) {
            try {
                entityTokens.sortThis();
                if (mode == null || mode == ApplicationMode.CREATE) {
                    boolean propertyExistenceOk = checkPropertyExistenceConstraintsOnCreate(entityId, violationVisitor);
                    boolean propertyTypesOk = checkPropertyTypeConstraints(entityId, violationVisitor);
                    if (propertyExistenceOk && propertyTypesOk && generateNonUniqueIndexUpdates) {
                        // For CREATE all index updates are simply added to each respective index populator
                        // and uniqueness violations will be sorted out afterwards, where violating entities
                        // are deleted.
                        generateIndexUpdatesForCreatedEntity(entityId);
                    }
                    return propertyExistenceOk && propertyTypesOk;
                } else if (mode == ApplicationMode.UPDATE) {
                    boolean propertyExistenceOk = checkPropertyExistenceConstraintsOnUpdate(
                            entityId, existingPropertyKeysLookup, violationVisitor);
                    boolean propertyTypesOk = checkPropertyTypeConstraints(entityId, violationVisitor);
                    if (propertyExistenceOk && propertyTypesOk) {
                        // For UPDATE at least uniqueness index updates needs to be generated so that their
                        // ADD part can be written to the indexes and validated right here.
                        return generateAndValidateUniquenessIndexUpdates(entityId, violationVisitor);
                    } else {
                        return false;
                    }
                } else {
                    return true;
                }
            } finally {
                reset();
            }
        }

        private boolean checkPropertyExistenceConstraintsOnUpdate(
                long entityId,
                ExistingPropertyKeysLookup existingPropertyKeysLookup,
                ViolationVisitor violationVisitor) {
            if (!entityTokens.isEmpty()) {
                // Check if all added properties in this input entity satisfies the mandatory properties
                // if not then we must load the existing property keys for this node and check
                var mandatoryPropertyKeys = propertyConstraints.mandatoryPropertyKeys(entityTokens.toArray());
                if (!mandatoryPropertyKeys.isEmpty()) {
                    var mandatoryPropertyKeysLeftToCheck = IntSets.mutable.ofAll(mandatoryPropertyKeys);
                    properties.keySet().forEach(mandatoryPropertyKeysLeftToCheck::remove);
                    if (!mandatoryPropertyKeysLeftToCheck.isEmpty()) {
                        var existingRemainingKeys = existingPropertyKeysLookup.lookupPropertyKeys(
                                entityId, mandatoryPropertyKeysLeftToCheck);
                        existingRemainingKeys.forEach(key -> {
                            if (!removedProperties.contains(key)) {
                                mandatoryPropertyKeysLeftToCheck.remove(key);
                            }
                        });
                    }
                    if (!mandatoryPropertyKeysLeftToCheck.isEmpty()) {
                        violationVisitor.accept(entityId, entityTokens, properties, "a property existence constraint");
                        return false;
                    }
                }
            }

            if (!removedProperties.isEmpty()) {
                var affectedLabels = propertyConstraints.entityTokensRelatedToPropertyKeys(removedProperties.toArray());
                if (!affectedLabels.isEmpty()) {
                    var affectedLabelsLeftToCheck = IntSets.mutable.ofAll(affectedLabels);
                    affectedLabelsLeftToCheck.removeAll(removedEntityTokens);
                    if (affectedLabelsLeftToCheck.containsAny(existingEntityTokens)
                            || affectedLabelsLeftToCheck.containsAny(entityTokens)) {
                        violationVisitor.accept(entityId, entityTokens, properties, "a property existence constraint");
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Used for allowing external code, which is applying changes to the store, generate relevant index updates
         * for this entity. Any updates to uniqueness indexes will be converted to removals (of the before-values),
         * making this method essential and interlinked with
         * {@link #endOfEntity(long, ExistingPropertyKeysLookup, ViolationVisitor)} for updated entities.
         * @see #generateAndValidateUniquenessIndexUpdates(long, ViolationVisitor)
         */
        @Override
        public void indexUpdate(IndexEntryUpdate indexUpdate) {
            // TODO can we make this general assumption here? It's probably good because the splitting of
            //  uniqueness index updates to just do the ADD part is _also_ in this monitor.
            if (indexUpdate.indexKey().isUnique() && indexUpdate.updateMode() == UpdateMode.CHANGED) {
                indexUpdate = asRemoval(indexUpdate);
            }

            indexBuilder.add(indexUpdate);
        }

        @Override
        public void reset() {
            entityTokens.clear();
            properties.clear();
            removedProperties.clear();
            existingEntityTokens.clear();
            removedEntityTokens.clear();
            mode = null;
            identifierPropertyKeys.clear();
        }

        private void generateIndexUpdatesForCreatedEntity(long entityId) {
            var propertyKeyTokens = properties.keySet().toSortedArray();
            var indexes = schemaCache.getValueIndexesRelatedTo(
                    entityTokens.toArray(), EMPTY_INT_ARRAY, propertyKeyTokens, true, entityType);
            for (var index : indexes) {
                indexBuilder.add(constructIndexUpdate(entityId, index));
            }
        }

        /**
         * Used when updating an entity. Based on the data known about this entity, and also assuming that
         * uniqueness indexes always consists of a single entityToken/propertyKey pair, see which uniqueness indexes
         * this entity is associated with and attempt to add this entity into all such uniqueness indexes.
         * The trick here is that we don't need to know the previous values of the properties - instead just
         * add the new values to the affected indexes (atomically, i.e. if any fails then undo them all).
         * This relies on the actual index updates carrying the before-values will arrive later in
         * calls to {@link #indexUpdate(IndexEntryUpdate)}, where such updates will be transformed into
         * only removal of the before-values - thereby completing the updates.
         */
        private boolean generateAndValidateUniquenessIndexUpdates(long entityId, ViolationVisitor violationVisitor) {
            var propertyKeyTokens = identifierPropertyKeys.isEmpty()
                    ? properties.keySet().toSortedArray()
                    : properties
                            .keySet()
                            .reject(identifierPropertyKeys::contains)
                            .toSortedArray();
            var allEntityTokens = IntSets.mutable.ofAll(existingEntityTokens);
            allEntityTokens.addAll(entityTokens);
            var indexes = schemaCache.getValueIndexesRelatedTo(
                    allEntityTokens.toSortedArray(), EMPTY_INT_ARRAY, propertyKeyTokens, true, entityType);
            if (!indexes.isEmpty()) {
                List<IndexEntryUpdate> appliedAdditions = new ArrayList<>();
                boolean failed = false;
                for (var index : indexes) {
                    if (index.isUnique()) {
                        var indexUpdate = constructIndexUpdate(entityId, index);
                        if (indexBuilder.addDirect(indexUpdate)) {
                            appliedAdditions.add(indexUpdate);
                        } else {
                            failed = true;
                            violationVisitor.accept(entityId, entityTokens, properties, index.toString());
                            break;
                        }
                    }
                }
                if (failed) {
                    for (var updateToUndo : appliedAdditions) {
                        boolean removed = indexBuilder.addDirect(asRemoval(updateToUndo));
                        assert removed;
                    }
                    return false;
                }
            }
            return true;
        }

        private ValueIndexEntryUpdate asRemoval(IndexEntryUpdate update) {
            var valueUpdate = (ValueIndexEntryUpdate) update;
            return ValueIndexEntryUpdate.remove(update.getEntityId(), update.indexKey(), valueUpdate.beforeValues());
        }

        private IndexEntryUpdate constructIndexUpdate(long entityId, IndexDescriptor index) {
            var propertyIds = index.schema().getPropertyIds();
            Value[] values = new Value[propertyIds.length];
            for (int i = 0; i < propertyIds.length; i++) {
                values[i] = properties.get(propertyIds[i]);
            }
            return ValueIndexEntryUpdate.add(indexedEntityIdConverter.applyAsLong(entityId), index, values);
        }

        private boolean checkPropertyExistenceConstraintsOnCreate(long entityId, ViolationVisitor violationVisitor) {
            if (propertyConstraints.hasPropertyExistenceConstraints()) {
                var entityTokensIterator = entityTokens.intIterator();
                while (entityTokensIterator.hasNext()) {
                    var mandatoryPropertyKeys = propertyConstraints.mandatoryPropertyKeys(entityTokensIterator.next());
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

        private boolean checkPropertyTypeConstraints(long entityId, ViolationVisitor violationVisitor) {
            if (propertyConstraints.hasPropertyTypeConstraints()) {
                return checkPropertyTypeConstraints(entityId, violationVisitor, entityTokens)
                        && checkPropertyTypeConstraints(entityId, violationVisitor, existingEntityTokens);
            }
            return true;
        }

        private boolean checkPropertyTypeConstraints(long entityId, ViolationVisitor violationVisitor, IntList tokens) {
            var tokenIterator = tokens.intIterator();
            while (tokenIterator.hasNext()) {
                int token = tokenIterator.next();
                for (var typeConstraint : propertyConstraints.propertyTypeConstraints(token)) {
                    var value = properties.get(typeConstraint.propertyKeyId());
                    if (TypeRepresentation.disallows(typeConstraint.type(), value)) {
                        violationVisitor.accept(
                                entityId, entityTokens, properties, "a property type constraint " + value);
                        return false;
                    }
                }
            }
            return true;
        }
    }
}

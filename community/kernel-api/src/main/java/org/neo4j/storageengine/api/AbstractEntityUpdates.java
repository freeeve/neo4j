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
package org.neo4j.storageengine.api;

import static org.neo4j.internal.schema.SchemaPatternMatchingType.COMPLETE_ALL_TOKENS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.neo4j.collection.PrimitiveArrays;
import org.neo4j.common.EntityType;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.internal.schema.SchemaPatternMatchingType;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.values.storable.Value;

public abstract class AbstractEntityUpdates<T extends AbstractEntityUpdates.PropertyValueInterface> {
    protected final MutableIntObjectMap<T> knownProperties;

    public abstract static class Builder<
            P extends AbstractEntityUpdates.PropertyValueInterface,
            T extends AbstractEntityUpdates<P>,
            R extends Builder<P, T, R>> {

        protected final T updates;

        protected Builder(T updates) {
            this.updates = updates;
        }

        public final R added(int propertyKeyId, Value value) {
            updates.put(propertyKeyId, updates.after(value));
            return (R) this;
        }

        public final R removed(int propertyKeyId, Value value) {
            updates.put(propertyKeyId, updates.before(value));
            return (R) this;
        }

        public final R changed(int propertyKeyId, Value before, Value after) {
            updates.put(propertyKeyId, updates.changed(before, after));
            return (R) this;
        }

        public final R existing(int propertyKeyId, Value value) {
            updates.put(propertyKeyId, updates.unchanged(value));
            return (R) this;
        }

        public final R withTokens(int... entityTokens) {
            this.updates.entityTokensBefore = entityTokens;
            this.updates.entityTokensAfter = entityTokens;
            return (R) this;
        }

        public final R withTokensBefore(int... entityTokensBefore) {
            this.updates.entityTokensBefore = entityTokensBefore;
            return (R) this;
        }

        public final R withTokensAfter(int... entityTokensAfter) {
            this.updates.entityTokensAfter = entityTokensAfter;
            return (R) this;
        }

        public final T build() {
            return updates;
        }
    }

    protected final long entityId;

    // ASSUMPTION: these long arrays are actually sorted sets
    protected int[] entityTokensBefore;
    protected int[] entityTokensAfter;
    private final boolean propertyListComplete;
    protected int[] propertyKeyIds;
    protected int propertyKeyIdsCursor;
    private boolean hasLoadedAdditionalProperties;

    protected AbstractEntityUpdates(
            long entityId, int[] entityTokensBefore, int[] entityTokensAfter, boolean propertyListComplete) {
        this.entityId = entityId;
        this.entityTokensBefore = entityTokensBefore;
        this.entityTokensAfter = entityTokensAfter;
        this.propertyListComplete = propertyListComplete;
        this.propertyKeyIds = new int[8];
        this.knownProperties = new IntObjectHashMap<>();
    }

    protected abstract IndexEntryUpdate remove(IndexDescriptor indexKey, int[] propertyIds, boolean defaultToNoValue);

    protected abstract IndexEntryUpdate add(IndexDescriptor indexKey, int[] propertyIds);

    protected abstract IndexEntryUpdate change(IndexDescriptor indexKey, int[] propertyIds, boolean defaultToNoValue);

    protected abstract T before(Value value);

    protected abstract T after(Value value);

    protected abstract T unchanged(Value value);

    protected abstract T changed(Value before, Value after);

    protected abstract T noValue();

    public final long getEntityId() {
        return entityId;
    }

    public final int[] entityTokensChanged() {
        return PrimitiveArrays.symmetricDifference(entityTokensBefore, entityTokensAfter);
    }

    public final int[] entityTokensUnchanged() {
        return PrimitiveArrays.intersect(entityTokensBefore, entityTokensAfter);
    }

    /**
     * @return whether the list of properties is complete on this node.
     * If {@code false} then the list may contain some properties, whereas there may be other unloaded properties on the persisted existing node.
     */
    public final boolean isPropertyListComplete() {
        return propertyListComplete;
    }

    protected final T knownProperty(int propertyId, boolean defaultToNoValue) {
        T value = knownProperties.get(propertyId);
        if (value == null && defaultToNoValue) {
            value = noValue();
        }
        return value;
    }

    protected final boolean hasKnownProperty(int propertyId) {
        return knownProperties.containsKey(propertyId);
    }

    protected final void put(int propertyKeyId, T propertyValueSupplier) {
        T existing = knownProperties.put(propertyKeyId, propertyValueSupplier);
        if (existing == null) {
            if (propertyKeyIdsCursor >= propertyKeyIds.length) {
                propertyKeyIds = Arrays.copyOf(propertyKeyIds, propertyKeyIdsCursor * 2);
            }
            propertyKeyIds[propertyKeyIdsCursor++] = propertyKeyId;
        }
    }

    public final int[] propertiesChanged() {
        assert !hasLoadedAdditionalProperties
                : "Calling propertiesChanged() is not valid after non-changed "
                        + "properties have already been loaded.";
        Arrays.sort(propertyKeyIds, 0, propertyKeyIdsCursor);
        return propertyKeyIdsCursor == propertyKeyIds.length
                ? propertyKeyIds
                : Arrays.copyOf(propertyKeyIds, propertyKeyIdsCursor);
    }

    /**
     * Matches the provided schema descriptors to the entity updates in this object, and generates an IndexEntryUpdate
     * for any value index that needs to be updated.
     *<p
     * Note that unless this object contains a full representation of the node state after the update, the results
     * from this methods will not be correct. In that case, use the propertyLoader variant.
     *
     * @param indexKeys The index keys to generate entry updates for
     * @return IndexEntryUpdates for all relevant index keys
     */
    public final List<IndexEntryUpdate> valueUpdatesForIndexKeys(Iterable<IndexDescriptor> indexKeys) {
        Iterable<IndexDescriptor> potentiallyRelevant =
                Iterables.filter(indexKeys, indexKey -> atLeastOneRelevantChange(indexKey.schema()));

        List<IndexEntryUpdate> indexUpdates = new ArrayList<>();
        gatherUpdatesForPotentials(indexUpdates::add, potentiallyRelevant, true);
        return indexUpdates;
    }

    public final void consumeValueUpdatesForIndexKeys(
            Consumer<IndexEntryUpdate> indexUpdates, Iterable<IndexDescriptor> indexKeys) {
        Iterable<IndexDescriptor> potentiallyRelevant =
                Iterables.filter(indexKeys, indexKey -> atLeastOneRelevantChange(indexKey.schema()));

        gatherUpdatesForPotentials(indexUpdates, potentiallyRelevant, true);
    }

    /**
     * Matches the provided schema descriptors to the entity updates in this object, and generates an IndexEntryUpdate
     * for any value index that needs to be updated.
     * <p/>
     * In some cases the updates to an entity are not enough to determine whether some index should be affected. For
     * example if we have and index of label :A and property p1, and :A is added to this node, we cannot say whether
     * this should affect the index unless we know if this node has property p1. This get even more complicated for
     * composite indexes. To solve this problem, a propertyLoader is used to load any additional properties needed to
     * make these calls.
     * </p>
     * Note: This method will eagerly load any missing properties even when called on a {@link LazyEntityUpdates}.
     *
     * @param indexKeys The index keys to generate entry updates for
     * @param reader The property loader used to fetch needed additional properties
     * @param type EntityType of the indexes
     * @return IndexEntryUpdates for all relevant index keys
     */
    public final Iterable<IndexEntryUpdate> valueUpdatesForIndexKeys(
            Iterable<IndexDescriptor> indexKeys,
            StorageReader reader,
            EntityType type,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker) {
        List<IndexDescriptor> potentiallyRelevant = new ArrayList<>();
        final MutableIntSet additionalPropertiesToLoad = new IntHashSet();

        for (var indexKey : indexKeys) {
            if (atLeastOneRelevantChange(indexKey.schema())) {
                potentiallyRelevant.add(indexKey);
                gatherPropsToLoad(indexKey.schema(), additionalPropertiesToLoad);
            }
        }

        if (!additionalPropertiesToLoad.isEmpty()) {
            loadProperties(reader, additionalPropertiesToLoad, type, cursorContext, storeCursors, memoryTracker);
        }

        List<IndexEntryUpdate> indexUpdates = new ArrayList<>();
        gatherUpdatesForPotentials(indexUpdates::add, potentiallyRelevant, false);
        return indexUpdates;
    }

    private void loadProperties(
            StorageReader reader,
            MutableIntSet additionalPropertiesToLoad,
            EntityType type,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker) {
        hasLoadedAdditionalProperties = true;
        if (type == EntityType.NODE) {
            try (StorageNodeCursor cursor = reader.allocateNodeCursor(cursorContext, storeCursors, memoryTracker)) {
                cursor.single(entityId);
                loadProperties(reader, cursor, additionalPropertiesToLoad, cursorContext, storeCursors, memoryTracker);
            }
        } else if (type == EntityType.RELATIONSHIP) {
            try (StorageRelationshipScanCursor cursor =
                    reader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker)) {
                cursor.single(entityId);
                loadProperties(reader, cursor, additionalPropertiesToLoad, cursorContext, storeCursors, memoryTracker);
            }
        }

        // loadProperties removes loaded properties from the input set, so the remaining ones were not on the node
        final IntIterator propertiesWithNoValue = additionalPropertiesToLoad.intIterator();
        while (propertiesWithNoValue.hasNext()) {
            put(propertiesWithNoValue.next(), noValue());
        }
    }

    private void loadProperties(
            StorageReader reader,
            StorageEntityCursor cursor,
            MutableIntSet additionalPropertiesToLoad,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker) {
        if (cursor.next() && cursor.hasProperties()) {
            try (StoragePropertyCursor propertyCursor =
                    reader.allocatePropertyCursor(cursorContext, storeCursors, memoryTracker)) {
                cursor.properties(propertyCursor, PropertySelection.selection(additionalPropertiesToLoad.toArray()));
                while (propertyCursor.next()) {
                    additionalPropertiesToLoad.remove(propertyCursor.propertyKey());
                    put(propertyCursor.propertyKey(), unchanged(propertyCursor.propertyValue()));
                }
            }
        }
    }

    private void gatherPropsToLoad(SchemaDescriptor schema, MutableIntSet target) {
        for (int propertyId : schema.getPropertyIds()) {
            if (!hasKnownProperty(propertyId)) {
                target.add(propertyId);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void gatherUpdatesForPotentials(
            Consumer<IndexEntryUpdate> indexUpdates,
            Iterable<IndexDescriptor> potentiallyRelevant,
            boolean defaultToNoValue) {
        for (var indexKey : potentiallyRelevant) {
            SchemaDescriptor schema = indexKey.schema();
            boolean relevantBefore = relevantBefore(schema);
            boolean relevantAfter = relevantAfter(schema);
            int[] propertyIds = schema.getPropertyIds();
            if (relevantBefore && !relevantAfter) {
                indexUpdates.accept(remove(indexKey, propertyIds, defaultToNoValue));
            } else if (!relevantBefore && relevantAfter) {
                indexUpdates.accept(add(indexKey, propertyIds));
            } else if (relevantBefore && relevantAfter) {
                if (valuesChanged(propertyIds, schema.schemaPatternMatchingType(), defaultToNoValue)) {
                    indexUpdates.accept(change(indexKey, propertyIds, defaultToNoValue));
                }
            }
        }
    }

    /**
     * Matches the provided schema descriptor to the entity updates in this object, and generates an IndexEntryUpdate
     * for any token index that needs to be updated.
     *
     * @param indexKey The index key to generate entry updates for
     */
    public final Optional<IndexEntryUpdate> tokenUpdateForIndexKey(IndexDescriptor indexKey) {
        if (indexKey == null || Arrays.equals(entityTokensBefore, entityTokensAfter)) {
            return Optional.empty();
        }

        var additionsAndRemovals = PrimitiveArrays.toRemovalsAndAdditions(entityTokensBefore, entityTokensAfter);
        return Optional.of(TokenIndexEntryUpdate.tokenChange(
                entityId, indexKey, additionsAndRemovals.removals(), additionsAndRemovals.additions()));
    }

    private boolean relevantBefore(SchemaDescriptor schema) {
        return schema.isAffected(entityTokensBefore)
                && hasPropsBefore(schema.getPropertyIds(), schema.schemaPatternMatchingType());
    }

    private boolean relevantAfter(SchemaDescriptor schema) {
        return schema.isAffected(entityTokensAfter)
                && hasPropsAfter(schema.getPropertyIds(), schema.schemaPatternMatchingType());
    }

    protected final boolean atLeastOneRelevantChange(SchemaDescriptor schema) {
        boolean affectedBefore = schema.isAffected(entityTokensBefore);
        boolean affectedAfter = schema.isAffected(entityTokensAfter);
        if (affectedBefore && affectedAfter) {
            for (int propertyId : schema.getPropertyIds()) {
                if (hasKnownProperty(propertyId)) {
                    return true;
                }
            }
            return false;
        }
        return affectedBefore || affectedAfter;
    }

    private boolean hasPropsBefore(int[] propertyIds, SchemaPatternMatchingType schemaPatternMatchingType) {
        boolean found = false;
        for (int propertyId : propertyIds) {
            PropertyValueInterface propertyValue = knownProperty(propertyId, true);
            if (!propertyValue.hasBefore()) {
                if (schemaPatternMatchingType == COMPLETE_ALL_TOKENS) {
                    return false;
                }
            } else {
                found = true;
            }
        }
        return found;
    }

    private boolean hasPropsAfter(int[] propertyIds, SchemaPatternMatchingType schemaPatternMatchingType) {
        boolean found = false;
        for (int propertyId : propertyIds) {
            PropertyValueInterface propertyValue = knownProperty(propertyId, true);
            if (!propertyValue.hasAfter()) {
                if (schemaPatternMatchingType == COMPLETE_ALL_TOKENS) {
                    return false;
                }
            } else {
                found = true;
            }
        }
        return found;
    }

    /**
     * This method should only be called in a context where you know that your entity is relevant both before and after
     */
    private boolean valuesChanged(
            int[] propertyIds, SchemaPatternMatchingType schemaPatternMatchingType, boolean defaultToNoValue) {
        if (schemaPatternMatchingType == COMPLETE_ALL_TOKENS) {
            // In the case of indexes where all entries must have all indexed tokens, one of the properties must have
            // changed for us to generate a change.
            for (int propertyId : propertyIds) {
                if (knownProperty(propertyId, false).type() == PropertyValueType.Changed) {
                    return true;
                }
            }
            return false;
        } else {
            // In the case of indexes where we index incomplete index entries, we need to update as long as _anything_
            // happened to one of the indexed properties.
            for (int propertyId : propertyIds) {
                var type = knownProperty(propertyId, defaultToNoValue).type();
                if (type != PropertyValueType.UnChanged && type != PropertyValueType.NoValue) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractEntityUpdates<?> that = (AbstractEntityUpdates<?>) o;
        return entityId == that.entityId
                && Arrays.equals(entityTokensBefore, that.entityTokensBefore)
                && Arrays.equals(entityTokensAfter, that.entityTokensAfter);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(entityId);
        result = 31 * result + Arrays.hashCode(entityTokensBefore);
        result = 31 * result + Arrays.hashCode(entityTokensAfter);
        return result;
    }

    public enum PropertyValueType {
        NoValue,
        Before,
        After,
        UnChanged,
        Changed
    }

    public interface PropertyValueInterface {
        boolean hasBefore();

        boolean hasAfter();

        PropertyValueType type();
    }
}

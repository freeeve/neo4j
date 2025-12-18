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
package org.neo4j.kernel.impl.api.state;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.neo4j.collection.trackable.HeapTrackingCollections;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
import org.neo4j.collection.trackable.HeapTrackingUnifiedSet;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.txstate.TransactionStateBehaviour;
import org.neo4j.values.storable.ValueTuple;
import org.neo4j.values.storable.Values;

class IndexUpdate {
    private static final ValueTuple NO_VALUE_TUPLE = ValueTuple.of(Values.NO_VALUE);
    private final MemoryTracker memoryTracker;
    protected final HeapTrackingUnifiedSet<ValueTuple> changedValues;
    protected final HeapTrackingLongObjectHashMap<ValueTuple> removedValueEntries;
    protected final HeapTrackingLongObjectHashMap<ValueTuple> addedEntriesIds;
    private Map<ValueTuple, MutableLongSet> addedEntriesValues;
    private boolean isSorted = false;

    private IndexUpdate(MemoryTracker stateMemoryTracker) {
        this.memoryTracker = stateMemoryTracker;
        changedValues = HeapTrackingCollections.newSet(stateMemoryTracker);
        removedValueEntries = HeapTrackingCollections.newLongObjectMap(stateMemoryTracker);
        addedEntriesIds = HeapTrackingCollections.newLongObjectMap(stateMemoryTracker);
        addedEntriesValues = HeapTrackingCollections.newMap(stateMemoryTracker);
    }

    static IndexUpdate createIndexUpdate(TransactionStateBehaviour behaviour, MemoryTracker stateMemoryTracker) {
        if (behaviour.useIndexCommands()) {
            return new IndexUpdateWithIndexCommands(stateMemoryTracker);
        } else {
            return new IndexUpdate(stateMemoryTracker);
        }
    }

    void addEntry(ValueTuple values, long entityId) {
        boolean added = changedValues.add(values);
        if (!added) {
            values = changedValues.get(values);
        }
        removePrevAddedEntry(entityId);
        addedEntriesIds.put(entityId, values);
        addedEntriesValues
                .computeIfAbsent(values, v -> HeapTrackingCollections.newLongSet(memoryTracker))
                .add(entityId);
    }

    void removeEntry(ValueTuple values, long entityId) {
        removePrevAddedEntry(entityId);
        removedValueEntries.put(entityId, NO_VALUE_TUPLE);
    }

    protected void removePrevAddedEntry(long entityId) {
        ValueTuple prevValue = addedEntriesIds.remove(entityId);
        if (prevValue != null) {
            addedEntriesValues.get(prevValue).remove(entityId);
        }
    }

    Map<ValueTuple, MutableLongSet> getAddedValueEntries() {
        return addedEntriesValues;
    }

    TreeMap<ValueTuple, MutableLongSet> getSortedAddedValueEntries() {
        if (!isSorted) {
            Map<ValueTuple, MutableLongSet> sortedMap = new TreeMap<>(ValueTuple.COMPARATOR);
            sortedMap.putAll(addedEntriesValues);
            addedEntriesValues = sortedMap;
            isSorted = true;
        }
        return (TreeMap<ValueTuple, MutableLongSet>) addedEntriesValues;
    }

    HeapTrackingLongObjectHashMap<ValueTuple> getRemovedValueEntries() {
        throw new IllegalArgumentException("Must use index commands to get removed entries with values");
    }

    MutableLongSet getRemovedEntityIds() {
        return removedValueEntries.keySet();
    }

    private static class IndexUpdateWithIndexCommands extends IndexUpdate {
        IndexUpdateWithIndexCommands(MemoryTracker stateMemoryTracker) {
            super(stateMemoryTracker);
        }

        @Override
        void addEntry(ValueTuple values, long entityId) {
            var prevRemoved = removedValueEntries.get(entityId);
            if (Objects.equals(prevRemoved, values)) {
                removedValueEntries.remove(entityId);
                return;
            }
            super.addEntry(values, entityId);
        }

        @Override
        void removeEntry(ValueTuple values, long entityId) {
            assert values != null;
            var prevAdded = addedEntriesIds.get(entityId);
            if (Objects.equals(prevAdded, values)) {
                removePrevAddedEntry(entityId);
                return;
            }
            boolean added = changedValues.add(values);
            if (!added) {
                values = changedValues.get(values);
            }
            removedValueEntries.put(entityId, values);
        }

        @Override
        HeapTrackingLongObjectHashMap<ValueTuple> getRemovedValueEntries() {
            return removedValueEntries;
        }
    }
}

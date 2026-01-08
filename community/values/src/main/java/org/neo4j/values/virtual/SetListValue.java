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
package org.neo4j.values.virtual;

import static org.neo4j.memory.HeapEstimator.SCOPED_MEMORY_TRACKER_SHALLOW_SIZE;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;
import static org.neo4j.values.SequenceValue.IterationPreference.ITERATION;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.util.Iterator;
import java.util.Objects;
import org.github.jamm.Unmetered;
import org.neo4j.collection.trackable.HeapTrackingOrderedAppendSet;
import org.neo4j.collection.trackable.OrderedAppendSet;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.values.AnyValue;
import org.neo4j.values.Equality;
import org.neo4j.values.SequenceValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueRepresentation;
import org.neo4j.values.storable.Values;

/**
 * A SetListValue is specialized ListValue that can be used for `collect(DISTINCT ...)`.
 * <p>
 * Uses a LinkedHashSet<T> internally so that insertion order is preserved. Note that it is not allowed
 * to store a NO_VALUE in the set.
 */
public final class SetListValue extends ListValue {
    private static final long SET_LIST_VALUE_SHALLOW_SIZE = shallowSizeOfInstance(SetListValue.class);

    public static final class HeapTrackingBuilder implements AutoCloseable {
        private static final long SHALLOW_SIZE = shallowSizeOfInstance(HeapTrackingBuilder.class);

        @Unmetered
        private ValueRepresentation valueRepresentation;
        /*
         * Estimated heap usage in bytes of items that has been added to the
         * builder but not yet accounted for in the memory tracker.
         *
         * We have seen queries that spend a lot of time to allocate heap in the
         * memory tracker when adding lots of small items (RollupApply micro
         * benchmark). This is an optimisation for such cases.
         */
        private long unAllocatedHeapSize;

        private final HeapTrackingOrderedAppendSet<AnyValue> set;
        // We wait to track memory (bytes) below this threshold (see `unAllocatedHeapSize`).
        private static final long HEAP_SIZE_ALLOCATION_THRESHOLD = 4096;
        private final MemoryTracker scopedMemoryTracker;

        private HeapTrackingBuilder(MemoryTracker memoryTracker) {
            // To be in control of the heap usage of both the added values and the internal array list holding them,
            // we use a scoped memory tracker
            scopedMemoryTracker = memoryTracker.getScopedMemoryTracker();
            scopedMemoryTracker.allocateHeap(SHALLOW_SIZE + SCOPED_MEMORY_TRACKER_SHALLOW_SIZE);
            set = HeapTrackingOrderedAppendSet.createOrderedSet(scopedMemoryTracker);
            valueRepresentation = ValueRepresentation.ANYTHING;
        }

        public void addAll(Iterable<? extends AnyValue> values) {
            for (AnyValue value : values) {
                add(value);
            }
        }

        public void add(AnyValue value) {
            // NOTE: that since we are only using this in COLLECT(DISTINCT ..) which will filter out NO_VALUE
            //     If we lift this restriction we must also update set.contains and set.ternaryContains
            assert value != NO_VALUE;
            if (set.add(value)) {
                unAllocatedHeapSize += value.estimatedHeapUsage();
                if (unAllocatedHeapSize >= HEAP_SIZE_ALLOCATION_THRESHOLD) {
                    scopedMemoryTracker.allocateHeap(unAllocatedHeapSize);
                    unAllocatedHeapSize = 0;
                }
                valueRepresentation = valueRepresentation.coerce(value.valueRepresentation());
            }
        }

        public SetListValue build() {
            scopedMemoryTracker.allocateHeap(unAllocatedHeapSize);
            unAllocatedHeapSize = 0;
            return new SetListValue(set, payloadSize(), valueRepresentation);
        }

        public SetListValue buildAndClose() {
            var value = build();
            close();
            return value;
        }

        @Override
        public void close() {
            scopedMemoryTracker.close();
        }

        private long payloadSize() {
            // The shallow size should not be transferred to the ListValue (but the ScopedMemoryTracker is)
            // Note if the scopedMemoryTracker is an EmptyMemoryTracker then we might get a negative value here
            return Math.max(unAllocatedHeapSize + scopedMemoryTracker.estimatedHeapMemory() - SHALLOW_SIZE, 0L);
        }
    }

    public static HeapTrackingBuilder heapTrackingBuilder(MemoryTracker memoryTracker) {
        return new HeapTrackingBuilder(memoryTracker);
    }

    @Unmetered
    private final ValueRepresentation itemRepresentation;

    private final OrderedAppendSet<AnyValue> set;
    private final long payload;

    SetListValue(OrderedAppendSet<AnyValue> set, long payload, ValueRepresentation itemRepresentation) {
        this.itemRepresentation = itemRepresentation;
        this.set = set;
        this.payload = payload;
    }

    @Override
    public ValueRepresentation itemValueRepresentation() {
        return itemRepresentation;
    }

    @Override
    public long estimatedHeapUsage() {
        return SET_LIST_VALUE_SHALLOW_SIZE + payload;
    }

    @Override
    public long actualSize() {
        return set.size();
    }

    @Override
    public AnyValue value(long offset) {
        Objects.checkIndex(offset, intSize());
        return set.get((int) offset);
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public AnyValue head() {
        if (set.isEmpty()) {
            return NO_VALUE;
        }
        return set.getFirst();
    }

    @Override
    public ListValue reverse() {
        return new SetListValue(set.reversedOrderedAppendSet(), payload, itemRepresentation);
    }

    @Override
    public AnyValue last() {
        if (set.isEmpty()) {
            return NO_VALUE;
        }
        return set.getLast();
    }

    @Override
    public Value ternaryContains(AnyValue value) {
        if (value instanceof SequenceValue || value instanceof MapValue) {
            return ternaryContainsMayHaveNull(value);
        } else {
            return ternaryContainsSafe(value);
        }
    }

    private Value ternaryContainsSafe(AnyValue value) {
        return value != NO_VALUE ? Values.booleanValue(set.contains(value)) : NO_VALUE;
    }

    private Value ternaryContainsMayHaveNull(AnyValue value) {
        if (set.contains(value)) {
            // TODO: future improvement (list/map).containsNull ? NO_VALUE : TRUE
            return value.ternaryEquals(value) == Equality.TRUE ? Values.TRUE : NO_VALUE;
        } else {
            return super.ternaryContains(value);
        }
    }

    @Override
    public ListValue distinct() {
        return this;
    }

    @Override // override to skip recomputing payloadSize
    protected long compactInto(AnyValue[] array, int fromInclusive) {
        int i = fromInclusive;
        for (var x : this) {
            array[i] = x;
            i++;
        }
        return payload;
    }

    @Override
    public Iterator<AnyValue> iterator() {
        return set.iterator();
    }

    @Override
    public IterationPreference iterationPreference() {
        return ITERATION;
    }
}

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
package org.neo4j.collection.trackable;

import static org.neo4j.collection.trackable.HeapTrackingCollections.newSet;
import static org.neo4j.memory.HeapEstimator.SCOPED_MEMORY_TRACKER_SHALLOW_SIZE;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfObjectArray;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import org.neo4j.memory.MemoryTracker;

/**
 * A heap tracking, ordered, append-only, set. It only tracks the internal structure, not the elements within.
 *
 * Elements are also inserted in a single-linked list to allow traversal from first to last in the order of insertion.
 * If an already existing element is readded to the set its insertion order is NOT updated.
 *
 * @param <V> value type
 */
@SuppressWarnings("ALL")
public class HeapTrackingOrderedAppendSet<V> extends OrderedAppendSet<V> implements AutoCloseable {
    private static final long SHALLOW_SIZE = shallowSizeOfInstance(HeapTrackingOrderedAppendSet.class);
    private static final int INITIAL_CHUNK_SIZE =
            32; // Must be even, preferably a power of 2 (32 matches the HeapTrackingUnifiedMap initial size)
    private static final int MAX_CHUNK_SIZE = 8192; // Must be even, preferably a power of 2

    private final MemoryTracker scopedMemoryTracker;
    private final HeapTrackingUnifiedSet<V> set;

    // Linked chunk list used to store key-value pairs in order
    private final Chunk<V> first;
    private Chunk<V> current;

    public static <V> HeapTrackingOrderedAppendSet<V> createOrderedSet(MemoryTracker memoryTracker) {
        MemoryTracker scopedMemoryTracker = memoryTracker.getScopedMemoryTracker();
        scopedMemoryTracker.allocateHeap(SHALLOW_SIZE + SCOPED_MEMORY_TRACKER_SHALLOW_SIZE);
        return new HeapTrackingOrderedAppendSet<>(scopedMemoryTracker);
    }

    private HeapTrackingOrderedAppendSet(MemoryTracker scopedMemoryTracker) {
        this.scopedMemoryTracker = scopedMemoryTracker;
        this.set = newSet(scopedMemoryTracker);
        first = new Chunk<V>(INITIAL_CHUNK_SIZE, scopedMemoryTracker);
        current = first;
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean add(V value) {
        if (set.add(value)) {
            addToBuffer(value);
            return true;
        }

        return false;
    }

    public boolean addWithMemoryTracker(V value, Consumer<MemoryTracker> consumer) {
        if (set.add(value)) {
            var tracker = scopedMemoryTracker;
            consumer.accept(tracker);
            addToBuffer(value);
            return true;
        }

        return false;
    }

    @Override
    public boolean contains(Object value) {
        return set.contains(value);
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public V getFirst() {
        if (set.isEmpty()) {
            throw new NoSuchElementException();
        }
        return first.getFirst();
    }

    @Override
    public V getLast() {
        if (set.isEmpty()) {
            throw new NoSuchElementException();
        }
        return (V) current.getLast();
    }

    @Override
    public V get(int index) {
        var chunk = first;
        while (chunk != null) {
            if (index < chunk.cursor) {
                return chunk.get(index);
            }
            index -= chunk.cursor;
            chunk = chunk.next;
        }

        throw new IndexOutOfBoundsException();
    }

    @Override
    public Iterator<V> iterator() {
        return new ApendSetIterator(first);
    }

    @Override
    public void close() {
        current = null;
        scopedMemoryTracker.close();
    }

    public boolean isClosed() {
        return current == null;
    }

    private void addToBuffer(V value) {
        if (!current.add(value)) {
            int newChunkSize = grow(current.elements.length);
            Chunk<V> newChunk = new Chunk<>(newChunkSize, scopedMemoryTracker);
            current.next = newChunk;
            current = newChunk;
            current.add(value);
        }
    }

    @Override
    public OrderedAppendSet<V> reversedOrderedAppendSet() {
        class ReversedView extends OrderedAppendSet<V> {

            @Override
            public int size() {
                return HeapTrackingOrderedAppendSet.this.size();
            }

            @Override
            public boolean contains(Object value) {
                return HeapTrackingOrderedAppendSet.this.contains(value);
            }

            @Override
            public boolean isEmpty() {
                return HeapTrackingOrderedAppendSet.this.isEmpty();
            }

            @Override
            public V getFirst() {
                return HeapTrackingOrderedAppendSet.this.getLast();
            }

            @Override
            public V getLast() {
                return HeapTrackingOrderedAppendSet.this.getFirst();
            }

            @Override
            public V get(int index) {
                return HeapTrackingOrderedAppendSet.this.get(size() - index - 1);
            }

            @Override
            public Iterator<V> iterator() {
                return new ApendSetReverseIterator<>(
                        HeapTrackingOrderedAppendSet.this.first, HeapTrackingOrderedAppendSet.this.current);
            }

            @Override
            public OrderedAppendSet<V> reversedOrderedAppendSet() {
                return HeapTrackingOrderedAppendSet.this;
            }
        }

        return new ReversedView();
    }

    private static class ApendSetIterator<V> implements Iterator<V> {
        private Chunk<V> chunk;
        private Chunk<V> nextChunk;
        private int nextIndex;

        ApendSetIterator(Chunk<V> first) {
            chunk = nextChunk = first;
        }

        @Override
        public boolean hasNext() {
            if (nextChunk == null || nextIndex >= nextChunk.cursor) {
                return false;
            }
            return true;
        }

        @Override
        public V next() {
            if (nextChunk == null) {
                throw new NoSuchElementException();
            }

            // Set current entry
            int index = nextIndex;
            chunk = nextChunk;

            // Advance next entry
            nextIndex += 1;
            if (nextIndex >= nextChunk.cursor) {
                nextChunk = nextChunk.next;
                nextIndex = 0;
            }

            return (V) chunk.elements[index];
        }
    }

    private static class ApendSetReverseIterator<V> implements Iterator<V> {
        private final Chunk<V> firstChunk;
        private Chunk<V> currentChunk;
        private Chunk<V> nextChunk;
        private int nextIndex;

        ApendSetReverseIterator(Chunk<V> first, Chunk<V> last) {
            firstChunk = first;
            currentChunk = nextChunk = last;
            nextIndex = currentChunk.cursor - 1;
        }

        @Override
        public boolean hasNext() {
            if (nextChunk == null || nextIndex < 0) {
                return false;
            }
            return true;
        }

        private Chunk<V> findNextChunk() {
            var chunk = firstChunk;
            var current = currentChunk;
            while (chunk != current) {
                var next = chunk.next;
                if (next == current) {
                    return chunk;
                }
                chunk = next;
            }

            return null;
        }

        @Override
        public V next() {
            if (nextChunk == null) {
                throw new NoSuchElementException();
            }

            // Set current entry
            int index = nextIndex;
            currentChunk = nextChunk;

            // Advance next entry
            nextIndex -= 1;
            if (nextIndex < 0) {
                nextChunk = findNextChunk();
                nextIndex = nextChunk == null ? -1 : nextChunk.cursor - 1;
            }

            return (V) currentChunk.elements[index];
        }
    }

    private static int grow(int size) {
        if (size == MAX_CHUNK_SIZE) {
            return size;
        }
        int newSize = size << 1;
        if (newSize <= 0 || newSize > MAX_CHUNK_SIZE) // Check overflow
        {
            return MAX_CHUNK_SIZE;
        }
        return newSize;
    }

    private static final class Chunk<V> {
        private static final long SHALLOW_SIZE = shallowSizeOfInstance(Chunk.class);

        private final Object[] elements;
        private Chunk<V> next;
        private int cursor;

        Chunk(int size, MemoryTracker memoryTracker) {
            memoryTracker.allocateHeap(SHALLOW_SIZE + shallowSizeOfObjectArray(size));
            elements = new Object[size];
        }

        boolean add(V value) {
            if (cursor < elements.length) {
                elements[cursor] = value;
                cursor += 1;
                return true;
            }
            return false;
        }

        V get(int index) {
            return (V) elements[index];
        }

        V getFirst() {
            return (V) elements[0];
        }

        V getLast() {
            int i = Math.min(cursor, elements.length) - 1;
            if (i < 0) {
                throw new NoSuchElementException();
            }
            return (V) elements[i];
        }
    }
}

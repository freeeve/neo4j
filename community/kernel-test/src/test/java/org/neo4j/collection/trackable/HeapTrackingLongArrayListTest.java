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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.collections.api.LongIterable;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.collection.PrimitiveLongResourceIterator;
import org.neo4j.memory.LocalMemoryTracker;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;

@RandomSupportExtension
class HeapTrackingLongArrayListTest {
    private final MemoryTracker memoryTracker = new LocalMemoryTracker();
    private HeapTrackingLongArrayList aList;
    private long[] longArray;

    @Inject
    private RandomSupport random;

    @BeforeEach
    void setUp() {
        longArray = new long[100];
        for (int i = 0; i < longArray.length; i++) {
            longArray[i] = i;
        }
        aList = HeapTrackingLongArrayList.newLongArrayList(memoryTracker);
        aList.addAll(longArray);
    }

    @AfterEach
    void tearDown() {
        longArray = null;
        aList.close();
        assertEquals(0, memoryTracker.estimatedHeapMemory(), "Leaking memory");
    }

    @Test
    void initialSize() {
        try (HeapTrackingLongArrayList list = HeapTrackingLongArrayList.newLongArrayList(5, memoryTracker)) {
            assertEquals(0, list.size(), "Should not contain any elements when created");
        }

        assertThrows(
                IllegalArgumentException.class, () -> HeapTrackingLongArrayList.newLongArrayList(-10, memoryTracker));
    }

    @Test
    void addObjectAtIndex() {
        long l;
        aList.add(50, l = random.nextLong());
        assertEquals(aList.get(50), l, "Failed to add Object");
        assertTrue(
                aList.get(51) == longArray[50] && (aList.get(52) == longArray[51]),
                "Failed to fix up list after insert");
        Object oldItem = aList.get(25);
        aList.add(25, -1L);
        assertEquals(aList.get(25), -1L, "Should have returned null");
        assertSame(aList.get(26), oldItem, "Should have returned the old item from slot 25");
        assertThrows(IndexOutOfBoundsException.class, () -> aList.add(-1, -1L));
        assertThrows(IndexOutOfBoundsException.class, () -> aList.add(aList.size() + 1, -1L));
    }

    @Test
    void addObjectLast() {
        long l = random.nextLong();
        aList.add(l);
        assertEquals(aList.get(aList.size() - 1), l, "Failed to add long");
    }

    @Test
    void clear() {
        aList.clear();
        assertEquals(0, aList.size(), "List did not clear");
        aList.add(random.nextLong());
        aList.add(random.nextLong());
        aList.add(random.nextLong());
        aList.add(random.nextLong());
        aList.clear();
        assertEquals(0, aList.size(), "List with nulls did not clear");
    }

    @Test
    void get() {
        assertSame(aList.get(22), longArray[22], "Returned incorrect element");
        assertThrows(IndexOutOfBoundsException.class, () -> aList.get(8765));
    }

    @Test
    void isEmpty() {
        try (HeapTrackingLongArrayList list = HeapTrackingLongArrayList.newLongArrayList(memoryTracker)) {
            assertTrue(list.isEmpty(), "isEmpty returned false for new list");
        }
        assertFalse(aList.isEmpty(), "Returned true for existing list with elements");
    }

    @Test
    void setElement() {
        long l;
        aList.set(65, l = random.nextLong());
        assertEquals(aList.get(65), l, "Failed to set object");
        assertEquals(100, aList.size(), "Setting increased the list's size to: " + aList.size());
        assertThrows(IndexOutOfBoundsException.class, () -> aList.set(-1, random.nextLong()));
        assertThrows(IndexOutOfBoundsException.class, () -> aList.set(aList.size() + 1, random.nextLong()));
    }

    @Test
    void size() {
        assertEquals(100, aList.size(), "Returned incorrect size for exiting list");
        try (HeapTrackingLongArrayList list = HeapTrackingLongArrayList.newLongArrayList(memoryTracker)) {
            assertEquals(0, list.size(), "Returned incorrect size for new list");
        }
    }

    @Test
    void iterator() {
        PrimitiveLongResourceIterator iterator = aList.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            assertTrue(i < longArray.length);
            assertEquals(longArray[i++], iterator.next());
        }
        assertEquals(i, longArray.length);
    }

    @Test
    void equalsAndHashCode() {
        try (var a = HeapTrackingLongArrayList.newLongArrayList(memoryTracker);
                var b = HeapTrackingLongArrayList.newLongArrayList(memoryTracker)) {
            // Empty lists are equal
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());

            a.addAll(1L, 2L, 3L);
            b.addAll(1L, 2L, 3L);
            // Same elements, potentially different backing-array capacities
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());

            b.add(4L);
            assertFalse(a.equals(b));
        }
    }

    @Test
    void sortThis() {
        try (HeapTrackingLongArrayList list = HeapTrackingLongArrayList.newLongArrayList(memoryTracker)) {
            // Empty list is a no-op and returns this
            assertSame(list, list.sortThis());

            // Unsorted list is sorted in place
            list.addAll(5L, 1L, 3L, 2L);
            list.sortThis();
            assertEquals(1L, list.get(0));
            assertEquals(2L, list.get(1));
            assertEquals(3L, list.get(2));
            assertEquals(5L, list.get(3));

            // Already-sorted list is unchanged
            list.sortThis();
            assertEquals(1L, list.get(0));
            assertEquals(2L, list.get(1));
            assertEquals(3L, list.get(2));
            assertEquals(5L, list.get(3));
        }
    }

    @Test
    void toArray() {
        try (HeapTrackingLongArrayList list = HeapTrackingLongArrayList.newLongArrayList(memoryTracker)) {
            list.addAll(3L, 1L, 2L);
            long[] arr = list.toArray();
            assertThat(arr).isEqualTo(new long[] {3L, 1L, 2L});

            // Returned array is a copy, not an alias of internal state
            arr[0] = 99L;
            assertEquals(3L, list.get(0));
        }
    }

    @Test
    void asLongIterable() {
        try (HeapTrackingLongArrayList trackedList = HeapTrackingLongArrayList.newLongArrayList(memoryTracker)) {
            // Given
            LongIterable list = trackedList;
            // Then
            assertThat(list.toArray()).isEmpty();
            assertThat(list.size()).isZero();
            assertThat(list.makeString()).isEmpty();

            // When
            trackedList.addAll(3, 2, 1);
            // Then
            assertThat(list.toArray()).isEqualTo(new long[] {3, 2, 1});
            assertThat(list.size()).isEqualTo(3);
            assertThat(list.contains(2)).isTrue();
            assertThat(list.contains(4)).isFalse();
            assertThat(list.min()).isEqualTo(1);
            assertThat(list.max()).isEqualTo(3);
            assertThat(list.sum()).isEqualTo(6);
            MutableLongList otherList = LongLists.mutable.empty();
            list.forEach(otherList::add);
            assertThat(otherList.toArray()).isEqualTo(new long[] {3, 2, 1});
            assertThat(list.makeString()).isEqualTo("3, 2, 1");
        }
    }
}

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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.memory.EmptyMemoryTracker;

public class HeapTrackingOrderedAppendSetTest {

    @Test
    void emptySet() {
        HeapTrackingOrderedAppendSet<Integer> appendSet = create();
        assertThat(appendSet.isEmpty()).isTrue();
        assertThat(appendSet.contains(1337)).isFalse();
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(appendSet::getFirst);
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(appendSet::getLast);
        assertThat(appendSet.iterator()).isExhausted();
    }

    @Test
    void singleElementSet() {
        HeapTrackingOrderedAppendSet<Integer> appendSet = create(1337);
        assertThat(appendSet.isEmpty()).isFalse();
        assertThat(appendSet.contains(1337)).isTrue();
        assertThat(appendSet.contains(1338)).isFalse();
        assertThat(appendSet.getFirst()).isEqualTo(1337);
        assertThat(appendSet.getLast()).isEqualTo(1337);
        assertThat(Iterators.asList(appendSet.iterator())).isEqualTo(List.of(1337));
    }

    @Test
    void addAndRetrieveNoDuplicates() {
        assertSetContains(create(100, 9, 0, 7), 100, 9, 0, 7);
    }

    @Test
    void addAndRetrieveWithDuplicates() {
        assertSetContains(create(100, 100, 9, 0, 9, 7, 0), 100, 9, 0, 7);
    }

    @Test
    void randomTest() {
        long seed = System.currentTimeMillis();
        Random random = new Random(seed);
        int size = 10000;
        HeapTrackingOrderedAppendSet<Integer> appendSet = create();
        LinkedHashSet<Integer> linkedSet = new LinkedHashSet<>();
        for (int i = 0; i < size; i++) {
            int next = random.nextInt(1000);
            appendSet.add(next);
            linkedSet.add(next);
        }

        assertThat(Iterators.asList(appendSet.iterator()))
                .as(String.format("LinkedHashSet and HeapTrackingOrderedAppendSet disagreed using seed=%d", seed))
                .isEqualTo(Iterators.asList(linkedSet.iterator()));
    }

    @SafeVarargs
    private <T> void assertSetContains(HeapTrackingOrderedAppendSet<T> set, T... objects) {
        assertThat(set.size()).isEqualTo(objects.length);
        assertThat(set.isEmpty()).isEqualTo(objects.length == 0);
        assertThat(set.getFirst()).isEqualTo(objects[0]);
        assertThat(set.getLast()).isEqualTo(objects[objects.length - 1]);
        var iterator = set.iterator();
        for (T object : objects) {
            assertThat(iterator).hasNext();
            assertThat(iterator.next()).isEqualTo(object);
        }
        assertThat(iterator).isExhausted();
        for (T object : objects) {
            assertThat(set.contains(object)).isTrue();
        }
    }

    @SafeVarargs
    private <T> HeapTrackingOrderedAppendSet<T> create(T... objects) {
        var orderedSet = HeapTrackingOrderedAppendSet.<T>createOrderedSet(EmptyMemoryTracker.INSTANCE);
        for (T anObject : objects) {
            if (orderedSet.contains(anObject)) {
                assertThat(orderedSet.add(anObject)).isFalse();
            } else {
                assertThat(orderedSet.add(anObject)).isTrue();
            }
        }

        return orderedSet;
    }
}

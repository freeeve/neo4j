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
package org.neo4j.kernel.impl.util.diffsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.neo4j.internal.helpers.collection.Iterators.asSet;

import java.util.HashSet;
import java.util.Set;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.junit.jupiter.api.Test;
import org.neo4j.collection.diffset.MutableIntDiffSetsImpl;
import org.neo4j.collection.factory.CollectionsFactory;
import org.neo4j.collection.factory.OnHeapCollectionsFactory;
import org.neo4j.memory.EmptyMemoryTracker;

class MutableIntDiffSetsImplTest {

    @Test
    void newDiffSetIsEmpty() {
        assertTrue(createDiffSet().isEmpty());
    }

    @Test
    void addElementsToDiffSets() {
        MutableIntDiffSetsImpl diffSets = createDiffSet();

        diffSets.add(1);
        diffSets.add(2);

        assertEquals(asSet(1, 2), toSet(diffSets.getAdded()));
        assertTrue(diffSets.getRemoved().isEmpty());
        assertFalse(diffSets.isEmpty());
    }

    @Test
    void removeElementsInDiffSets() {
        MutableIntDiffSetsImpl diffSets = createDiffSet();

        diffSets.remove(1);
        diffSets.remove(2);

        assertFalse(diffSets.isEmpty());
        assertEquals(asSet(1, 2), toSet(diffSets.getRemoved()));
    }

    @Test
    void removeAndAddElementsToDiffSets() {
        MutableIntDiffSetsImpl diffSets = createDiffSet();

        diffSets.remove(1);
        diffSets.remove(2);
        diffSets.add(1);
        diffSets.add(2);
        diffSets.add(3);
        diffSets.remove(4);

        assertFalse(diffSets.isEmpty());
        assertEquals(asSet(4), toSet(diffSets.getRemoved()));
        assertEquals(asSet(3), toSet(diffSets.getAdded()));
    }

    @Test
    void checkIsElementsAddedOrRemoved() {
        MutableIntDiffSetsImpl diffSet = createDiffSet();

        diffSet.add(1);

        assertTrue(diffSet.isAdded(1));
        assertFalse(diffSet.isRemoved(1));

        diffSet.remove(2);

        assertFalse(diffSet.isAdded(2));
        assertTrue(diffSet.isRemoved(2));

        assertFalse(diffSet.isAdded(3));
        assertFalse(diffSet.isRemoved(3));
    }

    @Test
    void addedAndRemovedElementsDelta() {
        MutableIntDiffSetsImpl diffSet = createDiffSet();
        assertEquals(0, diffSet.delta());

        diffSet.add(7);
        diffSet.add(8);
        diffSet.add(9);
        diffSet.add(10);
        assertEquals(4, diffSet.delta());

        diffSet.remove(8);
        diffSet.remove(9);
        assertEquals(2, diffSet.delta());
    }

    @Test
    void useCollectionsFactory() {
        final MutableIntSet set1 = new IntHashSet();
        final MutableIntSet set2 = new IntHashSet();
        final CollectionsFactory collectionsFactory = mock(CollectionsFactory.class);
        doReturn(set1, set2).when(collectionsFactory).newIntSet(EmptyMemoryTracker.INSTANCE);

        final MutableIntDiffSetsImpl diffSets =
                new MutableIntDiffSetsImpl(collectionsFactory, EmptyMemoryTracker.INSTANCE);
        diffSets.add(1);
        diffSets.remove(2);

        assertSame(set1, diffSets.getAdded());
        assertSame(set2, diffSets.getRemoved());
        verify(collectionsFactory, times(2)).newIntSet(EmptyMemoryTracker.INSTANCE);
        verifyNoMoreInteractions(collectionsFactory);
    }

    private static MutableIntDiffSetsImpl createDiffSet() {
        return new MutableIntDiffSetsImpl(OnHeapCollectionsFactory.INSTANCE, EmptyMemoryTracker.INSTANCE);
    }

    private static Set<Integer> toSet(IntSet set) {
        return toSet(set.intIterator());
    }

    private static Set<Integer> toSet(IntIterator iterator) {
        Set<Integer> set = new HashSet<>();
        while (iterator.hasNext()) {
            set.add(iterator.next());
        }
        return set;
    }
}

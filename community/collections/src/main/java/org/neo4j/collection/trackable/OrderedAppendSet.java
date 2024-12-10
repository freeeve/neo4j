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

import java.util.AbstractSet;
import java.util.SequencedSet;

/**
 * An OrderedAppendSet is an alternative to a LinkedHashSet, as it preserves insertion order while still having constant
 * time lookup.
 * @param <V> – the type of elements in this set
 */
public abstract class OrderedAppendSet<V> extends AbstractSet<V> implements SequencedSet<V> {

    /**
     * OrderedAppendSet support random access, but BE AWARE that this is not a constant time operation.
     * @param index the index of the element
     * @return the element at the given index
     */
    public abstract V get(int index);

    /**
     *  Returns a reverse-ordered view of this collection.
     *  The encounter order of elements in the returned view is the inverse of the encounter
     *  order of elements in this collection. The reverse ordering affects all order-sensitive
     *  operations, including those on the view collections of the returned view. The view does not
     *  support writing to the underlying set.
     * @return a reverse-ordered view of this collection, as a {@code OrderedAppendSet}
     */
    public abstract OrderedAppendSet<V> reversedOrderedAppendSet();

    @Override
    public SequencedSet<V> reversed() {
        return reversedOrderedAppendSet();
    }
}

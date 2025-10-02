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
package org.neo4j.collection;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

/// A set-like collection that replaces the value with the new value on a hash collision, in contrast to a
/// [java.util.HashSet], which will ignore the new value. This distinction is important for objects where
/// [Object#hashCode] and [Object#equals] do not take into consideration some other field which is to be modified.
/// Semantically this is not a [java.util.Set] due to breaking [java.util.Set#add]'s contracts.
/// Like [java.util.HashSet], this leverages a [HashMap] as a backing container, and ensures a single lookup.
/// Note: The backing [HashMap] key will retain a reference to the _first_ occurrence of the element, whereas the
/// value will retain the _latest_.
public class RetainLatestUniqueValueCollection<E> extends AbstractCollection<E> {
    private final HashMap<E, E> map;

    public RetainLatestUniqueValueCollection() {
        this(new HashMap<>());
    }

    public RetainLatestUniqueValueCollection(Collection<? extends E> collection) {
        this(HashMap.newHashMap(Math.max(collection.size(), 12)));
        addAll(collection);
    }

    public RetainLatestUniqueValueCollection(int initialCapacity, float loadFactor) {
        this(new HashMap<>(initialCapacity, loadFactor));
    }

    public RetainLatestUniqueValueCollection(int initialCapacity) {
        this(new HashMap<>(initialCapacity));
    }

    private RetainLatestUniqueValueCollection(HashMap<E, E> map) {
        assert map != null && map.isEmpty();
        this.map = map;
    }

    public static <E> RetainLatestUniqueValueCollection<E> newRetainLatestUniqueValueCollection(int numberOfElements) {
        return new RetainLatestUniqueValueCollection<>(HashMap.newHashMap(numberOfElements));
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /// @throws NullPointerException if the specified object is null.
    @Override
    public boolean contains(Object object) {
        return map.containsKey(Objects.requireNonNull(object));
    }

    @Override
    public Iterator<E> iterator() {
        return map.values().iterator();
    }

    @Override
    public Object[] toArray() {
        return map.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
        return map.values().toArray(array);
    }

    /// Adds the specified element into the collection retaining the latest unique value.
    /// Specifically, if an equal value already exists in the collection, it is replaced with the specified element.
    /// @return `true` (as specified in [Collection#add]).
    /// @throws NullPointerException if the specified element is null.
    @Override
    public boolean add(E element) {
        map.put(Objects.requireNonNull(element), element);
        return true;
    }

    /// @throws NullPointerException if the specified element is null.
    @Override
    public boolean remove(Object object) {
        return map.remove(Objects.requireNonNull(object)) != null;
    }

    @Override
    public void clear() {
        map.clear();
    }
}

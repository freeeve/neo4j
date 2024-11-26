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
package org.neo4j.internal.batchimport.cache;

/**
 * Abstraction over primitive arrays.
 *
 * The arrays provide the same concurrency guarantees as regular arrays. I.e. updating different parts of the array
 * from different threads are fine, but for sharing, some external synchronization will be needed.
 *
 * @see NumberArrayFactory
 */
public interface NumberArray extends MemoryStatsVisitor.Visitable, AutoCloseable {
    /**
     * @return length of the array, i.e. the capacity.
     */
    long length();

    /**
     * Swaps items from {@code fromIndex} to {@code toIndex}, such that
     * {@code fromIndex} and {@code toIndex}, {@code fromIndex+1} and {@code toIndex} a.s.o swaps places.
     * The number of items swapped is equal to the length of the default value of the array.
     *  @param fromIndex where to start swapping from.
     * @param toIndex where to start swapping to.
     */
    void swap(long fromIndex, long toIndex);

    /**
     * Sets all values to a default value.
     */
    void clear();

    /**
     * Releases any resources that GC won't release automatically.
     */
    @Override
    void close();
}

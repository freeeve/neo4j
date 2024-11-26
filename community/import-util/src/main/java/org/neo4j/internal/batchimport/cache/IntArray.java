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
 * Abstraction of a {@code int[]} so that different implementations can be plugged in, for example
 * off-heap, dynamically growing, or other implementations.
 *
 * @see NumberArrayFactory
 */
public interface IntArray extends NumberArray {
    int get(long index);

    void set(long index, int value);

    /**
     * Atomically compare and set a value with volatile memory semantic.
     *
     * @param index The index in the array.
     * @param expected expected value to compare with.
     * @param value new value to set if {@code expected} matches.
     * @return {@code true} if CAS was successful, {@code false} otherwise.
     */
    boolean compareAndSet(long index, int expected, int value);

    /**
     * Atomically compare and exchange a value with volatile memory semantic.
     *
     * @param index The index in the array.
     * @param expected expected value to compare with.
     * @param value new value to set if {@code expected} matches.
     * @return the witness value.
     */
    int compareAndExchange(long index, int expected, int value);

    @Override
    default void swap(long fromIndex, long toIndex) {
        int intermediary = get(fromIndex);
        set(fromIndex, get(toIndex));
        set(toIndex, intermediary);
    }

    IntArray EMPTY_ARRAY = new IntArray() {
        @Override
        public void acceptMemoryStatsVisitor(MemoryStatsVisitor visitor) {}

        @Override
        public long length() {
            return 0;
        }

        @Override
        public void clear() {}

        @Override
        public void close() {}

        @Override
        public int get(long index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public void set(long index, int value) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public boolean compareAndSet(long index, int expectedValue, int updatedValue) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int compareAndExchange(long index, int expected, int value) {
            throw new IndexOutOfBoundsException(index);
        }
    };
}

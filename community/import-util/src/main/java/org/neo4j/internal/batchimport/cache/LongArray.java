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
 * Abstraction of a {@code long[]} so that different implementations can be plugged in, for example
 * off-heap, dynamically growing, or other implementations.
 *
 * @see NumberArrayFactory
 */
public interface LongArray extends NumberArray {
    long get(long index);

    void set(long index, long value);

    /**
     * Atomically compare and set a value with volatile memory semantic.
     *
     * @param index The index in the array.
     * @param expected expected value to compare with.
     * @param value new value to set if {@code expected} matches.
     * @return {@code true} if CAS was successful, {@code false} otherwise.
     */
    boolean compareAndSet(long index, long expected, long value);

    /**
     * Atomically compare and exchange a value with volatile memory semantic.
     *
     * @param index The index in the array.
     * @param expected expected value to compare with.
     * @param value new value to set if {@code expected} matches.
     * @return the witness value.
     */
    long compareAndExchange(long index, long expected, long value);

    void getAndAdd(long index, long delta);

    @Override
    default void swap(long fromIndex, long toIndex) {
        long intermediary = get(fromIndex);
        set(fromIndex, get(toIndex));
        set(toIndex, intermediary);
    }

    LongArray EMPTY_ARRAY = new LongArray() {
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
        public long get(long index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public void set(long index, long value) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public boolean compareAndSet(long index, long expected, long value) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public long compareAndExchange(long index, long expected, long value) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public void getAndAdd(long index, long delta) {
            throw new IndexOutOfBoundsException(index);
        }
    };
}

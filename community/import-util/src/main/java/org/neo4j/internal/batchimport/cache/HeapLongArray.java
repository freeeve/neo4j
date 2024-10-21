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

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import org.neo4j.internal.helpers.VarHandleUtils;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.memory.MemoryTracker;

/**
 * A {@code long[]} on heap, abstracted into a {@link LongArray}.
 */
public class HeapLongArray extends HeapNumberArray<LongArray> implements LongArray {
    private static final VarHandle VH_ARRAY = VarHandleUtils.arrayElementVarHandle(long[].class);
    private final long[] array;
    private final long defaultValue;

    public HeapLongArray(int length, long defaultValue, long base, MemoryTracker memoryTracker) {
        super(Long.BYTES, base);
        this.defaultValue = defaultValue;
        this.array = new long[length];
        memoryTracker.allocateHeap(HeapEstimator.sizeOf(array));
        clear();
    }

    @Override
    public long length() {
        return array.length;
    }

    @Override
    public long get(long index) {
        return (long) VH_ARRAY.get(array, index(index));
    }

    @Override
    public void set(long index, long value) {
        VH_ARRAY.set(array, index(index), value);
    }

    @Override
    public boolean compareAndSet(long index, long expected, long value) {
        return VH_ARRAY.compareAndSet(array, index(index), expected, value);
    }

    @Override
    public long compareAndExchange(long index, long expected, long value) {
        return (long) VH_ARRAY.compareAndExchange(array, index(index), expected, value);
    }

    @Override
    public void clear() {
        Arrays.fill(array, defaultValue);
    }
}

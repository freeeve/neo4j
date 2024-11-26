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

import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.lang.invoke.VarHandle;
import org.neo4j.memory.MemoryTracker;

class LongArrayImpl extends BaseDynamicArray implements LongArray {
    private static final VarHandle VH_LONG_BYTE_BUFFER = byteBufferViewVarHandle(long[].class, LITTLE_ENDIAN);

    LongArrayImpl(
            long maxNumberOfElements,
            int elementsPerChunk,
            byte defaultValue,
            BufferFactory bufferFactory,
            MemoryTracker memoryTracker) {
        super(maxNumberOfElements, Long.BYTES, elementsPerChunk, defaultValue, bufferFactory, memoryTracker);
    }

    @Override
    public long get(long index) {
        return (long) VH_LONG_BYTE_BUFFER.get(getBuffer(index), offset(index));
    }

    @Override
    public void set(long index, long value) {
        VH_LONG_BYTE_BUFFER.set(getBuffer(index), offset(index), value);
    }

    @Override
    public boolean compareAndSet(long index, long expected, long value) {
        return VH_LONG_BYTE_BUFFER.compareAndSet(getBuffer(index), offset(index), expected, value);
    }

    @Override
    public long compareAndExchange(long index, long expected, long value) {
        return (long) VH_LONG_BYTE_BUFFER.compareAndExchange(getBuffer(index), offset(index), expected, value);
    }

    @Override
    public void getAndAdd(long index, long delta) {
        VH_LONG_BYTE_BUFFER.getAndAdd(getBuffer(index), offset(index), delta);
    }

    @Override
    protected int offset(long index) {
        return (int) (index & bufferMask) << 3; // Avoid doing multiplication
    }
}

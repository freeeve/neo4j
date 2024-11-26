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

class IntArrayImpl extends BaseDynamicArray implements IntArray {
    private static final VarHandle VH_INTEGER_BYTE_BUFFER = byteBufferViewVarHandle(int[].class, LITTLE_ENDIAN);

    IntArrayImpl(
            long maxNumberOfElements,
            int elementsPerChunk,
            byte defaultValue,
            BufferFactory bufferFactory,
            MemoryTracker memoryTracker) {
        super(maxNumberOfElements, Integer.BYTES, elementsPerChunk, defaultValue, bufferFactory, memoryTracker);
    }

    @Override
    public int get(long index) {
        return (int) VH_INTEGER_BYTE_BUFFER.get(getBuffer(index), offset(index));
    }

    @Override
    public void set(long index, int value) {
        VH_INTEGER_BYTE_BUFFER.set(getBuffer(index), offset(index), value);
    }

    @Override
    public boolean compareAndSet(long index, int expected, int value) {
        return VH_INTEGER_BYTE_BUFFER.compareAndSet(getBuffer(index), offset(index), expected, value);
    }

    @Override
    public int compareAndExchange(long index, int expected, int value) {
        return (int) VH_INTEGER_BYTE_BUFFER.compareAndExchange(getBuffer(index), offset(index), expected, value);
    }

    @Override
    protected int offset(long index) {
        return (int) (index & bufferMask) << 2; // Avoid doing multiplication
    }
}

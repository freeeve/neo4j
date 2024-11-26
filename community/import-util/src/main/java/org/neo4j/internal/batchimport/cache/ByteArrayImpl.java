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

import static java.lang.Math.min;

import java.nio.ByteBuffer;
import org.neo4j.memory.MemoryTracker;

class ByteArrayImpl extends BaseDynamicArray implements ByteArray {

    ByteArrayImpl(
            long maxNumberOfElements,
            int elementSize,
            int elementsPerChunk,
            byte defaultValue,
            BufferFactory bufferFactory,
            MemoryTracker memoryTracker) {
        super(maxNumberOfElements, elementSize, elementsPerChunk, defaultValue, bufferFactory, memoryTracker);
    }

    @Override
    public void get(long index, byte[] into) {
        int length = into.length;
        int offset = offset(index);
        int intoOffset = 0;
        while (intoOffset < length) {
            ByteBuffer buffer = getBuffer(index + intoOffset);
            int batch = min(length - intoOffset, buffer.capacity() - offset);
            buffer.get(offset, into, intoOffset, batch);
            intoOffset += batch;
            offset = 0;
        }
    }

    @Override
    public void getElement(long index, byte[] into) {
        assert into.length == elementSize : "Destination must match elementSize";
        getBuffer(index).get(offset(index), into, 0, into.length);
    }

    @Override
    public byte getByte(long index, int additionalOffset) {
        return getBuffer(index).get(offset(index) + additionalOffset);
    }

    @Override
    public short getShort(long index, int additionalOffset) {
        return getBuffer(index).getShort(offset(index) + additionalOffset);
    }

    @Override
    public int get3ByteInt(long index, int additionalOffset) {
        ByteBuffer b = getBuffer(index);
        int o = offset(index) + additionalOffset;

        int lsb = b.getShort(o) & 0xFFFF;
        int msb = (int) b.get(o + Short.BYTES) & 0xFF;
        return signExtension(lsb | (msb << Short.SIZE), 0xFF800000);
    }

    @Override
    public int getInt(long index, int additionalOffset) {
        return getBuffer(index).getInt(offset(index) + additionalOffset);
    }

    @Override
    public long get5ByteLong(long index, int additionalOffset) {
        ByteBuffer b = getBuffer(index);
        int o = offset(index) + additionalOffset;

        long lsb = b.getInt(o) & 0xFFFFFFFFL;
        long msb = b.get(o + Integer.BYTES) & 0xFFL;
        return signExtension(lsb | msb << Integer.SIZE, 0xFFFFFF8000000000L);
    }

    @Override
    public long get6ByteLong(long index, int additionalOffset) {
        ByteBuffer b = getBuffer(index);
        int o = offset(index) + additionalOffset;
        long lsb = b.getInt(o) & 0xFFFFFFFFL;
        long msb = b.getShort(o + Integer.BYTES) & 0xFFFFL;
        return signExtension(lsb | msb << Integer.SIZE, 0xFFFF800000000000L);
    }

    @Override
    public long getLong(long index, int additionalOffset) {
        return getBuffer(index).getLong(offset(index) + additionalOffset);
    }

    @Override
    public void set(long index, byte[] value) {
        int length = value.length;
        int offset = offset(index);
        int sourceIndex = 0;
        while (sourceIndex < length) {
            ByteBuffer buffer = getBuffer(index + sourceIndex);
            int batch = min(length - sourceIndex, buffer.capacity() - offset);
            buffer.put(offset, value, sourceIndex, batch);
            sourceIndex += batch;
            offset = 0;
        }
    }

    @Override
    public void setElement(long index, byte[] value) {
        assert value.length == elementSize : "Value must match elementSize";
        getBuffer(index).put(offset(index), value);
    }

    @Override
    public void setByte(long index, int additionalOffset, byte value) {
        getBuffer(index).put(offset(index) + additionalOffset, value);
    }

    @Override
    public void setShort(long index, int additionalOffset, short value) {
        getBuffer(index).putShort(offset(index) + additionalOffset, value);
    }

    @Override
    public void set3ByteInt(long index, int additionalOffset, int value) {
        ByteBuffer b = getBuffer(index);
        int o = offset(index) + additionalOffset;
        b.putShort(o, (short) value);
        b.put(o + Short.BYTES, (byte) (value >>> Short.SIZE));
    }

    @Override
    public void setInt(long index, int additionalOffset, int value) {
        getBuffer(index).putInt(offset(index) + additionalOffset, value);
    }

    @Override
    public void set5ByteLong(long index, int additionalOffset, long value) {
        ByteBuffer b = getBuffer(index);
        int o = offset(index) + additionalOffset;
        b.putInt(o, (int) value);
        b.put(o + Integer.BYTES, (byte) (value >>> Integer.SIZE));
    }

    @Override
    public void set6ByteLong(long index, int additionalOffset, long value) {
        ByteBuffer b = getBuffer(index);
        int o = offset(index) + additionalOffset;
        b.putInt(o, (int) value);
        b.putShort(o + Integer.BYTES, (short) (value >>> Integer.SIZE));
    }

    @Override
    public void setLong(long index, int additionalOffset, long value) {
        getBuffer(index).putLong(offset(index) + additionalOffset, value);
    }

    @Override
    public void swap(long fromIndex, long toIndex) {
        ByteBuffer fromBuffer = getBuffer(fromIndex);
        ByteBuffer toBuffer = getBuffer(toIndex);
        int fromOffset = offset(fromIndex);
        int toOffset = offset(toIndex);

        if (elementSize == 1) {
            byte tmp = fromBuffer.get(fromOffset);
            fromBuffer.put(fromOffset, toBuffer.get(toOffset));
            toBuffer.put(toOffset, tmp);
        } else if (elementSize == 2) {
            short tmp = fromBuffer.getShort(fromOffset);
            fromBuffer.putShort(fromOffset, toBuffer.getShort(toOffset));
            toBuffer.putShort(toOffset, tmp);
        } else if (elementSize == 4) {
            int tmp = fromBuffer.getInt(fromOffset);
            fromBuffer.putInt(fromOffset, toBuffer.getInt(toOffset));
            toBuffer.putInt(toOffset, tmp);
        } else if (elementSize == 8) {
            long tmp = fromBuffer.getLong(fromOffset);
            fromBuffer.putLong(fromOffset, toBuffer.getLong(toOffset));
            toBuffer.putLong(toOffset, tmp);
        } else {
            byte[] tmp = new byte[elementSize];
            fromBuffer.get(fromOffset, tmp);
            fromBuffer.put(fromOffset, toBuffer, toOffset, elementSize);
            toBuffer.put(toOffset, tmp);
        }
    }

    private static long signExtension(long value, long signMask) {
        return (value & signMask) == 0 ? value : value | signMask;
    }

    private static int signExtension(int value, int signMask) {
        return (value & signMask) == 0 ? value : value | signMask;
    }
}

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
package org.neo4j.internal.batchimport.cache.idmapping.string;

import static org.neo4j.io.pagecache.PageCache.PAGE_SIZE;

import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.internal.batchimport.cache.ByteArray;
import org.neo4j.internal.batchimport.cache.MemoryStatsVisitor;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.string.UTF8;

/**
 * Stores collision values efficiently for retrieval later. Supports multiple threads adding and getting values.
 */
public class CollisionValues implements MemoryStatsVisitor.Visitable, AutoCloseable {
    private static final byte TYPE_STRING = 0x0;
    private static final byte TYPE_LONG = 0x1;
    private static final int STRING_LENGTH_IS_INLINED = 0b10;
    private static final int MAX_STRING_LENGTH_MASK = 0b111111;
    private static final int STRING_LENGTH_FIELD_SHIFT = 2;

    private final ByteArray cache;
    private final AtomicLong offset = new AtomicLong();

    public CollisionValues(NumberArrayFactory factory, long numberOfCollisions, MemoryTracker memoryTracker) {
        int chunkSize = Math.max((int) numberOfCollisions, PAGE_SIZE);
        cache = factory.newDynamicByteArray(chunkSize, new byte[1], memoryTracker);
    }

    /**
     * Adds a value returning an offset which is used to retrieve it later.
     * @param id the value to add.
     * @return an offset to retrieve the value later.
     */
    public long add(Object id) {
        if (id instanceof String stringId) {
            return addString(stringId);
        } else if (id instanceof Number numberId) {
            return addLong(numberId.longValue());
        } else {
            throw new IllegalArgumentException(id.getClass().getName());
        }
    }

    private long addString(String string) {
        byte[] bytes = UTF8.encode(string);
        int length = bytes.length;
        if (length > 0xFFFF) {
            throw new IllegalArgumentException(string);
        }

        long startOffset = offset.getAndAdd(3 + length);
        long offset = startOffset;
        // [llll,llit]
        // t: type (in this case STRING(=0))
        // i: length is inlined
        // l: length if inlined, otherwise number of bytes required to store length
        byte header = TYPE_STRING;
        int headerLengthField;
        int numberOfLengthBytes = 0;
        if (length <= MAX_STRING_LENGTH_MASK) {
            header |= STRING_LENGTH_IS_INLINED;
            headerLengthField = length;
        } else {
            numberOfLengthBytes = ((Integer.SIZE - Integer.numberOfLeadingZeros(length) - 1) / Byte.SIZE) + 1;
            headerLengthField = numberOfLengthBytes;
        }
        header |= (byte) (headerLengthField << STRING_LENGTH_FIELD_SHIFT);
        cache.setByte(offset++, 0, header);
        for (int i = 0; i < numberOfLengthBytes; i++) {
            cache.setByte(offset++, 0, (byte) (length >>> (Byte.SIZE * i)));
        }
        cache.set(offset, bytes);
        return startOffset;
    }

    private long addLong(long longId) {
        int numberOfBytes = ((Long.SIZE - Long.numberOfLeadingZeros(longId) - 1) / Byte.SIZE) + 1;
        byte header = TYPE_LONG;
        header |= (byte) (numberOfBytes << 1);
        long startOffset = offset.getAndAdd(1 + numberOfBytes);
        long offset = startOffset;
        cache.setByte(offset++, 0, header);
        for (int i = 0; i < numberOfBytes; i++) {
            cache.setByte(offset++, 0, (byte) (longId >>> (Byte.SIZE * i)));
        }
        return startOffset;
    }

    /**
     * Retrieves a value previously added with {@link #add(Object)}.
     * @param offset the offset returned from {@link #add(Object)}.
     * @return the value added at the given offset.
     */
    public Object get(long offset) {
        byte header = cache.getByte(offset++, 0);
        if ((header & 0x1) == TYPE_STRING) {
            return readString(offset, header);
        } else {
            return readLong(offset, header);
        }
    }

    private String readString(long offset, byte header) {
        int lengthField = (header >>> STRING_LENGTH_FIELD_SHIFT) & MAX_STRING_LENGTH_MASK;
        int length = 0;
        if ((header & STRING_LENGTH_IS_INLINED) != 0) {
            length = lengthField;
        } else {
            for (int i = 0; i < lengthField; i++) {
                int part = cache.getByte(offset++, 0) & 0xFF;
                length |= (part << (Byte.SIZE * i));
            }
        }
        byte[] bytes = new byte[length];
        cache.get(offset, bytes);
        return UTF8.decode(bytes);
    }

    private long readLong(long offset, byte header) {
        int length = header >>> 1;
        long value = 0;
        for (int i = 0; i < length; i++) {
            long part = cache.getByte(offset++, 0) & 0xFF;
            value |= (part << (Byte.SIZE * i));
        }
        return value;
    }

    @Override
    public void acceptMemoryStatsVisitor(MemoryStatsVisitor visitor) {
        cache.acceptMemoryStatsVisitor(visitor);
    }

    @Override
    public void close() {
        cache.close();
    }
}

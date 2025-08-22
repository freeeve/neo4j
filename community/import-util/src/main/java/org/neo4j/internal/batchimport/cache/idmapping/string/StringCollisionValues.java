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
 * Stores {@link String strings} in a {@link ByteArray} provided by {@link NumberArrayFactory}. Each string can have different
 * length, where maximum string length is 2^16 - 1.
 */
public class StringCollisionValues implements CollisionValues {
    private final ByteArray cache;
    private final AtomicLong offset = new AtomicLong();

    public StringCollisionValues(NumberArrayFactory factory, long numberOfCollisions, MemoryTracker memoryTracker) {
        int chunkSize = Math.max((int) numberOfCollisions, PAGE_SIZE);
        cache = factory.newDynamicByteArray(chunkSize, new byte[1], memoryTracker);
    }

    @Override
    public long add(Object id) {
        String string = StringEncoder.convertToString(id);
        byte[] bytes = UTF8.encode(string);
        int length = bytes.length;
        if (length > 0xFFFF) {
            throw new IllegalArgumentException(string);
        }

        long startOffset = offset.getAndAdd(2 + length);
        long offset = startOffset;
        cache.setByte(offset++, 0, (byte) length);
        cache.setByte(offset++, 0, (byte) (length >>> Byte.SIZE));
        cache.set(offset, bytes);
        return startOffset;
    }

    @Override
    public Object get(long offset) {
        int length = cache.getByte(offset++, 0) & 0xFF;
        length |= (cache.getByte(offset++, 0) & 0xFF) << Byte.SIZE;
        byte[] bytes = new byte[length];
        cache.get(offset, bytes);
        return UTF8.decode(bytes);
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

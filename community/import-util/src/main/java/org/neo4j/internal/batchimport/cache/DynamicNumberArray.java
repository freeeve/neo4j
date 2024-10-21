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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.neo4j.memory.MemoryTracker;

/**
 * Base class for common functionality for any {@link NumberArray} where the data is dynamically growing,
 * where parts can live inside and parts off-heap.
 *
 * @see NumberArrayFactory#newDynamicLongArray(long, long, MemoryTracker)
 * @see NumberArrayFactory#newDynamicIntArray(long, int, MemoryTracker)
 */
abstract class DynamicNumberArray<N extends NumberArray<N>> implements NumberArray<N> {
    protected final NumberArrayFactory factory;
    protected final long chunkSize;
    protected N[] chunks;
    protected final AtomicInteger numberOfChunks = new AtomicInteger(0);

    DynamicNumberArray(NumberArrayFactory factory, long chunkSize, N[] initialChunks) {
        this.factory = factory;
        this.chunkSize = chunkSize;
        this.chunks = initialChunks;
    }

    @Override
    public long length() {
        return numberOfChunks.getAcquire() * chunkSize;
    }

    @Override
    public void clear() {
        for (N chunk : chunks) {
            chunk.clear();
        }
    }

    @Override
    public void acceptMemoryStatsVisitor(MemoryStatsVisitor visitor) {
        for (N chunk : chunks) {
            chunk.acceptMemoryStatsVisitor(visitor);
        }
    }

    protected N chunkOrNullAt(long index) {
        int chunkIndex = chunkIndex(index);
        return chunkIndex < numberOfChunks.getAcquire() ? chunks[chunkIndex] : null;
    }

    protected int chunkIndex(long index) {
        return (int) (index / chunkSize);
    }

    @Override
    public N at(long index) {
        if (index >= length()) {
            synchronizedAddChunk(index);
        }

        int chunkIndex = chunkIndex(index);
        return chunks[chunkIndex];
    }

    private synchronized void synchronizedAddChunk(long index) {
        if (index >= length()) {
            int newLength = chunkIndex(index) + 1;
            N[] newChunks = Arrays.copyOf(chunks, newLength);
            for (int i = chunks.length; i < newLength; i++) {
                newChunks[i] = addChunk(chunkSize, chunkSize * i);
            }
            chunks = newChunks;
            numberOfChunks.setPlain(newLength);
        }
    }

    protected abstract N addChunk(long chunkSize, long base);

    @Override
    public void close() {
        for (N chunk : chunks) {
            chunk.close();
        }
    }
}

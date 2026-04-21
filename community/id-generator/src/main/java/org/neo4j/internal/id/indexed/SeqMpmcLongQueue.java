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
package org.neo4j.internal.id.indexed;

import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static org.neo4j.util.Preconditions.requirePowerOfTwo;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Multi Producer Multiple Consumers FIFO queue based on the Dmitry Vyukov sequence-number algorithm.
 * <p>
 * Each slot in the buffer carries an explicit sequence number that acts as the sole synchronization
 * point between producers and consumers. A {@code setRelease} on the sequence number publishes a
 * written value; a {@code getAcquire} on the sequence number observes it. This makes the queue
 * correct on relaxed memory architectures without requiring stronger ordering on the position
 * counters or the data fields themselves.
 * <p>
 * The enqueue and dequeue positions are stored at the front of the same
 * {@link AtomicLongArray} as the buffer, each padded to its own cache line to prevent false sharing
 * between producers and consumers.
 * <p>
 * Unlike {@link MpmcLongQueue}, this implementation has no sentinel value restriction and can store
 * any {@code long}, including {@code -1}.
 * <p>
 * Capacity must be a power of two.
 */
public class SeqMpmcLongQueue implements ConcurrentLongQueue {
    private static final int LONGS_PER_CACHE_LINE = 8;
    private static final int OFFER_POSITION_OFFSET = 0;
    private static final int TAKE_POSITION_OFFSET = OFFER_POSITION_OFFSET + LONGS_PER_CACHE_LINE;
    private static final int BUFFER_OFFSET = TAKE_POSITION_OFFSET + LONGS_PER_CACHE_LINE;

    private final AtomicLongArray buffer;
    private final int mask;

    public SeqMpmcLongQueue(int capacity) {
        requirePowerOfTwo(capacity);
        buffer = new AtomicLongArray(BUFFER_OFFSET + capacity * 2);
        mask = capacity - 1;
        for (int i = 0; i < capacity; i++) {
            buffer.setPlain(idx(i), i);
        }
    }

    @Override
    public boolean offer(long data) {
        int idx;
        long pos = buffer.getOpaque(OFFER_POSITION_OFFSET);
        while (true) {
            idx = idx(pos);
            long diff = buffer.getAcquire(idx) - pos;
            if (diff == 0) {
                if (buffer.weakCompareAndSetPlain(OFFER_POSITION_OFFSET, pos, pos + 1)) {
                    break;
                }
            } else if (diff < 0) {
                return false;
            } else {
                pos = buffer.getOpaque(OFFER_POSITION_OFFSET);
            }
        }

        buffer.setPlain(idx + 1, data);
        buffer.setRelease(idx, pos + 1);

        return true;
    }

    @Override
    public long takeOrDefault(long defaultValue) {
        int idx;
        long pos = buffer.getOpaque(TAKE_POSITION_OFFSET);
        while (true) {
            idx = idx(pos);
            long diff = buffer.getAcquire(idx) - (pos + 1);
            if (diff == 0) {
                if (buffer.weakCompareAndSetPlain(TAKE_POSITION_OFFSET, pos, pos + 1)) {
                    break;
                }
            } else if (diff < 0) {
                return defaultValue;
            } else {
                pos = buffer.getOpaque(TAKE_POSITION_OFFSET);
            }
        }

        long data = buffer.getPlain(idx + 1);
        buffer.setRelease(idx, pos + mask + 1);

        return data;
    }

    @Override
    public long takeInRange(long minBoundary, long maxBoundary) {
        int idx;
        long data;
        long pos = buffer.getOpaque(TAKE_POSITION_OFFSET);
        while (true) {
            idx = idx(pos);
            long diff = buffer.getAcquire(idx) - (pos + 1);
            if (diff == 0) {
                data = buffer.getPlain(idx + 1);
                if (data >= maxBoundary || data < minBoundary) {
                    return Long.MAX_VALUE;
                }
                if (buffer.weakCompareAndSetPlain(TAKE_POSITION_OFFSET, pos, pos + 1)) {
                    break;
                }
            } else if (diff < 0) {
                return Long.MAX_VALUE;
            } else {
                pos = buffer.getOpaque(TAKE_POSITION_OFFSET);
            }
        }

        buffer.setRelease(idx, pos + mask + 1);

        return data;
    }

    @Override
    public int size() {
        // Max with 0 prevents occasional negative numbers from non-atomic double read
        return toIntExact(max(0L, buffer.get(OFFER_POSITION_OFFSET) - buffer.get(TAKE_POSITION_OFFSET)));
    }

    @Override
    public int availableSpace() {
        return mask + 1 - size();
    }

    private int idx(long pos) {
        return ((int) (pos & mask) << 1) + BUFFER_OFFSET;
    }
}

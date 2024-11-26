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
package org.neo4j.consistency.checking.cache;

import static org.neo4j.internal.batchimport.cache.NumberArrayFactories.AUTO_WITHOUT_SWAP;

import org.neo4j.consistency.checking.ByteArrayBitsManipulator;
import org.neo4j.consistency.checking.IdAssigningThreadLocal;
import org.neo4j.internal.batchimport.cache.ByteArray;
import org.neo4j.memory.MemoryTracker;

/**
 * {@link CacheAccess} that uses {@link PackedMultiFieldCache} for cache.
 */
public class DefaultCacheAccess implements CacheAccess {

    private final IdAssigningThreadLocal<Client> clients = new IdAssigningThreadLocal<>() {
        @Override
        protected Client initialValue(int id) {
            return new DefaultClient(id);
        }
    };

    private final PackedMultiFieldCache cache;
    private long pivotId;

    public static ByteArray defaultByteArray(long highNodeId, MemoryTracker memoryTracker) {
        return AUTO_WITHOUT_SWAP.newByteArray(highNodeId, new byte[ByteArrayBitsManipulator.MAX_BYTES], memoryTracker);
    }

    public DefaultCacheAccess(ByteArray array) {
        this.cache = new PackedMultiFieldCache(array, ByteArrayBitsManipulator.MAX_SLOT_BITS, 1);
    }

    @Override
    public Client client() {
        return clients.get();
    }

    @Override
    public void clearCache() {
        cache.clearParallel(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void setCacheSlotSizes(int... slotSizes) {
        cache.setSlotSizes(slotSizes);
    }

    @Override
    public void setCacheSlotSizesAndClear(int... slotSizes) {
        cache.setSlotSizes(slotSizes);
        cache.clearParallel(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void setPivotId(long pivotId) {
        this.pivotId = pivotId;
    }

    private long translate(long id) {
        return id - pivotId;
    }

    private class DefaultClient implements Client {
        private final int threadIndex;

        DefaultClient(int threadIndex) {
            this.threadIndex = threadIndex;
        }

        @Override
        public long getFromCache(long id, int slot) {
            return cache.get(translate(id), slot);
        }

        @Override
        public boolean getBooleanFromCache(long id, int slot) {
            return cache.get(translate(id), slot) != 0;
        }

        @Override
        public void putToCache(long id, long... values) {
            cache.put(translate(id), values);
        }

        @Override
        public void putToCacheSingle(long id, int slot, long value) {
            cache.put(translate(id), slot, value);
        }

        @Override
        public String toString() {
            return "Client[" + threadIndex + "]";
        }
    }
}

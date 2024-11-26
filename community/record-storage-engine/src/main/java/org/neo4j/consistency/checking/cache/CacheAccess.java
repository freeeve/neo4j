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

/**
 * The access patterns to {@link CacheAccess} is designed to have multiple threads concurrently, and so
 * {@link #client()} provides a {@link Client} that accesses the cache on the current thread's behalf.
 *
 * The cache is a compact representation of records, tied to an id, for example nodeId. There can be multiple
 * cached values per id, selected by {@code slot}.
 */
public interface CacheAccess {
    /**
     * Client per thread for accessing cache and counts for statistics
     */
    interface Client {
        /**
         * Gets a cached value, put there with {@link #putToCache(long, long...)} or
         * {@link #putToCacheSingle(long, int, long)}.
         *
         * @param id the entity id this cached value is tied to.
         * @param slot which cache slot for this id.
         * @return the cached value.
         */
        long getFromCache(long id, int slot);

        /**
         * Gets a cached value, put there with {@link #putToCache(long, long...)} or
         * {@link #putToCacheSingle(long, int, long)} and interpret field value as a boolean.
         * 0 will be treated as false all the rest as true.
         *
         * @param id the entity id this cached value is tied to.
         * @param slot which cache slot for this id.
         * @return false if slot value is 0, true otherwise.
         */
        boolean getBooleanFromCache(long id, int slot);

        /**
         * Caches all values for an id, i.e. fills all slots.
         *
         * @param id the entity id these cached values will be tied to.
         * @param cacheFields the values to cache, one per slot.
         */
        void putToCache(long id, long... cacheFields);

        /**
         * Caches a single value for an id and slot.
         *
         * @param id the entity id this cached values will be tied to.
         * @param slot the slot for the given {@code id}.
         * @param value the value to cache for this id and slot.
         */
        void putToCacheSingle(long id, int slot, long value);
    }

    /**
     * @return {@link Client} for the current {@link Thread}.
     */
    Client client();

    /**
     * Clears all cached values.
     */
    void clearCache();

    /**
     * Sets the slot sizes of the cached values.
     *
     * @param slotSize defines how many and how big the slots are for cached values that are put after this call.
     */
    void setCacheSlotSizes(int... slotSize);

    /**
     * Sets the slot sizes of the cached values. Also clears the cache.
     *
     * @param slotSize defines how many and how big the slots are for cached values that are put after this call.
     */
    void setCacheSlotSizesAndClear(int... slotSize);

    /**
     * Sets the node id that is 0, such that all cache interactions uses this pivot node id to calculate the actual node id.
     * This is because the node id is used as index into the cache and the cache may be used to run multiple iterations over
     * a store, where only parts of the store is checked.
     */
    void setPivotId(long pivotId);

    Client EMPTY_CLIENT = new Client() {

        @Override
        public void putToCache(long id, long... cacheFields) {}

        @Override
        public void putToCacheSingle(long id, int slot, long value) {}

        @Override
        public long getFromCache(long id, int slot) {
            return 0;
        }

        @Override
        public boolean getBooleanFromCache(long id, int slot) {
            return false;
        }
    };

    CacheAccess EMPTY = new CacheAccess() {
        @Override
        public Client client() {
            return EMPTY_CLIENT;
        }

        @Override
        public void setCacheSlotSizes(int... slotSizes) {}

        @Override
        public void setCacheSlotSizesAndClear(int... slotSizes) {}

        @Override
        public void clearCache() {}

        @Override
        public void setPivotId(long pivotId) {}
    };
}

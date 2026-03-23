package org.neo4j.fleetmanagement.common;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

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
public class CachedMethod<T> {
    private T cachedValue;
    private Instant lastCacheTime;
    private static final Duration CACHE_DURATION = Duration.ofHours(1);

    /**
     * Executes the provided supplier function and caches its result.
     * If called again within 1 hour, returns the cached value instead.
     *
     * @param supplier The function to execute if cache is not available or expired
     * @return The result of the supplier function or the cached value if available
     */
    public T GetCachedOrRun(Supplier<T> supplier) {
        Instant now = Instant.now();

        // If we have a cached value and it's less than 1 hour old, return it
        if (lastCacheTime != null && Duration.between(lastCacheTime, now).compareTo(CACHE_DURATION) < 0) {
            return cachedValue;
        }

        // Otherwise, execute the supplier, cache the result, and return it
        cachedValue = supplier.get();
        lastCacheTime = now;
        return cachedValue;
    }
}

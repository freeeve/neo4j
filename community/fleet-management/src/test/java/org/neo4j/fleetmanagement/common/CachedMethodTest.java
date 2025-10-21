package org.neo4j.fleetmanagement.common;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class CachedMethodTest {

    @Test
    void shouldReturnValueFromSupplier() {
        // Given
        CachedMethod<String> cachedMethod = new CachedMethod<>();

        // When
        String result = cachedMethod.GetCachedOrRun(() -> "test value");

        // Then
        assertEquals("test value", result);
    }

    @Test
    void shouldReturnCachedValueWhenCalledAgainWithinCacheDuration() {
        // Given
        CachedMethod<Integer> cachedMethod = new CachedMethod<>();
        AtomicInteger counter = new AtomicInteger(0);

        // When
        // First call should execute the lambda and increment the counter
        Integer firstResult = cachedMethod.GetCachedOrRun(() -> counter.incrementAndGet());

        // Second call should return cached value without incrementing the counter
        Integer secondResult = cachedMethod.GetCachedOrRun(() -> counter.incrementAndGet());

        // Then
        assertEquals(1, firstResult);
        assertEquals(1, secondResult);
        assertEquals(1, counter.get(), "Lambda should only be executed once");
    }

    @Test
    void shouldExecuteLambdaAgainAfterCacheExpires() throws Exception {
        // Given
        CachedMethod<Integer> cachedMethod = new CachedMethod<>();
        AtomicInteger counter = new AtomicInteger(0);

        // When
        // First call should execute the lambda and increment the counter
        Integer firstResult = cachedMethod.GetCachedOrRun(() -> counter.incrementAndGet());

        // Simulate cache expiration by setting lastCacheTime to more than 1 hour ago
        Field lastCacheTimeField = CachedMethod.class.getDeclaredField("lastCacheTime");
        lastCacheTimeField.setAccessible(true);
        lastCacheTimeField.set(cachedMethod, Instant.now().minus(61, ChronoUnit.MINUTES));

        // After cache expiration, the lambda should be executed again
        Integer secondResult = cachedMethod.GetCachedOrRun(() -> counter.incrementAndGet());

        // Then
        assertEquals(1, firstResult);
        assertEquals(2, secondResult);
        assertEquals(2, counter.get(), "Lambda should be executed twice");
    }
}

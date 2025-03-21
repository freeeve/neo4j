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
package org.neo4j.consistency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.io.ByteUnit.kibiBytes;
import static org.neo4j.io.ByteUnit.mebiBytes;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class ConsistencyCheckMemoryCalculationTest {
    @Test
    void shouldKeepPageCacheMemoryIfEnoughMaxMemory() {
        // given
        var desiredPageCacheMemory = mebiBytes(16);
        var desiredOffHeapCachingMemory = mebiBytes(1);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                mebiBytes(100), desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory()).isEqualTo(desiredPageCacheMemory);
        assertThat(distribution.offHeapCachingMemory()).isGreaterThanOrEqualTo(desiredOffHeapCachingMemory);
    }

    @Test
    void shouldConstrainPageCacheBarelyAboveLimit() {
        // given
        var desiredPageCacheMemory = mebiBytes(100);
        var desiredOffHeapCachingMemory = mebiBytes(10);
        var maxOffHeapMemory = mebiBytes(90);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                maxOffHeapMemory, desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory()).isEqualTo(maxOffHeapMemory - desiredOffHeapCachingMemory);
        assertThat(distribution.offHeapCachingMemory()).isEqualTo(desiredOffHeapCachingMemory);
    }

    @Test
    void shouldConstrainPageCacheMoreOnWayAboveLimit() {
        // given
        var desiredPageCacheMemory = mebiBytes(100);
        var desiredOffHeapCachingMemory = mebiBytes(10);
        var maxOffHeapMemory = mebiBytes(15);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                maxOffHeapMemory, desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory()).isLessThan(desiredPageCacheMemory);
        assertThat(distribution.offHeapCachingMemory()).isLessThan(desiredOffHeapCachingMemory);
    }

    @Test
    void shouldAllocateAllAvailableMemoryWhenLimited() {
        // given
        var desiredPageCacheMemory = mebiBytes(100);
        var desiredOffHeapCachingMemory = mebiBytes(100);
        var maxOffHeapMemory = mebiBytes(150);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                maxOffHeapMemory, desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory() + distribution.offHeapCachingMemory())
                .isEqualTo(maxOffHeapMemory);
    }

    @Test
    void shouldNotAllocateMoreThanNecessary() {
        // given
        var desiredPageCacheMemory = mebiBytes(10);
        var desiredOffHeapCachingMemory = mebiBytes(10);
        var maxOffHeapMemory = mebiBytes(100);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                maxOffHeapMemory, desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory() + distribution.offHeapCachingMemory())
                .isEqualTo(desiredOffHeapCachingMemory + desiredPageCacheMemory);
    }

    @Test
    void shouldConstrainOffHeapWhenLimitedOnMemory() {
        // given
        var desiredPageCacheMemory = mebiBytes(80);
        var desiredOffHeapCachingMemory = mebiBytes(80);
        var maxOffHeapMemory = mebiBytes(32);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                maxOffHeapMemory, desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory()).isLessThan(desiredPageCacheMemory);
        assertThat(distribution.offHeapCachingMemory()).isLessThan(desiredOffHeapCachingMemory);
        assertThat(distribution.pageCacheMemory() + distribution.offHeapCachingMemory())
                .isEqualTo(maxOffHeapMemory);
        assertThat((double) distribution.offHeapCachingMemory())
                .isCloseTo(0.25D * maxOffHeapMemory, Offset.offset(0.5));
    }

    @Test
    void shouldAllocateAtLeastDecentMinimumSizes() {
        // given
        var desiredPageCacheMemory = kibiBytes(10);
        var desiredOffHeapCachingMemory = kibiBytes(10);
        var maxOffHeapMemory = kibiBytes(100);

        // when
        var distribution = ConsistencyCheckMemoryCalculation.calculate(
                maxOffHeapMemory, desiredPageCacheMemory, desiredOffHeapCachingMemory);

        // then
        assertThat(distribution.pageCacheMemory()).isEqualTo(ConsistencyCheckMemoryCalculation.MIN_SIZE);
        assertThat(distribution.offHeapCachingMemory()).isEqualTo(ConsistencyCheckMemoryCalculation.MIN_SIZE);
    }
}

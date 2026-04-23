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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.internal.batchimport.cache.NumberArrayFactories;

class GroupCacheTest {
    @Test
    void shouldHandleSingleByteCount() {
        // given
        int max = 256;
        try (GroupCache cache = GroupCache.select(NumberArrayFactories.OFF_HEAP, 100, max, INSTANCE)) {

            // when
            assertSetAndGet(cache, 10, 45);
            assertSetAndGet(cache, 100, 145);
            assertSetAndGet(cache, 1000, 245);

            // then
            assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> cache.set(10000, 345));
        }
    }

    @Test
    void shouldSwitchToTwoByteVersionBeyondSingleByteGroupIds() {
        // given
        int max = 257;
        try (GroupCache cache = GroupCache.select(NumberArrayFactories.OFF_HEAP, 100, max, INSTANCE)) {

            // when
            assertSetAndGet(cache, 10, 123);
            assertSetAndGet(cache, 100, 1234);
            assertSetAndGet(cache, 1000, 12345);
            assertSetAndGet(cache, 10000, 0xFFFF);

            // then
            assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> cache.set(100000, 123456));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void shouldSelectZeroMemoryVersion(int numGroups) {
        // when
        try (GroupCache cache = GroupCache.select(NumberArrayFactories.OFF_HEAP, 100, numGroups, INSTANCE)) {

            // then
            assertThat(cache).isSameAs(GroupCache.SINGLE);
            assertSetAndGet(cache, 123, 0);
        }
    }

    private static void assertSetAndGet(GroupCache cache, long nodeId, int groupId) {
        cache.set(nodeId, groupId);
    }
}

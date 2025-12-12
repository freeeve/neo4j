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
package org.neo4j.cypher.jdbc.mapping.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.neo4j.values.ElementIdMapper;

public class GlobalIdsTest {

    @Test
    void generateUniqueIds() throws InterruptedException {
        final var size = 10_000;
        final var idGen = GlobalIds.create(ElementIdMapper.PLACEHOLDER);
        final var seenNodeIds = new ConcurrentHashMap<Long, Boolean>();
        final var seenRelIds = new ConcurrentHashMap<Long, Boolean>();

        try (final var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < size; ++i) {
                final var externalId = i;
                executor.submit(() -> seenNodeIds.put(idGen.nodeId("schema-a", externalId), true));
                executor.submit(() -> seenNodeIds.put(idGen.nodeId("schema-b", externalId), true));
                executor.submit(() -> seenRelIds.put(idGen.relationshipId("schema-a", externalId), true));
                executor.submit(() -> seenRelIds.put(idGen.relationshipId("schema-b", externalId), true));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.MINUTES)).isTrue();
        }

        assertThat(seenNodeIds.size()).isEqualTo(2 * size);
        assertThat(seenRelIds.size()).isEqualTo(2 * size);
    }

    @Test
    void reuseIds() throws InterruptedException {
        final var size = 10_000;
        final var idGen = GlobalIds.create(ElementIdMapper.PLACEHOLDER);
        final var seenNodeIds = new ConcurrentHashMap<Long, Boolean>();
        final var seenRelIds = new ConcurrentHashMap<Long, Boolean>();

        try (final var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < size; ++i) {
                executor.submit(() -> seenNodeIds.put(idGen.nodeId("schema-a", 42), true));
                executor.submit(() -> seenRelIds.put(idGen.relationshipId("schema-a", 42), true));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.MINUTES)).isTrue();
        }

        assertThat(seenNodeIds.size()).isEqualTo(1);
        assertThat(seenRelIds.size()).isEqualTo(1);
    }
}

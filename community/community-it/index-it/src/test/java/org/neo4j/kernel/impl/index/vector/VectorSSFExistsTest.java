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
package org.neo4j.kernel.impl.index.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.kernel.impl.index.vector.VectorSSFQueryResult.field;
import static org.neo4j.values.storable.DurationValue.duration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.Values;

class VectorSSFExistsTest extends VectorSSFTestBase {

    @Test
    void singleInteger() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "age");
        createTestNode(Map.of("id", 10, "name", "Alice", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", "age", 18, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "name", "Carol", EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "name", "Ted", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "name", "Bob", "age", 45, EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(existsQuery("age")))
                .extracting(EXTRACT_ID)
                .containsExactlyInAnyOrder(10L, 20L, 40L, 50L);
    }

    @Test
    void singleString() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name");
        createTestNode(Map.of("id", 10, "name", "Alice", EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "name", "Ted", EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "name", "Bob", EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(existsQuery("name")))
                .extracting(EXTRACT_ID)
                .containsExactlyInAnyOrder(10L, 20L, 40L, 50L);
    }

    @Test
    void stringAndInteger() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", "age");
        createTestNode(Map.of("id", 10, "name", "Alice", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", "age", 18, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "name", "Carol", EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "name", "Bob", "age", 45, EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(existsQuery("name"), existsQuery("age")))
                .extracting(EXTRACT_ID)
                .containsExactlyInAnyOrder(10L, 20L, 50L);

        assertThat(queryNodeIndex(existsQuery("name"), exactQuery("age", Values.of(23))))
                .singleElement()
                .has(field("name", "Alice"));

        assertThat(queryNodeIndex(rangeQuery("name", Values.of("B"), true, Values.of("C"), true), existsQuery("age")))
                .extracting(EXTRACT_ID)
                .containsExactlyInAnyOrder(20L, 50L);
    }

    @Test
    void durations() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "duration");
        createTestNode(Map.of("id", 10, "duration", duration(0, 0, 0, 1000_000), EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "duration", duration(0, 0, 1, 0), EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "duration", duration(0, 1, 0, 0), EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "notDuration", duration(0, 1, 1, 0), EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "duration", duration(1, 1, 1, 1), EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(existsQuery("duration")))
                .extracting(EXTRACT_ID)
                .containsExactlyInAnyOrder(10L, 20L, 30L, 50L);
    }

    @Test
    void nonIndexableType() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "location");
        createTestNode(
                Map.of("id", 10, "name", "Alice", "location", "Malmö Office", EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", "location", "London Office", EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(
                Map.of("id", 30, "name", "Carol", "location", "London Office", EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "name", "Ted", EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of(
                "id",
                50,
                "name",
                "Bob",
                "location",
                Values.pointValue(CoordinateReferenceSystem.WGS_84, 12.994840, 55.612103),
                EMBEDDING_NAME,
                EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(existsQuery("location")))
                .extracting(EXTRACT_ID)
                .containsExactlyInAnyOrder(10L, 20L, 30L, 50L);
    }
}

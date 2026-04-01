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
import static org.neo4j.values.storable.Values.FALSE;
import static org.neo4j.values.storable.Values.TRUE;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.values.storable.Values;

class VectorSSFExactTest extends VectorSSFTestBase {

    @Test
    void singleString() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name");
        createTestNode(Map.of("id", 10, "name", "Alice", EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "name", "Carol", EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "name", "Ted", EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "name", "Bob", EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(exactQuery("name", Values.of("Bob"))))
                .hasSize(2)
                .have(field("name", "Bob"));
    }

    @Test
    void singleInteger() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "age");
        createTestNode(Map.of("id", 1, "age", 75, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 10, "name", "Alice", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 20, "name", "Bob", "age", 18, EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 30, "name", "Carol", "age", 45, EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 40, "name", "Ted", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(5)));
        createTestNode(Map.of("id", 50, "name", "Bob", "age", 45, EMBEDDING_NAME, EMBEDDINGS.get(6)));

        assertThat(queryNodeIndex(exactQuery("age", Values.of(75))))
                .singleElement()
                .has(field("id", 1));
        assertThat(queryNodeIndex(exactQuery("age", Values.of(74)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(76)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(75.0f))))
                .singleElement()
                .has(field("id", 1));
        assertThat(queryNodeIndex(exactQuery("age", Values.of(75.5f)))).isEmpty();

        assertThat(queryNodeIndex(exactQuery("age", Values.of(23)))).hasSize(2).have(field("age", 23));
        assertThat(queryNodeIndex(exactQuery("age", Values.of(45)))).hasSize(2).have(field("age", 45));
        assertThat(queryNodeIndex(exactQuery("age", Values.of(18))))
                .singleElement()
                .has(field("age", 18))
                .has(field("name", "Bob"));
        assertThat(queryNodeIndex(exactQuery("age", Values.of(21)))).isEmpty();
    }

    @Test
    void stringAndInteger() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", "age", "shoesize");
        createTestNode(Map.of("id", 10, "name", "Alice", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", "age", 18, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "name", "Carol", "age", 45, EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "name", "Ted", "age", 23, EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "name", "Bob", "age", 45, EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(exactQuery("name", Values.of("Bob")), exactQuery("age", Values.of(45))))
                .singleElement()
                .has(field("name", "Bob"))
                .has(field("age", 45));
    }

    @Test
    void stringAndIntegerAndFloat() throws Exception {

        // in order to break the index id ordering being 1,2,3
        createTestNode(Map.of("id", 103, "priority", 15, "story", "Once upon a time", "age", 128));
        createTestNode(Map.of("id", 104, "shoesize", 11.5, "story", "In a galaxy far, far away"));

        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", "age", "shoesize");
        createTestNode(
                Map.of("id", 10, "name", "Alice", "age", 23, "shoesize", 8.5, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "name", "Bob", "age", 18, "shoesize", 8.5, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(
                Map.of("id", 30, "name", "Carol", "age", 45, "shoesize", 8.5, EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "name", "Ted", "age", 23, "shoesize", 8.5, EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "name", "Bob", "age", 45, "shoesize", 8.5, EMBEDDING_NAME, EMBEDDINGS.get(5)));

        // order of exact queries consistent with the index
        assertThat(queryNodeIndex(
                        exactQuery("name", Values.of("Bob")),
                        exactQuery("age", Values.of(45)),
                        exactQuery("shoesize", Values.of(8.5))))
                .singleElement()
                .has(field("name", "Bob"))
                .has(field("age", 45));

        assertThat(queryNodeIndex(
                        exactQuery("name", Values.of("Bob")), allQuery("age"), exactQuery("shoesize", Values.of(8.5))))
                .hasSize(2)
                .have(field("name", "Bob"));
    }

    @Test
    void singleBoolean() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "authorized");
        createTestNode(Map.of("id", 1, "authorized", true, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 2, "authorized", false, EMBEDDING_NAME, EMBEDDINGS.get(2)));

        assertThat(queryNodeIndex()).hasSize(2);
        assertThat(queryNodeIndex(exactQuery("authorized", TRUE)))
                .singleElement()
                .has(field("id", 1));
        assertThat(queryNodeIndex(exactQuery("authorized", FALSE)))
                .singleElement()
                .has(field("id", 2));
    }

    @Test
    void singleFloat() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "age");
        createTestNode(Map.of("id", 1, "age", 75.0f, EMBEDDING_NAME, EMBEDDINGS.get(1)));

        assertThat(queryNodeIndex(exactQuery("age", Values.of(75.0f))))
                .singleElement()
                .has(field("id", 1));
        assertThat(queryNodeIndex(exactQuery("age", Values.of(74.0f)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(76.0f)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(75))))
                .singleElement()
                .has(field("id", 1));
    }

    @Test
    void extremeFloatValues() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "age");
        createTestNode(Map.of("id", 1, "age", 75, EMBEDDING_NAME, EMBEDDINGS.get(1)));

        assertThat(queryNodeIndex(exactQuery("age", Values.of(Integer.MAX_VALUE))))
                .isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(Integer.MIN_VALUE))))
                .isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(Float.NaN)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(Float.NEGATIVE_INFINITY))))
                .isEmpty();
        assertThat(queryNodeIndex(exactQuery("age", Values.of(Float.POSITIVE_INFINITY))))
                .isEmpty();
    }

    @Test
    void durations() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "duration");
        createTestNode(Map.of("id", 10, "duration", duration(0, 0, 0, 1000_000), EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "duration", duration(0, 0, 1, 0), EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "duration", duration(0, 1, 0, 0), EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "duration", duration(0, 1, 1, 0), EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "duration", duration(1, 1, 1, 1), EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(exactQuery("duration", duration(0, 0, 0, 1000_000))))
                .singleElement()
                .has(field("id", 10));
        assertThat(queryNodeIndex(exactQuery("duration", duration(0, 0, 1, 0))))
                .singleElement()
                .has(field("id", 20));
        assertThat(queryNodeIndex(exactQuery("duration", duration(0, 1, 1, 0))))
                .singleElement()
                .has(field("id", 40));
        assertThat(queryNodeIndex(exactQuery("duration", duration(1, 1, 1, 1))))
                .singleElement()
                .has(field("id", 50));
        assertThat(queryNodeIndex(exactQuery("duration", duration(1, 1, 1, 0)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("duration", duration(1, 1, 0, 0)))).isEmpty();
        assertThat(queryNodeIndex(exactQuery("duration", duration(1, 0, 0, 0)))).isEmpty();
    }
}

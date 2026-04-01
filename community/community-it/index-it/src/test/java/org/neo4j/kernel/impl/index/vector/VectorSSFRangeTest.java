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
import static org.neo4j.values.storable.DurationValue.duration;
import static org.neo4j.values.storable.Values.FALSE;
import static org.neo4j.values.storable.Values.TRUE;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.values.storable.Values;

class VectorSSFRangeTest extends VectorSSFTestBase {

    @Test
    void booleanRange() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "authorized");
        createTestNode(Map.of("id", 1, "authorized", true, "team", "Goodies", EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 2, "authorized", false, "team", "Baddies", EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 3, "authorized", true, "team", "Goodies", EMBEDDING_NAME, EMBEDDINGS.get(3)));

        assertThat(queryNodeIndex()).hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("authorized", FALSE, true, TRUE, true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("authorized", TRUE, true, FALSE, true)))
                .isEmpty();

        // Boolean range does not respect the "inclusiveness" flag; always inclusive.
        // Should we be pedantic ? Querying a boolean range is already quite a strange thing to do.
        assertThat(queryNodeIndex(rangeQuery("authorized", FALSE, false, TRUE, false)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("authorized", FALSE, true, TRUE, false)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery("authorized", FALSE, false, TRUE, true)))
                .hasSize(2);
    }

    @Test
    void extremeFloatValues() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "age");
        createTestNode(Map.of("id", 1, "age", Float.NaN, EMBEDDING_NAME, EMBEDDINGS.get(1)));

        assertThat(queryNodeIndex(rangeQuery(
                        "age", Values.of(Float.NEGATIVE_INFINITY), true, Values.of(Float.POSITIVE_INFINITY), true)))
                .isEmpty();
        createTestNode(Map.of("id", 2, "age", Float.NEGATIVE_INFINITY, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        assertThat(queryNodeIndex(rangeQuery(
                        "age", Values.of(Float.NEGATIVE_INFINITY), true, Values.of(Float.POSITIVE_INFINITY), true)))
                .singleElement();
        createTestNode(Map.of("id", 3, "age", Float.POSITIVE_INFINITY, EMBEDDING_NAME, EMBEDDINGS.get(3)));
        assertThat(queryNodeIndex(rangeQuery(
                        "age", Values.of(Float.NEGATIVE_INFINITY), true, Values.of(Float.POSITIVE_INFINITY), true)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery(
                        "age", Values.of(Float.NEGATIVE_INFINITY), false, Values.of(Float.POSITIVE_INFINITY), true)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery(
                        "age", Values.of(Float.NEGATIVE_INFINITY), false, Values.of(Float.POSITIVE_INFINITY), false)))
                .isEmpty();
        assertThat(queryNodeIndex(
                        rangeQuery("age", Values.of(Float.NaN), true, Values.of(Float.POSITIVE_INFINITY), true)))
                .isEmpty();
        assertThat(queryNodeIndex(
                        rangeQuery("age", Values.of(Float.NEGATIVE_INFINITY), true, Values.of(Float.NaN), true)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(0.0f), true, Values.of(Float.NaN), true)))
                .isEmpty();
    }

    @Test
    void stringRange() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "password");
        createTestNode(Map.of("id", 1, "password", "zebra", EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 2, "password", "antelope", EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 3, "password", "marsupial", EMBEDDING_NAME, EMBEDDINGS.get(3)));

        assertThat(queryNodeIndex(rangeQuery("password", Values.of("aardvark"), true, Values.of("zygote"), true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("aardvark"), false, Values.of("zygote"), false)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("chameleon"), true, Values.of("zygote"), false)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("chameleon"), false, Values.of("zygote"), false)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), true, Values.of("zygote"), false)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), false, Values.of("zygote"), false)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), false, Values.of("zebra"), true)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), false, Values.of("zebra"), false)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("ZZZ"), false, Values.of("zebra"), true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("a"), false, Values.of("ZEBRA"), true)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), true, Values.of("marsupial"), true)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), true, Values.of("marsupial"), false)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), false, Values.of("marsupial"), true)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("password", Values.of("marsupial"), false, Values.of("marsupial"), false)))
                .isEmpty();
    }

    @Test
    void numericRange() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "age");

        // no data
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), true, Values.of(12.0f), false)))
                .isEmpty();
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(128), true, Values.of(127), false)))
                .isEmpty();

        createTestNode(Map.of("id", 1, "age", 75, EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 2, "age", 85, EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 3, "age", 95, EMBEDDING_NAME, EMBEDDINGS.get(3)));

        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75), true, Values.of(100), true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75), true, Values.of(95), true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75), true, Values.of(95), false)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75), false, Values.of(95), false)))
                .singleElement();

        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), true, Values.of(100.0f), true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), true, Values.of(95), true)))
                .hasSize(3);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), true, Values.of(76.0f), false)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), true, Values.of(94.99f), false)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), true, Values.of(95.0f), false)))
                .hasSize(2);
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), false, Values.of(95.0f), false)))
                .singleElement();
        assertThat(queryNodeIndex(rangeQuery("age", Values.of(75.0f), false, Values.of(Math.nextDown(95.0f)), true)))
                .singleElement();
    }

    @Test
    void durations() throws Exception {
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "duration");
        createTestNode(Map.of("id", 10, "duration", duration(0, 0, 0, 1000_000), EMBEDDING_NAME, EMBEDDINGS.get(1)));
        createTestNode(Map.of("id", 20, "duration", duration(0, 0, 1, 0), EMBEDDING_NAME, EMBEDDINGS.get(2)));
        createTestNode(Map.of("id", 30, "duration", duration(0, 1, 0, 0), EMBEDDING_NAME, EMBEDDINGS.get(3)));
        createTestNode(Map.of("id", 40, "duration", duration(0, 1, 1, 0), EMBEDDING_NAME, EMBEDDINGS.get(4)));
        createTestNode(Map.of("id", 50, "duration", duration(1, 1, 1, 1), EMBEDDING_NAME, EMBEDDINGS.get(5)));

        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), true, duration(0, 0, 1, 0), true)))
                .hasSize(1);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), false, duration(0, 0, 1, 0), true)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), true, duration(0, 0, 1, 0), false)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), true, duration(0, 2, 1, 0), true)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), false, duration(0, 2, 1, 0), true)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), true, duration(0, 2, 1, 0), false)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), false, duration(0, 2, 1, 0), false)))
                .hasSize(0);

        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), true, null, false)))
                .hasSize(1);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 0, 1, 0), false, null, false)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", duration(0, 1, 0, 1), true, null, false)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", null, false, duration(0, 0, 1, 0), true)))
                .hasSize(1);
        assertThat(queryNodeIndex(rangeQuery("duration", null, false, duration(0, 0, 1, 0), false)))
                .hasSize(0);
        assertThat(queryNodeIndex(rangeQuery("duration", null, false, duration(0, 1, 0, 1), true)))
                .hasSize(0);
    }
}

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
package org.neo4j.genai.dbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertFalse;

import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

/**
 * A utility class modelled in parts after {@code apoc.util.TestUtil}, so that the tests from Apoc Extended for the
 * database integration could be easier ported.
 * TODO rename to Assertions
 */
final class Helper {

    enum EntityType {
        NODE,
        REL,
        UNDEFINED
    }

    // TODO rename to assertResult
    static void testResult(GraphDatabaseService db, String call, Consumer<Result> resultConsumer) {
        testResult(db, call, null, resultConsumer);
    }

    static void testResult(
            GraphDatabaseService db, String call, Map<String, Object> params, Consumer<Result> resultConsumer) {
        try (Transaction tx = db.beginTx()) {
            Map<String, Object> p = (params == null) ? Map.of() : params;
            Result result = tx.execute(call, p);
            resultConsumer.accept(result);
            tx.commit();
        }
    }

    public static void testCallAssertions(Result res, Consumer<Map<String, Object>> consumer) {
        assertThat(res.hasNext()).as("Should have an element").isTrue();
        Map<String, Object> row = res.next();
        consumer.accept(row);
        assertThat(res.hasNext()).as("Should not have a second element").isFalse();
    }

    static void testCall(
            GraphDatabaseService db, String call, Map<String, Object> params, Consumer<Map<String, Object>> consumer) {
        testResult(db, call, params, (res) -> testCallAssertions(res, consumer));
    }

    static void assertFails(
            GraphDatabaseService db,
            String query,
            Map<String, Object> params,
            Class<? extends Exception> expectedException,
            String expectedMessage) {
        Consumer<Map<String, Object>> emptyResultConsumer = r -> {};
        assertThatExceptionOfType(QueryExecutionException.class)
                .isThrownBy(() -> testCall(db, query, params, emptyResultConsumer))
                .withRootCauseInstanceOf(expectedException)
                .withMessageContaining(expectedMessage);
    }

    static void assertBerlinResult(Map<String, Object> row, EntityType entityType) {
        assertBerlinResult(row, "1", entityType);
    }

    static void assertBerlinResult(Map<String, Object> row, Object id, EntityType entityType) {
        assertThat(row.get("metadata"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsExactlyInAnyOrderEntriesOf(Map.of("city", "Berlin", "foo", "one"));
        assertThat(row).containsEntry("id", id);
        if (!entityType.equals(EntityType.UNDEFINED)) {
            String entity = entityType.equals(EntityType.NODE) ? "node" : "rel";
            Map<String, Object> props = ((Entity) row.get(entity)).getAllProperties();
            assertBerlinProperties(props);
        }
    }

    static void assertLondonResult(Map<String, Object> row, EntityType entityType) {
        assertLondonResult(row, "2", entityType);
    }

    static void assertLondonResult(Map<String, Object> row, Object id, EntityType entityType) {
        assertThat(row.get("metadata"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsExactlyInAnyOrderEntriesOf(Map.of("city", "London", "foo", "two"));
        assertThat(row).containsEntry("id", id);
        if (!entityType.equals(EntityType.UNDEFINED)) {
            String entity = entityType.equals(EntityType.NODE) ? "node" : "rel";
            Map<String, Object> props = ((Entity) row.get(entity)).getAllProperties();
            assertLondonProperties(props);
        }
    }

    private static void assertLondonProperties(Map<String, Object> props) {
        assertThat(props).containsEntry("city", "London");
        assertThat(props).containsEntry("myId", "two");
        assertThat(props).hasEntrySatisfying("vect", v -> assertThat(v).isInstanceOf(float[].class));
    }

    private static void assertBerlinProperties(Map<String, Object> props) {
        assertThat(props).containsEntry("city", "Berlin");
        assertThat(props).containsEntry("myId", "one");
        assertThat(props).hasEntrySatisfying("vect", v -> assertThat(v).isInstanceOf(float[].class));
    }

    public static void assertNodesCreated(GraphDatabaseService db) {
        testResult(db, "MATCH (n:Test) RETURN properties(n) AS props ORDER BY n.myId", Helper::vectorEntityAssertions);
    }

    public static void assertRelsCreated(GraphDatabaseService db) {
        testResult(
                db,
                "MATCH (:Start)-[r:TEST]->(:End) RETURN properties(r) AS props ORDER BY r.myId",
                Helper::vectorEntityAssertions);
    }

    public static void vectorEntityAssertions(Result r) {
        ResourceIterator<Map<String, Object>> propsIterator = r.columnAs("props");
        assertBerlinProperties(propsIterator.next());
        assertLondonProperties(propsIterator.next());

        assertThat(propsIterator).isExhausted();
    }

    public static void assertReadOnlyProcWithMappingResults(Result r, String node, boolean scoreExpected) {
        Map<String, Object> row = r.next();
        Map<String, Object> props = ((Entity) row.get(node)).getAllProperties();
        assertThat(props).containsExactlyInAnyOrderEntriesOf(Map.of("readID", "one"));
        if (scoreExpected) {
            assertThat(row).hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
        }
        assertThat(row).hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

        row = r.next();
        props = ((Entity) row.get(node)).getAllProperties();
        assertThat(props).containsExactlyInAnyOrderEntriesOf(Map.of("readID", "two"));
        if (scoreExpected) {
            assertThat(row).hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
        }
        assertThat(row).hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

        assertFalse(r.hasNext());
    }

    private Helper() {}
}

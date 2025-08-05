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
package org.neo4j.kernel.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.values.storable.CoordinateReferenceSystem.CARTESIAN;
import static org.neo4j.values.storable.Values.durationValue;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.Values;

@ImpermanentDbmsExtension
/*
 This covers a *very* special case of range index over point, durations and arrays of points/durations whereby
 we want to do equality comparisons when we have queries in the form 'y <= x <= y' or `y <= x` or `y >= x`
*/
public class RangeIndexPointAndDurationEqualityEdgeCaseIT {

    @Inject
    GraphDatabaseAPI db;

    @BeforeEach
    void setup() {
        // Create some entities that we should never match on with our test cases
        var nonMatchingProperties = List.of(
                Values.pointValue(CARTESIAN, 100.0, 100.0),
                new PointValue[] {Values.pointValue(CARTESIAN, 100.0, 100.0), Values.pointValue(CARTESIAN, 200.0, 200.0)
                },
                durationValue(durationValue(Duration.ofSeconds(100))),
                new TemporalAmount[] {Duration.ofSeconds(100), Duration.ofSeconds(2000)});

        try (var tx = db.beginTx()) {
            for (var property : nonMatchingProperties) {
                tx.createNode(Label.label("Foo")).setProperty("p", property);
                tx.createNode()
                        .createRelationshipTo(tx.createNode(), RelationshipType.withName("Bar"))
                        .setProperty("p", property);
            }
            tx.commit();
        }
    }

    public static Stream<Arguments> points() {
        return createTestCase(Values.pointValue(CARTESIAN, 1.0, 1.0), "point({x: 1.0, y: 1.0, crs: 'Cartesian'})");
    }

    @ParameterizedTest
    @MethodSource(value = "points")
    void canComparePoints(TestSpec testSpec) {
        testSpec.runTest(db);
    }

    public static Stream<Arguments> pointArrays() {
        var pointArray =
                new PointValue[] {Values.pointValue(CARTESIAN, 1.0, 1.0), Values.pointValue(CARTESIAN, 2.0, 2.0)};
        return createTestCase(
                pointArray, "[point({x: 1.0, y: 1.0, crs: 'Cartesian'}), point({x: 2.0, y: 2.0, crs: 'Cartesian'})]");
    }

    @ParameterizedTest
    @MethodSource(value = "pointArrays")
    void canComparePointArrays(TestSpec testSpec) {
        testSpec.runTest(db);
    }

    public static Stream<Arguments> durations() {
        return createTestCase(durationValue(Duration.ofSeconds(1)), "duration('PT1S')");
    }

    @ParameterizedTest
    @MethodSource(value = "durations")
    void canCompareDurations(TestSpec testSpec) {
        testSpec.runTest(db);
    }

    public static Stream<Arguments> durationArray() {
        return createTestCase(
                new TemporalAmount[] {Duration.ofSeconds(1), Duration.ofSeconds(2)},
                "[duration('PT1S'), duration('PT2S')]");
    }

    @ParameterizedTest
    @MethodSource(value = "durationArray")
    void canCompareDurationArray(TestSpec testSpec) {
        testSpec.runTest(db);
    }

    enum EntityType {
        NODE,
        RELATIONSHIP;

        void createIndex(GraphDatabaseAPI db) {
            try (var tx = db.beginTx()) {
                switch (this) {
                    case NODE ->
                        tx.schema().indexFor(Label.label("Foo")).on("p").create();
                    case RELATIONSHIP ->
                        tx.schema()
                                .indexFor(RelationshipType.withName("Bar"))
                                .on("p")
                                .create();
                }
                tx.commit();
            }

            try (var tx = db.beginTx()) {
                tx.schema().awaitIndexesOnline(1, TimeUnit.MINUTES);
            }
        }

        List<?> createEntities(GraphDatabaseAPI db, Object... properties) {
            var createdEntities = new ArrayList<>(properties.length);
            try (var tx = db.beginTx()) {
                for (var property : properties) {
                    switch (this) {
                        case NODE -> {
                            var node = tx.createNode(Label.label("Foo"));
                            node.setProperty("p", property);
                            createdEntities.add(node);
                        }
                        case RELATIONSHIP -> {
                            var relationship = tx.createNode(Label.label("Foo"))
                                    .createRelationshipTo(
                                            tx.createNode(Label.label("Foo")), RelationshipType.withName("Bar"));
                            relationship.setProperty("p", property);
                            createdEntities.add(relationship);
                        }
                    }
                }
                tx.commit();
            }
            return createdEntities;
        }

        void match(GraphDatabaseAPI db, List<?> createdEntities, String whereClause) {
            try (var tx = db.beginTx()) {
                switch (this) {
                    case NODE -> {
                        var results = tx
                                .execute("match (e: Foo) where %s return e as e".formatted(whereClause))
                                .columnAs("e")
                                .stream()
                                .map(e -> (Node) e)
                                .toList();
                        assertThat(results).containsExactlyInAnyOrderElementsOf((List<Node>) createdEntities);
                    }
                    case RELATIONSHIP -> {
                        var results = tx
                                .execute("match ()-[e: Bar]->() where %s return e as e".formatted(whereClause))
                                .columnAs("e")
                                .stream()
                                .map(e -> (Relationship) e)
                                .toList();
                        assertThat(results).containsExactlyInAnyOrderElementsOf((List<Relationship>) createdEntities);
                    }
                }
            }
        }
    }

    record TestSpec(EntityType entity, String whereClause, Object... values) {
        public TestSpec withEntity(EntityType entityType) {
            return new TestSpec(entityType, whereClause, values);
        }

        void runTest(GraphDatabaseAPI db) {
            var createdEntities = entity.createEntities(db, values);
            // ensure we match without an index to make sure behaviour is consistent
            entity.match(db, createdEntities, whereClause);
            entity.createIndex(db);
            entity.match(db, createdEntities, whereClause);
        }
    }

    private static Stream<Arguments> createTestCase(Object matchingPoint, String vExpr) {
        return Stream.of(
                        // We want to test each of these queries  against  cases in which we have 0, 1, and 2
                        // entities with matching points created in the database.
                        "e.p >= %s".formatted(vExpr),
                        "e.p <= %s".formatted(vExpr),
                        "%s <= e.p <= %s".formatted(vExpr, vExpr))
                .flatMap(whereClause -> Stream.of(
                        new TestSpec(EntityType.NODE, whereClause),
                        new TestSpec(EntityType.NODE, whereClause, matchingPoint),
                        new TestSpec(EntityType.NODE, whereClause, matchingPoint, matchingPoint)))
                .flatMap(spec -> Stream.of(spec, spec.withEntity(EntityType.RELATIONSHIP)))
                .map(Arguments::of);
    }
}

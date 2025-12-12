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
package org.neo4j.cypher.jdbc.mapping.value.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.neo4j.values.storable.Values.NO_VALUE;
import static org.neo4j.values.storable.Values.longValue;
import static org.neo4j.values.storable.Values.stringValue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.cypher.jdbc.mapping.id.GlobalIds;
import org.neo4j.values.AnyValue;
import org.neo4j.values.ElementIdMapper;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;
import org.neo4j.values.virtual.NodeValue;
import org.neo4j.values.virtual.RelationshipValue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JdbcValueMapperTest {

    private Connection connection;

    @BeforeAll
    void openConnection() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @AfterAll
    void closeConnection() throws SQLException {
        if (connection != null) connection.close();
    }

    @Test
    void mapBooleans() throws SQLException {
        final var sql = "select true as v1, false as v2, null as v3";
        singleRow(sql, result -> {
            assertThat(new BooleanMapping(1).toNeo4jValue(result)).isEqualTo(BooleanValue.TRUE);
            assertThat(new BooleanMapping(2).toNeo4jValue(result)).isEqualTo(BooleanValue.FALSE);
            assertThat(new BooleanMapping(3).toNeo4jValue(result)).isEqualTo(NO_VALUE);
        });
    }

    @Test
    void mapStrings() throws SQLException {
        final var sql = "select 'a' as v1, 'b' as v2, null as v3";
        singleRow(sql, result -> {
            assertThat(new StringMapping(1).toNeo4jValue(result)).isEqualTo(stringValue("a"));
            assertThat(new StringMapping(2).toNeo4jValue(result)).isEqualTo(stringValue("b"));
            assertThat(new BooleanMapping(3).toNeo4jValue(result)).isEqualTo(NO_VALUE);
        });
    }

    @Test
    void mapLongs() throws SQLException {
        final var sql = "select 0 as v1, -7 as v2, null as v3";
        singleRow(sql, result -> {
            assertThat(new LongMapping(1).toNeo4jValue(result)).isEqualTo(longValue(0));
            assertThat(new LongMapping(2).toNeo4jValue(result)).isEqualTo(longValue(-7));
            assertThat(new LongMapping(3).toNeo4jValue(result)).isEqualTo(NO_VALUE);
        });
    }

    @Test
    void mapNode() throws SQLException {
        final var sql = """
            select
              'external-id' as id,
              'a' as p1,
              1 as p2,
              null as p3
            """;
        singleRow(sql, result -> {
            final var globalIds = new TestId(Map.of(new TestId.Key("schema-id", "external-id"), 42L), Map.of());
            final var labels = Values.stringArray("A", "B");
            final var propertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "p1_cypher", new StringMapping(2),
                    "p2_cypher", new LongMapping(3),
                    "p3_cypher", new StringMapping(4)));
            final var nodeMapping = new NodeMapping(globalIds, "schema-id", 1, labels, propertiesMapper);

            assertThat(nodeMapping.toNeo4jValue(result))
                    .asInstanceOf(type(NodeValue.class))
                    .hasFieldOrPropertyWithValue("id", 42L)
                    .hasFieldOrPropertyWithValue("elementId", "n::42")
                    .hasFieldOrPropertyWithValue("labels", labels)
                    .hasFieldOrPropertyWithValue(
                            "properties", mapValue(Map.of("p1_cypher", "a", "p2_cypher", 1L, "p3_cypher", NO_VALUE)));
        });
    }

    @Test
    void mapNullNode() throws SQLException {
        final var sql = """
            select
              null as id,
              null as p1,
              null as p2,
              null as p3
            """;
        singleRow(sql, result -> {
            final var globalIds = new TestId(Map.of(), Map.of());
            final var labels = Values.stringArray("A", "B");
            final var propertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "p1_cypher", new StringMapping(2),
                    "p2_cypher", new LongMapping(3),
                    "p3_cypher", new StringMapping(4)));
            final var nodeMapping = new NodeMapping(globalIds, "schema-id", 1, labels, propertiesMapper);

            assertThat(nodeMapping.toNeo4jValue(result)).isEqualTo(NO_VALUE);
        });
    }

    @Test
    void mapRelationship() throws SQLException {
        final var sql = """
            select
              -- relationship
              'external-id' as id,
              'a' as p1,
              1 as p2,
              null as p3,
              -- from node
              'external-from-node-id' as from_node_id,
              'b' as from_node_p1,
              2 as from_node_p2,
              null as from_node_p3,
              -- to node
              'external-to-node-id' as to_node_id,
              'c' as to_node_p1,
              3 as to_node_p2,
              null as to_node_p3
            """;
        singleRow(sql, result -> {
            final var globalIds = new TestId(
                    Map.of(
                            new TestId.Key("from-node-schema", "external-from-node-id"), 1L,
                            new TestId.Key("to-node-schema", "external-to-node-id"), 2L),
                    Map.of(new TestId.Key("rel-schema", "external-id"), 3L));
            final var type = Values.stringValue("R");
            final var propertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "p1_cypher", new StringMapping(2),
                    "p2_cypher", new LongMapping(3),
                    "p3_cypher", new StringMapping(4)));

            final var fromNodeLabels = Values.stringArray("A", "B");
            final var fromNodePropertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "from_node_p1_cypher", new StringMapping(6),
                    "from_node_p2_cypher", new LongMapping(7),
                    "from_node_p3_cypher", new StringMapping(8)));
            final var fromNodeMapping =
                    new NodeMapping(globalIds, "from-node-schema", 5, fromNodeLabels, fromNodePropertiesMapper);

            final var toNodeLabels = Values.stringArray("C");
            final var toNodePropertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "to_node_p1_cypher", new StringMapping(10),
                    "to_node_p2_cypher", new LongMapping(11),
                    "to_node_p3_cypher", new StringMapping(12)));
            final var toNodeMapping =
                    new NodeMapping(globalIds, "to-node-schema", 9, toNodeLabels, toNodePropertiesMapper);

            final var relMapping = new RelationshipMapping(
                    globalIds, "rel-schema", 1, type, propertiesMapper, fromNodeMapping, toNodeMapping);

            assertThat(relMapping.toNeo4jValue(result))
                    .asInstanceOf(type(RelationshipValue.class))
                    .hasFieldOrPropertyWithValue("id", 3L)
                    .hasFieldOrPropertyWithValue("elementId", "r::3")
                    .hasFieldOrPropertyWithValue("type", type)
                    .hasFieldOrPropertyWithValue(
                            "properties", mapValue(Map.of("p1_cypher", "a", "p2_cypher", 1L, "p3_cypher", NO_VALUE)))
                    .hasFieldOrPropertyWithValue("startNodeId", 1L)
                    .hasFieldOrPropertyWithValue("endNodeId", 2L)
                    .satisfies(r -> assertThat(r.startNode())
                            .asInstanceOf(type(NodeValue.class))
                            .hasFieldOrPropertyWithValue("id", 1L)
                            .hasFieldOrPropertyWithValue("elementId", "n::1")
                            .hasFieldOrPropertyWithValue("labels", fromNodeLabels)
                            .hasFieldOrPropertyWithValue(
                                    "properties",
                                    mapValue(Map.of(
                                            "from_node_p1_cypher",
                                            "b",
                                            "from_node_p2_cypher",
                                            2L,
                                            "from_node_p3_cypher",
                                            NO_VALUE))))
                    .satisfies(r -> assertThat(r.endNode())
                            .asInstanceOf(type(NodeValue.class))
                            .hasFieldOrPropertyWithValue("id", 2L)
                            .hasFieldOrPropertyWithValue("elementId", "n::2")
                            .hasFieldOrPropertyWithValue("labels", toNodeLabels)
                            .hasFieldOrPropertyWithValue(
                                    "properties",
                                    mapValue(Map.of(
                                            "to_node_p1_cypher",
                                            "c",
                                            "to_node_p2_cypher",
                                            3L,
                                            "to_node_p3_cypher",
                                            NO_VALUE))));
        });
    }

    @Test
    void mapNullRelationship() throws SQLException {
        final var sql = """
            select
              -- relationship
              null as id,
              null as p1,
              null as p2,
              null as p3,
              -- from node
              null as from_node_id,
              null as from_node_p1,
              null as from_node_p2,
              null as from_node_p3,
              -- to node
              null as to_node_id,
              null as to_node_p1,
              null as to_node_p2,
              null as to_node_p3
            """;
        singleRow(sql, result -> {
            final var globalIds = new TestId(Map.of(), Map.of());
            final var type = Values.stringValue("R");
            final var propertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "p1_cypher", new StringMapping(2),
                    "p2_cypher", new LongMapping(3),
                    "p3_cypher", new StringMapping(4)));

            final var fromNodeLabels = Values.stringArray("A", "B");
            final var fromNodePropertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "from_node_p1_cypher", new StringMapping(6),
                    "from_node_p2_cypher", new LongMapping(7),
                    "from_node_p3_cypher", new StringMapping(8)));
            final var fromNodeMapping =
                    new NodeMapping(globalIds, "from-node-schema", 5, fromNodeLabels, fromNodePropertiesMapper);

            final var toNodeLabels = Values.stringArray("C");
            final var toNodePropertiesMapper = new MultiColumnMapValueMapper(Map.of(
                    "to_node_p1_cypher", new StringMapping(10),
                    "to_node_p2_cypher", new LongMapping(11),
                    "to_node_p3_cypher", new StringMapping(12)));
            final var toNodeMapping =
                    new NodeMapping(globalIds, "to-node-schema", 9, toNodeLabels, toNodePropertiesMapper);

            final var relMapping = new RelationshipMapping(
                    globalIds, "rel-schema", 1, type, propertiesMapper, fromNodeMapping, toNodeMapping);

            assertThat(relMapping.toNeo4jValue(result)).isEqualTo(NO_VALUE);
        });
    }

    private static MapValue mapValue(Map<String, Object> map) {
        final var builder = new MapValueBuilder();
        map.forEach((k, v) -> builder.add(k, v instanceof AnyValue av ? av : Values.of(v)));
        return builder.build();
    }

    private void singleRow(String sql, ThrowingConsumer<ResultSet> assertion) throws SQLException {
        try (final var statement = connection.prepareStatement(sql);
                final var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertion.accept(result);
            assertThat(result.next()).isFalse();
        }
    }
}

record TestId(Map<Key, Long> nodeIds, Map<Key, Long> relIds) implements GlobalIds {
    @Override
    public long nodeId(String schemaId, Object externalId) {
        final var id = nodeIds.get(new Key(schemaId, externalId));
        if (id != null) {
            return id;
        }
        throw new IllegalArgumentException("Id not defined: " + schemaId + " " + externalId);
    }

    @Override
    public long relationshipId(String schemaId, Object externalId) {
        final var id = relIds.get(new Key(schemaId, externalId));
        if (id != null) {
            return id;
        }
        throw new IllegalArgumentException("Id not defined: " + schemaId + " " + externalId);
    }

    @Override
    public ElementIdMapper idMapper() {
        return ElementIdMapper.PLACEHOLDER;
    }

    record Key(String schemaId, Object externalId) {}
}

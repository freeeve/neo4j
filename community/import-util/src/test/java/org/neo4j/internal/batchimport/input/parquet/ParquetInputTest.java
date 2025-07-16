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
package org.neo4j.internal.batchimport.input.parquet;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.neo4j.batchimport.api.input.Collector.EMPTY;
import static org.neo4j.batchimport.api.input.IdType.ACTUAL;
import static org.neo4j.batchimport.api.input.IdType.INTEGER;
import static org.neo4j.batchimport.api.input.IdType.STRING;
import static org.neo4j.internal.helpers.ArrayUtil.union;
import static org.neo4j.internal.helpers.collection.Iterators.asSet;

import blue.strategic.parquet.ParquetWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Types;
import org.assertj.core.api.Assertions;
import org.eclipse.collections.api.factory.Maps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.batchimport.api.InputIterator;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.Input;
import org.neo4j.batchimport.api.input.InputChunk;
import org.neo4j.csv.reader.Configuration;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.internal.batchimport.input.InputEntity;
import org.neo4j.internal.batchimport.input.InputException;
import org.neo4j.internal.helpers.collection.MapUtil;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;
import org.neo4j.token.CreatingTokenHolder;
import org.neo4j.token.ReadOnlyTokenCreator;
import org.neo4j.token.TokenHolders;
import org.neo4j.token.api.NamedToken;
import org.neo4j.token.api.TokenHolder;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.DateValue;
import org.neo4j.values.storable.DurationValue;
import org.neo4j.values.storable.LocalDateTimeValue;
import org.neo4j.values.storable.LocalTimeValue;
import org.neo4j.values.storable.TimeValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.storable.VectorValue;

@TestDirectoryExtension
@ExtendWith(RandomExtension.class)
class ParquetInputTest {

    @Inject
    private RandomSupport random;

    @Inject
    private TestDirectory directory;

    private final InputEntity visitor = new InputEntity();
    private final Groups groups = new Groups();
    private final Group globalGroup = groups.getOrCreate(null);
    private InputChunk chunk;
    private InputIterator referenceData;
    private int fileCounter;

    private static final ParquetMonitor MONITOR = new ParquetMonitor(System.out);

    @AfterEach
    void cleanup() throws IOException {
        fileCounter = 0;
        directory.cleanup();
    }

    @ParameterizedTest
    @MethodSource("groupNames")
    void shouldProvideNodesFromParquetInput(String groupName) throws Exception {
        final var group = groupName == null ? Set.<String>of() : Set.of("");
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "HACKER"}));
        Input input = createParquetInput(
                Map.of(group, List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldProvideNodesFromParquetInputWithHeaderFile() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "USER"}));
        Path headerFile = createHeaderFile(
                List.of(":ID", "name", ":Label"),
                List.of("ignored-column-id", "ignored-column-name", "ignored-column-label"));

        Input input = createParquetInput(
                Map.of(Set.of("HACKER"), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER", "USER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldProvideNodesFromMultipleParquetInputsWithHeaderFile() throws Exception {
        // GIVEN
        Path nodeFile1 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "USER"}));
        Path nodeFile2 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {456L, "SomeoneElse", "USER"}));
        Path headerFile = createHeaderFile(
                List.of(":ID", "name", ":Label"),
                List.of("ignored-column-id", "ignored-column-name", "ignored-column-label"));

        Input input = createParquetInput(
                Map.of(Set.of("HACKER"), List.<Path[]>of(new Path[] {headerFile, nodeFile1, nodeFile2})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER", "USER"));
            assertNextNode(nodes, 456L, properties("name", "SomeoneElse"), labels("HACKER", "USER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldProvideNodesFromMultipleParquetInputsAndDifferentColumnOrderingWithHeaderFile() throws Exception {
        // GIVEN
        Path nodeFile1 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "USER"}));
        Path nodeFile2 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id")),
                List.<Object[]>of(new Object[] {"USER", "SomeoneElse", 456L}));
        Path headerFile = createHeaderFile(
                List.of(":ID", "name", ":Label"),
                List.of("ignored-column-id", "ignored-column-name", "ignored-column-label"));

        Input input = createParquetInput(
                Map.of(Set.of("HACKER"), List.<Path[]>of(new Path[] {headerFile, nodeFile1, nodeFile2})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER", "USER"));
            assertNextNode(nodes, 456L, properties("name", "SomeoneElse"), labels("HACKER", "USER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldProvideNodesFromParquetInputWithHeaderFileReducedColumns() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "USER"}));
        Path headerFile =
                createHeaderFile(List.of(":ID", ":Label"), List.of("ignored-column-id", "ignored-column-label"));

        Input input = createParquetInput(
                Map.of(Set.of("HACKER"), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties(), labels("HACKER", "USER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldOnlyApplyHeadersInTheSameNodeGroup() throws Exception {
        // GIVEN
        Path nodeFile1 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":Label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "HACKER"}));

        Path nodeFile2 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":Label")),
                List.<Object[]>of(new Object[] {456L, "SomeoneElse", "HACKER"}));

        Path headerFile = createHeaderFile(List.of(":ID", "new_name", ":Label"), List.of(":ID", "name", ":Label"));

        Input input = createParquetInput(
                Map.of(
                        Set.of(),
                        List.<Path[]>of(new Path[] {nodeFile1}),
                        Set.of("somethingElse"),
                        List.<Path[]>of(new Path[] {headerFile, nodeFile2})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            List<InputEntity.Property> allProperties = new ArrayList<>();
            readNext(nodes);
            allProperties.addAll(visitor.properties);
            readNext(nodes);
            allProperties.addAll(visitor.properties);

            assertThat(allProperties)
                    .satisfiesExactlyInAnyOrder(
                            propertyNode1 -> {
                                assertThat(propertyNode1.asValue()).isEqualTo(Values.stringValue("Mattias Persson"));
                                assertThat(propertyNode1.key()).isEqualTo("name");
                            },
                            propertyNode2 -> {
                                assertThat(propertyNode2.asValue()).isEqualTo(Values.stringValue("SomeoneElse"));
                                assertThat(propertyNode2.key()).isEqualTo("new_name");
                            });
        }
    }

    @Test
    void failIfNodeHeaderFileIsNotFirstFile() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":Label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "HACKER"}));
        Path headerFile = createHeaderFile(List.of("should_not_be_applied"), List.of("name"));

        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(), List.<Path[]>of(new Path[] {nodeFile, headerFile})),
                        Map.of(),
                        INTEGER,
                        groups,
                        MONITOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "CSV header file for parquet data import must appear only once, as the first entry");
    }

    @Test
    void failIfHeaderHasMoreThanTwoRows() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "HACKER"}));
        Path headerFile = directory.file("header.csv");
        try (var writer = new BufferedWriter(new FileWriter(headerFile.toFile()))) {
            writer.write(":ID,name,:Label");
            writer.newLine();
            writer.write("ignored-column-id,ignored-column-name,ignored-column-label");
            writer.newLine();
            writer.write("idkid,idkname,idklabel");
            writer.newLine();
        }

        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                        Map.of(),
                        INTEGER,
                        groups,
                        MONITOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The header is expected to have one or two lines");
    }

    @Test
    void failIfHeaderIsEmptyOrBlank() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":Label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "HACKER"}));
        Path headerFile = directory.file("header.csv");
        try (var writer = new BufferedWriter(new FileWriter(headerFile.toFile()))) {
            writer.newLine();
        }

        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                        Map.of(),
                        INTEGER,
                        groups,
                        MONITOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The header definition is empty");
    }

    @Test
    void shouldProvideNodesFromParquetInputWithSingleLineHeaderFile() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ignored-column-id"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("ignored-column-label")),
                List.<Object[]>of(new Object[] {123L, "Mattias Persson", "USER"}));
        Path headerFile = createHeaderFile(List.of(":ID", "name", ":Label"), List.of());

        Input input = createParquetInput(
                Map.of(Set.of("HACKER"), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER", "USER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldStoreIdAsPropertyInSpecificValueTypeWithHeader() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("notid"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("notprop")),
                List.<Object[]>of(new Object[] {123, "val"}));
        Path headerFile = createHeaderFile(List.of("id:ID{id-type:int}", "prop"), List.of());
        try (var input = createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                        Map.of(),
                        STRING,
                        groups,
                        new ParquetMonitor(System.out));
                var nodes = input.nodes(EMPTY).iterator()) {
            // then
            assertNextNode(nodes, 123, properties("id", 123, "prop", "val"), labels());
            assertFalse(readNext(nodes));
        }
    }

    @ParameterizedTest
    @MethodSource("listTypes")
    void shouldReadListTypes(String fileName, List<?> expectedList) throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/" + fileName);
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("aList", expectedList, "name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadListTypesWithSingleEntry() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/list_single.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("aList", List.of("a"), "name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadListTypesWithNoEntry() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/list_empty.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("aList", List.of(), "name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadListTypesWithNullEntry() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/list_null.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadMapTypes() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes,
                    123L,
                    properties("aMap.a", "aa", "aMap.b", "bb", "name", "Mattias Persson"),
                    labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadNumericMapTypes() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map_numeric.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes, 123L, properties("aMap.a", 1L, "aMap.b", 23L, "name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadMultipleMapTypes() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map_multiple.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes,
                    123L,
                    properties(
                            "aMap.a",
                            "aa",
                            "aMap.b",
                            "bb",
                            "bMap.x",
                            "xx",
                            "bMap.y",
                            "yy",
                            "cMap.c",
                            "cc",
                            "name",
                            "Mattias Persson"),
                    labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadMapTypesWithNoEntry() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map_empty.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadMapTypesWithNullEntry() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map_null.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadMapTypesWithSingleEntry() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map_single.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 123L, properties("aMap.x", "abcd", "name", "Mattias Persson"), labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldFailOnDuplicatedNamePrefix() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/map_duplicate_names.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        // WHEN/THEN
        try {
            Input input = createParquetInput(
                    Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
            fail("Should have failed");
        } catch (DuplicatedColumnException e) {
            // THEN
            assertThat(e).hasMessageContaining("map_duplicate_names.parquet");
        }
    }

    @Test
    void shouldReadStructTypes() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/struct.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        System.out.println(nodeFile.toAbsolutePath());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes,
                    123L,
                    properties("aStruct.a", "aa", "aStruct.b", "bb", "name", "Mattias Persson"),
                    labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @Test
    void shouldReadMultipleStructTypes() throws Exception {
        // GIVEN
        var fileUrl = getClass().getResource("/parquet/struct_multiple.parquet");
        var nodeFile = Path.of(fileUrl.toURI());
        System.out.println(nodeFile.toAbsolutePath());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes,
                    123L,
                    properties(
                            "aStruct.a",
                            "aa",
                            "aStruct.b",
                            "bb",
                            "name",
                            "Mattias Persson",
                            "bStruct.x",
                            "xx",
                            "bStruct.y",
                            12),
                    labels("HACKER"));
            assertFalse(chunk.next(visitor));
        }
    }

    @ParameterizedTest
    @MethodSource("groupNames")
    void shouldProvideRelationshipsFromParquetInput(String groupName) throws Exception {
        // GIVEN
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("since")),
                List.of(
                        new Object[] {"node1", "node2", "KNOWS", 1234567L},
                        new Object[] {"node2", "node10", "HACKS", 987654L}));
        Input input = createParquetInput(
                Map.of(),
                Maps.mutable.of(groupName, List.<Path[]>of(new Path[] {relationshipFile})),
                STRING,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertNextRelationship(relationships, "node1", "node2", "KNOWS", properties("since", 1234567L));
            assertNextRelationship(relationships, "node2", "node10", "HACKS", properties("since", 987654L));
        }
    }

    @Test
    void shouldProvideRelationshipsFromParquetInputWithHeaderFile() throws Exception {
        // GIVEN
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("notstartid"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("notendid"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("nottype"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("notsince")),
                List.of(
                        new Object[] {"node1", "node2", "KNOWS", 1234567L},
                        new Object[] {"node2", "node10", "HACKS", 987654L}));

        Path headerFile = createHeaderFile(
                List.of(":START_ID", ":END_ID", ":Type", "since"),
                List.of("notstartid", "notendid", "nottype", "notsince"));
        Input input = createParquetInput(
                Map.of(),
                Map.of("", List.<Path[]>of(new Path[] {headerFile, relationshipFile})),
                STRING,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertNextRelationship(relationships, "node1", "node2", "KNOWS", properties("since", 1234567L));
            assertNextRelationship(relationships, "node2", "node10", "HACKS", properties("since", 987654L));
        }
    }

    @Test
    void shouldProvideRelationshipsFromParquetInputWithHeaderFileReducedColumns() throws Exception {
        // GIVEN
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("notstartid"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("notendid"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("nottype"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("notsince")),
                List.of(
                        new Object[] {"node1", "node2", "KNOWS", 1234567L},
                        new Object[] {"node2", "node10", "HACKS", 987654L}));

        Path headerFile = createHeaderFile(
                List.of(":START_ID", ":END_ID", ":Type"), List.of("notstartid", "notendid", "nottype"));
        Input input = createParquetInput(
                Map.of(),
                Map.of("", List.<Path[]>of(new Path[] {headerFile, relationshipFile})),
                STRING,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertNextRelationship(relationships, "node1", "node2", "KNOWS", properties());
            assertNextRelationship(relationships, "node2", "node10", "HACKS", properties());
        }
    }

    @Test
    void shouldOnlyApplyHeadersInTheSameRelationshipGroup() throws Exception {
        // GIVEN
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("since")),
                List.of(
                        new Object[] {"node1", "node2", "KNOWS", 1234567L},
                        new Object[] {"node2", "node10", "HACKS", 987654L}));

        Path headerFile = createHeaderFile(
                List.of(":START_ID", ":END_ID", ":Type"), List.of("notstartid", "notendid", "nottype"));
        Input input = createParquetInput(
                Map.of(),
                Map.of("", List.<Path[]>of(new Path[] {relationshipFile}), "ignore_me", List.<Path[]>of(new Path[] {
                    headerFile
                })),
                STRING,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertNextRelationship(relationships, "node1", "node2", "KNOWS", properties("since", 1234567L));
            assertNextRelationship(relationships, "node2", "node10", "HACKS", properties("since", 987654L));
        }
    }

    @Test
    void failIfRelationshipHeaderFileIsNotFirstFile() throws Exception {
        // GIVEN
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("since")),
                List.of(
                        new Object[] {"node1", "node2", "KNOWS", 1234567L},
                        new Object[] {"node2", "node10", "HACKS", 987654L}));

        Path headerFile = createHeaderFile(
                List.of(":START_ID", ":END_ID", ":Type"), List.of("notstartid", "notendid", "nottype"));
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(),
                        Map.of("", List.<Path[]>of(new Path[] {relationshipFile, headerFile})),
                        STRING,
                        groups,
                        MONITOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "CSV header file for parquet data import must appear only once, as the first entry");
    }

    @Test
    void shouldHandleMultipleInputGroups() throws Exception {
        // GIVEN multiple input groups, each with their own, specific, header
        Path nodeFile1 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("kills"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("health")),
                List.of(new Object[] {"1", "Jim", 10, 100}, new Object[] {"2", "Abathur", 0, 200}));
        Path nodeFile2 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("type")),
                List.of(new Object[] {"3", "zergling"}, new Object[] {"4", "csv"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                Map.of(),
                STRING,
                groups,
                MONITOR);
        // WHEN iterating over them, THEN the expected data should come out
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, "1", properties("name", "Jim", "kills", 10, "health", 100), labels());
            assertNextNode(nodes, "2", properties("name", "Abathur", "kills", 0, "health", 200), labels());
            assertNextNode(nodes, "3", properties("type", "zergling"), labels());
            assertNextNode(nodes, "4", properties("type", "csv"), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldProvideAdditiveLabels() throws Exception {
        // GIVEN
        String[] addedLabels = {"Two", "AddTwo"};
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of(new Object[] {0, "First", ""}, new Object[] {1, "Second", "One"}, new Object[] {
                    2, "Third", "One;Two"
                }));
        Input input = createParquetInput(
                Map.of(Set.of(addedLabels), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 0L, properties("name", "First"), labels(addedLabels));
            assertNextNode(nodes, 1L, properties("name", "Second"), labels(union(new String[] {"One"}, addedLabels)));
            assertNextNode(nodes, 2L, properties("name", "Third"), labels(union(new String[] {"One"}, addedLabels)));
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldProvideDefaultRelationshipType() throws Exception {
        // GIVEN
        String defaultType = "DEFAULT";
        String customType = "CUSTOM";
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE")),
                List.of(new Object[] {0, 1, ""}, new Object[] {1, 2, customType}, new Object[] {2, 1, defaultType}));
        Input input = createParquetInput(
                Map.of(),
                Map.of(defaultType, List.<Path[]>of(new Path[] {relationshipFile})),
                INTEGER,
                groups,
                MONITOR);

        // WHEN/THEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertNextRelationship(relationships, 0L, 1L, defaultType, emptyMap());
            assertNextRelationship(relationships, 1L, 2L, customType, emptyMap());
            assertNextRelationship(relationships, 2L, 1L, defaultType, emptyMap());
            assertFalse(readNext(relationships));
        }
    }

    @Test
    void shouldAllowNodesWithoutIdHeader() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("level")),
                List.of(new Object[] {"Mattias", 1}, new Object[] {"Johan", 2}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), STRING, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, null, null, properties("name", "Mattias", "level", 1), labels());
            assertNextNode(nodes, null, null, properties("name", "Johan", "level", 2), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldAllowSomeNodesToBeAnonymous() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("level")),
                List.of(new Object[] {"abc", "Mattias", 1}, new Object[] {null, "Johan", 2}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), STRING, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, "abc", properties("name", "Mattias", "level", 1), labels());
            assertNextNode(nodes, null, null, properties("name", "Johan", "level", 2), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldAllowNodesToBeAnonymousEvenIfIdHeaderIsNamed() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("id:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("level")),
                List.of(new Object[] {"abc", "Mattias", 1}, new Object[] {null, "Johan", 2}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), STRING, groups, MONITOR);

        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, "abc", properties("id", "abc", "name", "Mattias", "level", 1), labels());
            assertNextNode(nodes, null, null, properties("name", "Johan", "level", 2), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldNotHaveIdSetAsPropertyIfIdHeaderEntryIsNamedForActualIds() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("myId:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("level")),
                List.of(new Object[] {0, "Mattias", 1}, new Object[] {1, "Johan", 2}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), ACTUAL, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, null, 0L, properties("name", "Mattias", "level", 1), labels());
            assertNextNode(nodes, null, 1L, properties("name", "Johan", "level", 2), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldIgnoreNullPropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("extra")),
                List.of(new Object[] {0, "Mattias", null}, new Object[] {1, "Johan", "Additional"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, 0L, properties("name", "Mattias"), labels());
            assertNextNode(nodes, 1L, properties("name", "Johan", "extra", "Additional"), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldIgnoreEmptyPropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("extra")),
                List.of(new Object[] {0, "Mattias", ""}, new Object[] {1, "Johan", "Additional"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, 0L, properties("name", "Mattias"), labels());
            assertNextNode(nodes, 1L, properties("name", "Johan", "extra", "Additional"), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParsePointPropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("point:Point")),
                List.of(
                        new Object[] {0, "Mattias", "{x: 2.7, y:3.2 }"},
                        new Object[] {1, "Johan", " { height :0.01 ,longitude:5, latitude : -4.2 } "}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties(
                            "name",
                            "Mattias",
                            "point",
                            Values.pointValue(CoordinateReferenceSystem.CARTESIAN, 2.7, 3.2)),
                    labels());
            assertNextNode(
                    nodes,
                    1L,
                    properties(
                            "name",
                            "Johan",
                            "point",
                            Values.pointValue(CoordinateReferenceSystem.WGS_84_3D, 5, -4.2, 0.01)),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldNotParsePointPropertyValuesWithDuplicateKeys() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("point:Point")),
                List.<Object[]>of(
                        new Object[] {0, "Johan", " { height :0.01 ,longitude:5, latitude : -4.2, latitude : 4.2 } "}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            readNext(nodes);
            fail("Should have failed when key assigned multiple times, but didn't.");
        } catch (InputException ignore) {
            // this is fine
        }
    }

    @Test
    void shouldParsePointPropertyValuesWithCRSInHeader() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("point:Point{crs:WGS-84-3D}")),
                List.<Object[]>of(new Object[] {0, "Johan", " { height :0.01 ,longitude:5, latitude : -4.2 } "}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties(
                            "name",
                            "Johan",
                            "point",
                            Values.pointValue(CoordinateReferenceSystem.WGS_84_3D, 5, -4.2, 0.01)),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldUseHeaderInformationToParsePoint() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("point:Point{crs:WGS-84}")),
                List.<Object[]>of(new Object[] {0, "Johan", " { x :1 ,y:2 } "}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties("name", "Johan", "point", Values.pointValue(CoordinateReferenceSystem.WGS_84, 1, 2)),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseDatePropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("date:Date")),
                List.of(new Object[] {0, "Mattias", "2018-02-27"}, new Object[] {1, "Johan", "2018-03-01"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, 0L, properties("name", "Mattias", "date", DateValue.date(2018, 2, 27)), labels());
            assertNextNode(nodes, 1L, properties("name", "Johan", "date", DateValue.date(2018, 3, 1)), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseDatePropertyIntegerValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.INT32).named("date:Date")),
                List.<Object[]>of(new Object[] {0, "Mattias", 13193}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(nodes, 0L, properties("name", "Mattias", "date", DateValue.date(2006, 2, 14)), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseDateTimePropertyLongValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.INT64).named("date:LocalDateTime")),
                List.<Object[]>of(new Object[] {0, "Mattias", 1116975273000000L}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            // 2005-05-24 22:54:33 1116975273000000
            assertNextNode(
                    nodes,
                    0L,
                    properties("name", "Mattias", "date", LocalDateTimeValue.localDateTime(2005, 5, 24, 22, 54, 33, 0)),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseTimePropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("time:Time")),
                List.of(new Object[] {0, "Mattias", "13:37"}, new Object[] {1, "Johan", "16:20:01"}, new Object[] {
                    2, "Bob", "07:30-05:00"
                }));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes, 0L, properties("name", "Mattias", "time", TimeValue.time(13, 37, 0, 0, "+00:00")), labels());
            assertNextNode(
                    nodes, 1L, properties("name", "Johan", "time", TimeValue.time(16, 20, 1, 0, "+00:00")), labels());
            assertNextNode(
                    nodes, 2L, properties("name", "Bob", "time", TimeValue.time(7, 30, 0, 0, "-05:00")), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseTimePropertyValuesWithTimezoneInHeader() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("time:Time{timezone:+02:00}")),
                List.of(new Object[] {0, "Mattias", "13:37"}, new Object[] {1, "Johan", "16:20:01"}, new Object[] {
                    2, "Bob", "07:30-05:00"
                }));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes, 0L, properties("name", "Mattias", "time", TimeValue.time(13, 37, 0, 0, "+02:00")), labels());
            assertNextNode(
                    nodes, 1L, properties("name", "Johan", "time", TimeValue.time(16, 20, 1, 0, "+02:00")), labels());
            assertNextNode(
                    nodes, 2L, properties("name", "Bob", "time", TimeValue.time(7, 30, 0, 0, "-05:00")), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseDateTimePropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("time:DateTime")),
                List.of(
                        new Object[] {0, "Mattias", "2018-02-27T13:37"},
                        new Object[] {1, "Johan", "2018-03-01T16:20:01"},
                        new Object[] {2, "Bob", "1981-05-11T07:30-05:00"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties("name", "Mattias", "time", DateTimeValue.datetime(2018, 2, 27, 13, 37, 0, 0, "+00:00")),
                    labels());
            assertNextNode(
                    nodes,
                    1L,
                    properties("name", "Johan", "time", DateTimeValue.datetime(2018, 3, 1, 16, 20, 1, 0, "+00:00")),
                    labels());
            assertNextNode(
                    nodes,
                    2L,
                    properties("name", "Bob", "time", DateTimeValue.datetime(1981, 5, 11, 7, 30, 0, 0, "-05:00")),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseDateTimePropertyValuesWithTimezoneInHeader() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("time:DateTime{timezone:Europe/Stockholm}")),
                List.of(
                        new Object[] {0, "Mattias", "2018-02-27T13:37"},
                        new Object[] {1, "Johan", "2018-03-01T16:20:01"},
                        new Object[] {2, "Bob", "1981-05-11T07:30-05:00"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties(
                            "name",
                            "Mattias",
                            "time",
                            DateTimeValue.datetime(2018, 2, 27, 13, 37, 0, 0, "Europe/Stockholm")),
                    labels());
            assertNextNode(
                    nodes,
                    1L,
                    properties(
                            "name",
                            "Johan",
                            "time",
                            DateTimeValue.datetime(2018, 3, 1, 16, 20, 1, 0, "Europe/Stockholm")),
                    labels());
            assertNextNode(
                    nodes,
                    2L,
                    properties("name", "Bob", "time", DateTimeValue.datetime(1981, 5, 11, 7, 30, 0, 0, "-05:00")),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseLocalTimePropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("time:LocalTime")),
                List.of(new Object[] {0, "Mattias", "13:37"}, new Object[] {1, "Johan", "16:20:01"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes, 0L, properties("name", "Mattias", "time", LocalTimeValue.localTime(13, 37, 0, 0)), labels());
            assertNextNode(
                    nodes, 1L, properties("name", "Johan", "time", LocalTimeValue.localTime(16, 20, 1, 0)), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseLocalDateTimePropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("time:LocalDateTime")),
                List.of(new Object[] {0, "Mattias", "2018-02-27T13:37"}, new Object[] {1, "Johan", "2018-03-01T16:20:01"
                }));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties("name", "Mattias", "time", LocalDateTimeValue.localDateTime(2018, 2, 27, 13, 37, 0, 0)),
                    labels());
            assertNextNode(
                    nodes,
                    1L,
                    properties("name", "Johan", "time", LocalDateTimeValue.localDateTime(2018, 3, 1, 16, 20, 1, 0)),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldParseDurationPropertyValues() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("duration:Duration")),
                List.of(new Object[] {0, "Mattias", "P3MT13H37M"}, new Object[] {1, "Johan", "P-1YT4H20M"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            // THEN
            assertNextNode(
                    nodes,
                    0L,
                    properties("name", "Mattias", "duration", DurationValue.duration(3, 0, 13 * 3600 + 37 * 60, 0)),
                    labels());
            assertNextNode(
                    nodes,
                    1L,
                    properties("name", "Johan", "duration", DurationValue.duration(-12, 0, 4 * 3600 + 20 * 60, 0)),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldHaveNodesBelongToGroupSpecifiedInHeader() throws Exception {
        // GIVEN
        Group group = groups.getOrCreate("MyGroup");
        String idHeader = ":ID(%s)".formatted(group.name());
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(idHeader),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name")),
                List.of(new Object[] {123, "one"}, new Object[] {456, "two"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, group, 123L, properties("name", "one"), labels());
            assertNextNode(nodes, group, 456L, properties("name", "two"), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void multipleIdColumnsRequireStringIdType() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part1:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part2:ID")),
                List.of(new Object[] {123, 456}, new Object[] {3, 6}));
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Having multiple :ID columns requires idType: STRING");
    }

    @Test
    void shouldHandleMultipleNodeIdColumnsWithSameExplicitGroup() throws Exception {
        Group group = groups.getOrCreate("MyGroup");
        String idHeader = ":ID(%s)".formatted(group.name());
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part1%s".formatted(idHeader)),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part2%s".formatted(idHeader))),
                List.of(new Object[] {123, 456}, new Object[] {3, 6}));
        var input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), STRING, groups, MONITOR);
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes,
                    group,
                    "123%s456".formatted(ParquetInput.DELIMITER),
                    properties("part1", 123, "part2", 456),
                    labels());
            assertNextNode(
                    nodes,
                    group,
                    "3%s6".formatted(ParquetInput.DELIMITER),
                    properties("part1", 3, "part2", 6),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldNotFailWithDifferentIdsCombinedToVirtuallyTheSameId() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part1:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part2:ID")),
                List.of(new Object[] {123, 456}, new Object[] {1234, 56}));
        var input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), STRING, groups, MONITOR);
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(
                    nodes,
                    "123%s456".formatted(ParquetInput.DELIMITER),
                    properties("part1", 123, "part2", 456),
                    labels());
            assertNextNode(
                    nodes,
                    "1234%s56".formatted(ParquetInput.DELIMITER),
                    properties("part1", 1234, "part2", 56),
                    labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void multipleNodeIdColumnsRequireSameGroup() throws Exception {
        Group group1 = groups.getOrCreate("MyGroup1");
        Group group2 = groups.getOrCreate("MyGroup2");
        String idHeader1 = ":ID(%s)".formatted(group1.name());
        String idHeader2 = ":ID(%s)".formatted(group2.name());
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part1%s".formatted(idHeader1)),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("part2%s".formatted(idHeader2))),
                List.of(new Object[] {123, 456}, new Object[] {3, 6}));
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), STRING, groups, MONITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("There are multiple :ID columns, but they are referring to different groups");
    }

    @Test
    void shouldHaveRelationshipsSpecifyStartEndNodeIdGroupsInHeader() throws Exception {
        var startGroupName = "StartGroup";
        var endGroupName = "EndGroup";
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                                .named(":START_ID(%s)".formatted(startGroupName)),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                                .named(":END_ID(%s)".formatted(endGroupName))),
                List.of(new Object[] {123, "TYPE", 234}, new Object[] {345, "TYPE", 456}));
        Path nodeFile1 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                        .named(":ID(%s)".formatted(startGroupName))),
                List.of());
        Path nodeFile2 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID(%s)".formatted(endGroupName))),
                List.of());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                INTEGER,
                groups,
                MONITOR);
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertRelationship(relationships, startGroupName, 123L, endGroupName, 234L, "TYPE", properties());
            assertRelationship(relationships, startGroupName, 345L, endGroupName, 456L, "TYPE", properties());
            assertFalse(readNext(relationships));
        }
    }

    @Test
    void shouldCorrectlyAssignCombinedIdsFromNodesToRelationships() throws Exception {
        var groupName = "aGroup";
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                                .named("id1:START_ID(%s)".formatted(groupName)),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                                .named("id2:START_ID(%s)".formatted(groupName)),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                                .named("id3:END_ID(%s)".formatted(groupName)),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32)
                                .named("id4:END_ID(%s)".formatted(groupName))),
                List.of(new Object[] {123, 333, "TYPE", 234, 444}, new Object[] {345, 555, "TYPE", 456, 666}));
        Path nodeFile1 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("id1:ID(%s)".formatted(groupName)),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("id2:ID(%s)".formatted(groupName))),
                List.of(new Object[] {123, 333}, new Object[] {345, 555}));
        Path nodeFile2 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("id3:ID(%s)".formatted(groupName)),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("id4:ID(%s)".formatted(groupName))),
                List.of(new Object[] {234, 444}, new Object[] {456, 666}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                STRING,
                groups,
                MONITOR);
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertRelationship(
                    relationships,
                    groupName,
                    "123%c333".formatted(ParquetInput.DELIMITER),
                    groupName,
                    "234%c444".formatted(ParquetInput.DELIMITER),
                    "TYPE",
                    properties());
            assertRelationship(
                    relationships,
                    groupName,
                    "345%c555".formatted(ParquetInput.DELIMITER),
                    groupName,
                    "456%c666".formatted(ParquetInput.DELIMITER),
                    "TYPE",
                    properties());
            assertFalse(readNext(relationships));
        }
    }

    @Test
    void shouldDoWithoutRelationshipTypeHeaderIfDefaultSupplied() throws Exception {
        // GIVEN relationship data w/o :TYPE column
        String defaultType = "HERE";
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name")),
                List.of(new Object[] {0, 1, "First"}, new Object[] {2, 3, "Second"}));
        Path nodeFile = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID")), List.of());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(defaultType, List.<Path[]>of(new Path[] {relationshipFile})),
                INTEGER,
                groups,
                MONITOR);
        // WHEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            // THEN
            assertNextRelationship(relationships, 0L, 1L, defaultType, properties("name", "First"));
            assertNextRelationship(relationships, 2L, 3L, defaultType, properties("name", "Second"));
            assertFalse(readNext(relationships));
        }
    }

    @Test
    void shouldIgnoreNodeEntriesMarkedIgnoreUsingHeader() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name:IGNORE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("other:int"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of(
                        new Object[] {1, "Mattias", "10", "Person"},
                        new Object[] {2, "Johan", "111", "Person"},
                        new Object[] {3, "Emil", "12", "Person"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        // WHEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("other", 10), labels("Person"));
            assertNextNode(nodes, 2L, properties("other", 111), labels("Person"));
            assertNextNode(nodes, 3L, properties("other", 12), labels("Person"));
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldIgnoreRelationshipEntriesMarkedIgnoreUsingHeader() throws Exception {
        // GIVEN
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("prop:IGNORE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("other:int")),
                List.of(
                        new Object[] {1, "KNOWS", 2, "Mattias", "10"},
                        new Object[] {2, "KNOWS", 3, "Johan", "111"},
                        new Object[] {3, "KNOWS", 4, "Emil", "12"}));
        Path nodeFile = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID")), List.of());
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                INTEGER,
                new Groups(),
                MONITOR);

        // WHEN
        try (InputIterator relationships = input.relationships(EMPTY).iterator()) {
            assertNextRelationship(relationships, 1L, 2L, "KNOWS", properties("other", 10));
            assertNextRelationship(relationships, 2L, 3L, "KNOWS", properties("other", 111));
            assertNextRelationship(relationships, 3L, 4L, "KNOWS", properties("other", 12));
            assertFalse(readNext(relationships));
        }
    }

    @Test
    void shouldUseOverriddenArrayDelimiterWithSpecialCharacter() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("prop:int[]")),
                Collections.singletonList(new Object[] {1, "1?23"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR,
                Configuration.newBuilder().withArrayDelimiter('?').build());

        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("prop", new int[] {1, 23}), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldUseOverriddenArrayDelimiterWithSpecialCharacterForMultipleLabels() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                Collections.singletonList(new Object[] {1, "Foo?Bar"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR,
                Configuration.newBuilder().withArrayDelimiter('?').build());

        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties(), labels("Foo", "Bar"));
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldNotIncludeEmptyArraysInEntities() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("sprop:String[]"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("lprop:long[]")),
                List.of(new Object[] {1, "", ""}, new Object[] {2, "a;b", "10;20"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, emptyMap(), labels());
            assertNextNode(
                    nodes, 2L, properties("sprop", new String[] {"a", "b"}, "lprop", new long[] {10, 20}), labels());
            assertFalse(readNext(nodes));
        }
    }

    static Stream<Arguments> shouldImportVectors() {
        return Stream.of(
                Arguments.of(
                        "vector{coordinateType:byte,dimensions:2}", "1;23", Values.int8Vector((byte) 1, (byte) 23)),
                Arguments.of(
                        "vector{coordinateType:short,dimensions:2}", "1;23", Values.int16Vector((short) 1, (short) 23)),
                Arguments.of("vector{coordinateType:int,dimensions:2}", "1;23", Values.int32Vector(1, 23)),
                Arguments.of("vector{coordinateType:long,dimensions:2}", "1;23", Values.int64Vector(1, 23)),
                Arguments.of("vector{coordinateType:float,dimensions:2}", "1;23", Values.float32Vector(1, 23)),
                Arguments.of("vector{coordinateType:double,dimensions:2}", "1;23", Values.float64Vector(1, 23)));
    }

    @ParameterizedTest
    @MethodSource
    void shouldImportVectors(String header, String stringValue, VectorValue expectedValue) throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:%s".formatted(header))),
                Collections.singletonList(new Object[] {1, stringValue}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("vprop", expectedValue), labels());
            assertFalse(readNext(nodes));
        }
    }

    @ParameterizedTest
    @MethodSource("shouldImportVectors")
    void shouldImportVectorsWithHeaderFiles(String header, String stringValue, VectorValue expectedValue)
            throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop")),
                Collections.singletonList(new Object[] {1, stringValue}));
        Path headerFile = createHeaderFile(List.of(":ID", "vprop:" + header), List.of(":ID", "vprop"), ";");
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {headerFile, nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR,
                Configuration.newBuilder().withDelimiter(';').build());

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("vprop", expectedValue), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldNotUseOverriddenArrayDelimiterForVectors() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{coordinateType:int,dimensions:2}")),
                Collections.singletonList(new Object[] {1, "1;23"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR,
                Configuration.newBuilder().withArrayDelimiter('§').build());

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("vprop", Values.int32Vector(1, 23)), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldUseOverriddenVectorDelimiter() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{coordinateType:int,dimensions:2}")),
                Collections.singletonList(new Object[] {1, "1§23"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                INTEGER,
                groups,
                MONITOR,
                Configuration.newBuilder().withVectorDelimiter('§').build());

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("vprop", Values.int32Vector(1, 23)), labels());
            assertFalse(readNext(nodes));
        }
    }

    private static Stream<Arguments> dimensionMismatchedVectors() {
        return Stream.of(
                Arguments.of("vector{coordinateType:byte,dimensions:3}", "123;-2"),
                Arguments.of("vector{coordinateType:short,dimensions:3}", "123;-2"),
                Arguments.of("vector{coordinateType:int,dimensions:3}", "123;-2"),
                Arguments.of("vector{coordinateType:long,dimensions:3}", "123;-2"),
                Arguments.of("vector{coordinateType:float,dimensions:3}", "123;-2"),
                Arguments.of("vector{coordinateType:double,dimensions:3}", "123;-2"));
    }

    @ParameterizedTest
    @MethodSource("dimensionMismatchedVectors")
    void shouldFailImportOnVectorDataDimensionMismatch(String header, String stringValue) throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:%s".formatted(header))),
                Collections.singletonList(new Object[] {1, stringValue}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("Header specified 3 dimensions, but vector has 2 dimensions");
        }
    }

    @Test
    void shouldFailImportOnVectorDataDimensionMissing() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{coordinateType:int}")),
                Collections.singletonList(new Object[] {1, "1;23"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("vector must specify dimensions");
        }
    }

    @Test
    void shouldFailImportOnVectorDataCoordinateTypeMissing() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions:2}")),
                Collections.singletonList(new Object[] {1, "1;23"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("vector must specify coordinate type");
        }
    }

    @Test
    void shouldFailImportOnVectorDataDimensionAndCoordinateTypeMissing() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector")),
                Collections.singletonList(new Object[] {1, "1;23"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("vector must specify");
        }
    }

    @Test
    void shouldFailImportOnVectorDataCoordinateTypeSpecifiedTwice() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{coordinateType:byte, coordinateType:int}")),
                Collections.singletonList(new Object[] {1, "1;23"}));
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR))
                .hasMessageContaining("Duplicate field 'coordinateType'");
    }

    @Test
    void shouldFailImportOnVectorDataCoordinateTypeSpecifiedTwiceWithDifferentCasing() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{coordinateType:byte, coordinatetype:int}")),
                Collections.singletonList(new Object[] {1, "1;23"}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("Duplicate field 'coordinateType'");
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "vector{coordinateType:byte,dimensions:3}",
                "vector{coordinateType:short,dimensions:3}",
                "vector{coordinateType:int,dimensions:3}",
                "vector{coordinateType:long,dimensions:3}",
                "vector{coordinateType:float,dimensions:3}",
                "vector{coordinateType:double,dimensions:3}"
            })
    void shouldWriteNullForVectorsWhenInputIsEmpty(String header) throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:%s".formatted(header))),
                Collections.singletonList(new Object[] {1, ""}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties(), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldFailImportOnVectorWithMissingValue() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions: 3, coordinateType:int}")),
                Collections.singletonList(new Object[] {1, "1;;23"}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("could not convert 1;;23 to VECTOR");
        }
    }

    @Test
    void shouldFailImportOnVectorWithMissingValueLast() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions: 3, coordinateType:int}")),
                Collections.singletonList(new Object[] {1, "1;23;"}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("could not convert 1;23; to VECTOR");
        }
    }

    @Test
    void shouldFailImportOnVectorWithNonNumericValue() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions: 3, coordinateType:int}")),
                Collections.singletonList(new Object[] {1, "1;abc;23"}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("could not convert 1;abc;23 to VECTOR");
        }
    }

    static Stream<Arguments> shouldAllowDifferentCapitalizationOfVectorInfo() {
        return Stream.of(
                Arguments.of(
                        "vEcToR{coordinateType:byte,dimensions:2}", "1;23", Values.int8Vector((byte) 1, (byte) 23)),
                Arguments.of(
                        "vector{Coordinatetype:sHort,dimensions:2}", "1;23", Values.int16Vector((short) 1, (short) 23)),
                Arguments.of("vector{coordinateType:inT,DIMENSIONS:2}", "1;23", Values.int32Vector(1, 23)),
                Arguments.of("vector{coordinateType:lOng,dImensions:2}", "1;23", Values.int64Vector(1, 23)));
    }

    @ParameterizedTest
    @MethodSource
    void shouldAllowDifferentCapitalizationOfVectorInfo(String header, String stringValue, VectorValue expectedValue)
            throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:%s".formatted(header))),
                Collections.singletonList(new Object[] {1, stringValue}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, properties("vprop", expectedValue), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldFailImportOnVectorWithUnknownPropertyType() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions: 3, coordinateType:pyte}")),
                Collections.singletonList(new Object[] {1, "1;2;23"}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("pyte is not a valid coordinate type.");
        }
    }

    @Test
    void shouldFailImportOnVectorWithNonIntegerDimension() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions: three, coordinateType:byte}")),
                Collections.singletonList(new Object[] {1, "1;2;23"}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("three is not a valid value for dimensions.");
        }
    }

    @Test
    void shouldFailImportOnVectorWithTooLargeDimension() throws Exception {
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("vprop:vector{dimensions: 5000, coordinateType:byte}")),
                Collections.singletonList(
                        new Object[] {1, Stream.generate(() -> "1").limit(5000).collect(Collectors.joining(";"))}));

        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertThatThrownBy(() -> readNext(nodes))
                    .isInstanceOf(InputException.class)
                    .hasMessageContaining("Invalid vector dimensions: 5000");
        }
    }

    @Test
    void shouldNotIncludeNullArraysInEntities() throws Exception {
        // GIVEN
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("sprop:String[]"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("lprop:long[]")),
                List.of(new Object[] {1, null, null}, new Object[] {2, "a;b", "10;20"}));
        Input input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);

        // WHEN/THEN
        try (InputIterator nodes = input.nodes(EMPTY).iterator()) {
            assertNextNode(nodes, 1L, emptyMap(), labels());
            assertNextNode(
                    nodes, 2L, properties("sprop", new String[] {"a", "b"}, "lprop", new long[] {10, 20}), labels());
            assertFalse(readNext(nodes));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {":SOMETHING", "abcde#rtg:123", "", ":START_ID", ":END_ID", ":TYPE"})
    void shouldFailOnUnparsableNodeColumn(String unparsableColumnNames) throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(unparsableColumnNames)),
                List.<Object[]>of(new Object[] {1, "test"}));
        try {
            // when
            createParquetInput(
                    Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, MONITOR);
            fail("Should not parse");
        } catch (InputException e) {
            // then
            // OK
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {":SOMETHING", "abcde#rtg:123", ":ID", ":LABEL"})
    void shouldFailOnUnparsableRelationshipHeader(String unparsableColumnName) throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(unparsableColumnName)),
                List.<Object[]>of(new Object[] {1, 2, "TYPE", "test"}));
        try {
            // when
            createParquetInput(
                    Map.of(Set.of(""), List.of()),
                    Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                    INTEGER,
                    groups,
                    MONITOR);
            fail("Should not parse");
        } catch (InputException e) {
            // then
            // OK
        }
    }

    @Test
    void shouldFailOnUndefinedGroupInRelationshipHeader() throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID(left)"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID(rite)")),
                List.of(new Object[] {123, "TYPE", 234}, new Object[] {345, "TYPE", 456}));
        Path nodeFile1 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID(left)")), List.of());
        Path nodeFile2 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID(right)")), List.of());
        try {
            // when
            createParquetInput(
                    Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                    Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                    INTEGER,
                    groups,
                    MONITOR);
            fail("Should not validate");
        } catch (InputException e) {
            // then
            // OK
        }
    }

    @Test
    void shouldFailOnGlobalGroupInRelationshipHeaderIfNoGlobalGroupInNodeHeader() throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID(left)"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID")),
                List.of(new Object[] {123, "TYPE", 234}, new Object[] {345, "TYPE", 456}));
        Path nodeFile1 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID(left)")), List.of());
        Path nodeFile2 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID(right)")), List.of());
        try {
            // when
            createParquetInput(
                    Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                    Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                    INTEGER,
                    new Groups(),
                    MONITOR); // new Groups() instead of field groups important here to not have the global id space
            fail("Should not validate");
        } catch (InputException e) {
            // then
            // OK
        }
    }

    @Test
    void shouldNormalizeTypes() throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("byteProp:byte"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("longProp:long")),
                List.<Object[]>of(new Object[] {123, 234, 8, 123L}));
        Path nodeFile1 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("shortProp:short"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("intProp:int")),
                List.<Object[]>of(new Object[] {1, 234, 1024}));
        Path nodeFile2 = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.FLOAT).named("floatProp:float"),
                        Types.required(PrimitiveType.PrimitiveTypeName.DOUBLE).named("doubleProp")),
                List.<Object[]>of(new Object[] {2, 43f, 37d}));
        ParquetMonitor monitor = mock(ParquetMonitor.class);

        // when
        createParquetInput(
                Map.of(Set.of("someLabel"), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                Map.of("someType", List.<Path[]>of(new Path[] {relationshipFile})),
                INTEGER,
                groups,
                monitor);

        // then
        verify(monitor, times(1)).typeNormalized("test1.parquet", "intProp", "INT", "LONG");
        verify(monitor, times(1)).typeNormalized("test1.parquet", "shortProp", "SHORT", "LONG");
        verify(monitor, times(1)).typeNormalized("test2.parquet", "floatProp", "FLOAT", "DOUBLE");
        verify(monitor, times(1)).typeNormalized("test0.parquet", "byteProp", "BYTE", "LONG");
        verifyNoMoreInteractions(monitor);
    }

    @Test
    void shouldReportNoNodeLabels() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID")),
                List.<Object[]>of(new Object[] {1}));
        ParquetMonitor monitor = mock(ParquetMonitor.class);

        // when
        createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, monitor);
        // then
        verify(monitor).noNodeLabelsSpecified("test0.parquet");
    }

    @Test
    void shouldNotReportNoNodeLabelsIfDecorated() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID")),
                List.<Object[]>of(new Object[] {1}));
        ParquetMonitor monitor = mock(ParquetMonitor.class);

        // when
        createParquetInput(
                Map.of(Set.of("test"), List.<Path[]>of(new Path[] {nodeFile})), Map.of(), INTEGER, groups, monitor);

        // then
        verify(monitor, never()).noNodeLabelsSpecified("test0.parquet");
    }

    @Test
    void shouldReportNoRelationshipType() throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID")),
                List.<Object[]>of(new Object[] {1, 2}));
        ParquetMonitor monitor = mock(ParquetMonitor.class);

        // when
        createParquetInput(
                Map.of(), Map.of("", List.<Path[]>of(new Path[] {relationshipFile})), INTEGER, groups, monitor);

        // then
        verify(monitor).noRelationshipTypeSpecified("test0.parquet");
    }

    @Test
    void shouldNotReportNoRelationshipTypeIfDecorated() throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID")),
                List.<Object[]>of(new Object[] {1, 2}));
        ParquetMonitor monitor = mock(ParquetMonitor.class);

        // when
        createParquetInput(
                Map.of(), Map.of("someType", List.<Path[]>of(new Path[] {relationshipFile})), INTEGER, groups, monitor);
        // then
        verify(monitor, never()).noRelationshipTypeSpecified("test0.parquet");
    }

    @Test
    void shouldReportDuplicateNodeHeader() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name:string"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name")),
                List.of());
        try {
            // when
            createParquetInput(
                    Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                    Map.of(),
                    INTEGER,
                    groups,
                    new ParquetMonitor(System.out));
            fail("Should have failed");
        } catch (DuplicatedColumnException e) {
            // THEN
            assertThat(e).hasMessageContaining("test0.parquet");
        }
    }

    @Test
    void shouldReportDuplicateRelationshipHeader() throws Exception {
        // given
        Path relationshipFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":START_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named(":END_ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":TYPE"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name")),
                List.of());
        try {
            // when
            createParquetInput(
                    Map.of(),
                    Map.of("", List.<Path[]>of(new Path[] {relationshipFile})),
                    INTEGER,
                    groups,
                    new ParquetMonitor(System.out));
            fail("Should have failed");
        } catch (DuplicatedColumnException e) {
            // THEN
            assertThat(e).hasMessageContaining("test0.parquet");
        }
    }

    @Test
    void shouldThrowOnReferencedNodeSchemaWithoutExplicitLabelOptionData() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("my:ID(Person)"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name:string"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of());
        try (var input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                STRING,
                groups,
                new ParquetMonitor(System.out))) {
            // when
            var tokenHolders = new TokenHolders(
                    tokenHolder(Map.of("myId", 4)), tokenHolder(Map.of("Person", 2)), tokenHolder(Map.of()));

            // then
            assertThatThrownBy(() -> input.referencedNodeSchema(tokenHolders))
                    .hasMessageContaining("No label was specified");
        }
    }

    @Test
    void shouldHandleMultipleEqualReferencedSchemaForSameGroup() throws Exception {
        // given
        Path nodeFile1 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("myId:ID(MyGroup){label:Person}")),
                List.of());
        Path nodeFile2 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("myId:ID(MyGroup){label:Person}")),
                List.of());
        try (var input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                Map.of(),
                STRING,
                groups,
                new ParquetMonitor(System.out))) {
            // when
            var tokenHolders = new TokenHolders(
                    tokenHolder(Map.of("myId", 4)), tokenHolder(Map.of("Person", 2)), tokenHolder(Map.of()));

            // then
            var referencedNodeSchema = input.referencedNodeSchema(tokenHolders);
            assertThat(referencedNodeSchema)
                    .containsEntry(
                            "MyGroup",
                            SchemaDescriptors.forLabel(
                                    tokenHolders.labelTokens().getIdByName("Person"),
                                    tokenHolders.propertyKeyTokens().getIdByName("myId")));
        }
    }

    @Test
    void shouldFailMultipleNonEqualReferencedSchemaForSameGroup() throws Exception {
        // given
        Path nodeFile1 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("myId:ID(MyGroup){label:Person}")),
                List.of());
        Path nodeFile2 = createParquetFile(
                List.of(Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("myId:ID(MyGroup){label:Company}")),
                List.of());
        try (var input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile1, nodeFile2})),
                Map.of(),
                STRING,
                groups,
                new ParquetMonitor(System.out))) {
            // when
            var tokenHolders = new TokenHolders(
                    tokenHolder(Map.of("myId", 4)),
                    tokenHolder(Map.of("Person", 2, "Company", 3)),
                    tokenHolder(Map.of()));

            // then
            assertThatThrownBy(() -> input.referencedNodeSchema(tokenHolders))
                    .hasMessageContaining("Multiple different indexes for group");
        }
    }

    @Test
    void shouldParseReferencedNodeSchemaWithExplicitLabelOptionData() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("myId:ID(My Group){label:Person}"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name:string"),
                        Types.optional(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of());
        try (var input = createParquetInput(
                Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                Map.of(),
                STRING,
                groups,
                new ParquetMonitor(System.out))) {
            // when
            var tokenHolders = new TokenHolders(
                    tokenHolder(Map.of("myId", 4)), tokenHolder(Map.of("Person", 2)), tokenHolder(Map.of()));
            var schema = input.referencedNodeSchema(tokenHolders);

            // then
            Assertions.assertThat(schema).isEqualTo(Map.of("My Group", SchemaDescriptors.forLabel(2, 4)));
        }
    }

    @Test
    void shouldStoreIdAsPropertyInSpecificValueType() throws Exception {
        // given nodes w/ IDs as ints
        // when using string id-type in the input
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.INT32).named("id:ID{id-type:int}"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("prop")),
                List.<Object[]>of(new Object[] {123, "val"}));
        try (var input = createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                        Map.of(),
                        STRING,
                        groups,
                        new ParquetMonitor(System.out));
                var nodes = input.nodes(EMPTY).iterator()) {
            // then
            assertNextNode(nodes, 123, properties("id", 123, "prop", "val"), labels());
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldHandleMultipleNodeIdColumns() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("id1:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("id2:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of(new Object[] {"ABC", "123", "First", "Person"}, new Object[] {"ABC", "456", "Second", "Person"
                }));
        try (var input = createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                        Map.of(),
                        STRING,
                        groups,
                        new ParquetMonitor(System.out));
                var nodes = input.nodes(Collector.STRICT).iterator()) {
            assertNextNode(
                    nodes,
                    "ABC%s123".formatted(ParquetInput.DELIMITER),
                    properties("id1", "ABC", "id2", "123", "name", "First"),
                    Set.of("Person"));
            assertNextNode(
                    nodes,
                    "ABC%s456".formatted(ParquetInput.DELIMITER),
                    properties("id1", "ABC", "id2", "456", "name", "Second"),
                    Set.of("Person"));
            assertFalse(readNext(nodes));
        }
    }

    @Test
    void shouldFailOnStoringMultipleCompositeIdColumnsInSameProperty() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("id:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("id:ID"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of(new Object[] {"ABC", "123", "First", "Person"}, new Object[] {"ABC", "456", "Second", "Person"
                }));
        // when/then
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                        Map.of(),
                        STRING,
                        groups,
                        new ParquetMonitor(System.out)))
                .isInstanceOf(InputException.class)
                .hasMessageContaining("Cannot store composite IDs");
    }

    @Test
    void shouldFailOnCompositeIdColumnsForDifferentGroups() throws Exception {
        // given
        Path nodeFile = createParquetFile(
                List.of(
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":ID(group1)"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":ID(group2)"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named("name"),
                        Types.required(PrimitiveType.PrimitiveTypeName.BINARY)
                                .as(LogicalTypeAnnotation.stringType())
                                .named(":LABEL")),
                List.of(new Object[] {"ABC", "123", "First", "Person"}, new Object[] {"ABC", "456", "Second", "Person"
                }));
        // when/then
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                        Map.of(),
                        INTEGER,
                        groups,
                        new ParquetMonitor(System.out)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("referring to different groups");
    }

    @Test
    void shouldFailOnNonParquetFile() throws Exception {
        Path nodeFile = createNonParquetFile();
        assertThatThrownBy(() -> createParquetInput(
                        Map.of(Set.of(""), List.<Path[]>of(new Path[] {nodeFile})),
                        Map.of(),
                        INTEGER,
                        groups,
                        new ParquetMonitor(System.out)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Could not read parquet file %s".formatted(nodeFile));
    }

    private static ParquetInput createParquetInput(
            Map<Set<String>, List<Path[]>> nodeFiles,
            Map<String, List<Path[]>> relationshipFiles,
            IdType idType,
            Groups idGroups,
            ParquetMonitor parquetMonitor) {
        return createParquetInput(
                nodeFiles,
                relationshipFiles,
                idType,
                idGroups,
                parquetMonitor,
                Configuration.newBuilder().build());
    }

    private static ParquetInput createParquetInput(
            Map<Set<String>, List<Path[]>> nodeFiles,
            Map<String, List<Path[]>> relationshipFiles,
            IdType idType,
            Groups idGroups,
            ParquetMonitor parquetMonitor,
            Configuration csvConfig) {
        return new ParquetInput(nodeFiles, relationshipFiles, List.of(), idType, csvConfig, idGroups, parquetMonitor);
    }

    private Path createNonParquetFile() throws Exception {
        Path path = directory.file("test-non.parquet");
        try (var writer = new FileWriter(path.toFile())) {
            writer.write("some data for sure not parquet");
        }
        return path;
    }

    private Path createParquetFile(List<org.apache.parquet.schema.Type> types, List<Object[]> data) throws Exception {
        Path path = directory.file("test%d.parquet".formatted(fileCounter++));
        try (var writer =
                ParquetWriter.writeFile(new MessageType("something", types), path.toFile(), (record, valueWriter) -> {
                    var recordData = (Object[]) record;
                    for (int i = 0; i < types.size(); i++) {
                        org.apache.parquet.schema.Type type = types.get(i);
                        Object value = recordData[i];
                        if (value != null) {
                            valueWriter.write(type.getName(), value);
                        }
                    }
                })) {
            for (Object[] datum : data) {
                writer.write(datum);
            }
        }

        return path;
    }

    private Path createHeaderFile(List<String> columnNames, List<String> originalColumnNames) throws Exception {
        return createHeaderFile(columnNames, originalColumnNames, ",");
    }

    private Path createHeaderFile(List<String> columnNames, List<String> originalColumnNames, String delimiter)
            throws Exception {
        Path path = directory.file("header.csv");
        try (var writer = new BufferedWriter(new FileWriter(path.toFile()))) {
            writer.write(String.join(delimiter, columnNames));
            writer.newLine();
            if (!originalColumnNames.isEmpty()) {
                writer.write(String.join(delimiter, originalColumnNames));
                writer.newLine();
            }
        }
        return path;
    }

    private TokenHolder tokenHolder(Map<String, Integer> tokens) {
        var tokenHolder = new CreatingTokenHolder(ReadOnlyTokenCreator.READ_ONLY, "type");
        tokenHolder.setInitialTokens(tokens.entrySet().stream()
                .map(e -> new NamedToken(e.getKey(), e.getValue()))
                .toList());
        return tokenHolder;
    }

    private void assertNextRelationship(
            InputIterator relationship, Object startNode, Object endNode, String type, Map<String, Object> properties)
            throws IOException {
        assertRelationship(relationship, globalGroup, startNode, globalGroup, endNode, type, properties);
    }

    private void assertRelationship(
            InputIterator data,
            Group startNodeGroup,
            Object startNode,
            Group endNodeGroup,
            Object endNode,
            String type,
            Map<String, Object> properties)
            throws IOException {
        assertTrue(readNext(data));
        assertEquals(startNodeGroup, visitor.startIdGroup);
        assertEquals(startNode, visitor.startId());
        assertEquals(endNodeGroup, visitor.endIdGroup);
        assertEquals(endNode, visitor.endId());
        assertEquals(type, visitor.stringType);
        assertPropertiesEquals(properties, visitor.propertiesAsMap());
    }

    private void assertRelationship(
            InputIterator data,
            String startNodeGroupName,
            Object startNode,
            String endNodeGroupName,
            Object endNode,
            String type,
            Map<String, Object> properties)
            throws IOException {
        assertTrue(readNext(data));
        assertEquals(startNodeGroupName, visitor.startIdGroup.name());
        assertEquals(startNode, visitor.startId());
        assertEquals(endNodeGroupName, visitor.endIdGroup.name());
        assertEquals(endNode, visitor.endId());
        assertEquals(type, visitor.stringType);
        assertPropertiesEquals(properties, visitor.propertiesAsMap());
    }

    private void assertNextNode(InputIterator data, Object id, Map<String, Object> properties, Set<String> labels)
            throws IOException {
        assertNextNode(data, globalGroup, id, properties, labels);
    }

    private void assertNextNode(
            InputIterator data, Group group, Object id, Map<String, Object> properties, Set<String> labels)
            throws IOException {
        assertTrue(readNext(data));
        assertEquals(group, visitor.idGroup);
        assertEquals(id, visitor.id());
        assertEquals(labels, asSet(visitor.labels()));
        assertPropertiesEquals(properties, visitor.propertiesAsMap());
    }

    private void assertPropertiesEquals(Map<String, Object> expected, Map<String, Object> actual) {
        // Do this more complicated assert to handle primitive array equality
        assertEquals(primitiveArraysAsLists(expected), primitiveArraysAsLists(actual));
    }

    private Map<String, Object> primitiveArraysAsLists(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        for (var entry : map.entrySet()) {
            var value = entry.getValue();
            var cls = value.getClass();
            if (cls.isArray()) {
                List<Object> listValue = new ArrayList<>();
                var length = Array.getLength(value);
                for (var i = 0; i < length; i++) {
                    listValue.add(Array.get(value, i));
                }
                value = listValue;
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private boolean readNext(InputIterator data) throws IOException {
        if (referenceData != data) {
            chunk = null;
            referenceData = data;
        }

        if (chunk == null) {
            chunk = data.newChunk();
            if (!data.next(chunk)) {
                return false;
            }
        }

        if (chunk.next(visitor)) {
            return true;
        }
        if (!data.next(chunk)) {
            return false;
        }
        return chunk.next(visitor);
    }

    private static Map<String, Object> properties(Object... keysAndValues) {
        return MapUtil.map(keysAndValues);
    }

    private static Set<String> labels(String... labels) {
        return asSet(labels);
    }

    private static Stream<String> groupNames() {
        return Stream.of("", null);
    }

    private static Stream<Arguments> listTypes() {
        return Stream.of(
                Arguments.of("list.parquet", List.of("a", "b", "c")),
                Arguments.of("list_int32.parquet", List.of(123, 234, 345)),
                Arguments.of("list_int64.parquet", List.of(123L, 234L, 345L)),
                Arguments.of("list_int128.parquet", List.of(123d, 234d, 345d)),
                Arguments.of("list_float.parquet", List.of(1.01f, 2.21f, 3.23f)),
                Arguments.of("list_double.parquet", List.of(1.01d, 2.21d, 3.23d)),
                Arguments.of("list_boolean.parquet", List.of(true, false, true)));
    }
}

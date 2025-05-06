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
package org.neo4j.procedure.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.virtual.ListValue;

/**
 * Tests for {@link ListComponentsProcedure}.
 */
class ListComponentsProcedureTest {
    @Test
    void usesCustomVersionWhenConfigured() throws ProcedureException {
        // Given
        var customConfigVersion = "5.27.0";
        var procedure = new ListComponentsProcedure(
                new QualifiedName(new String[] {"dbms"}, "components"), "2025.01.0", "community", customConfigVersion);

        // When
        try (var result = procedure.apply(null, new AnyValue[0], null)) {
            // Then
            var row = filterByComponentName(Iterators.asList(result), "Neo4j Kernel");

            var versions = (ListValue) row[1];
            assertEquals(1, versions.intSize());
            assertEquals("5.27.0", ((TextValue) versions.value(0)).stringValue());
            assertEquals("community", ((TextValue) row[2]).stringValue());
        }
    }

    @Test
    void listCypherVersions() throws ProcedureException {
        var customConfigVersion = "5.27.0";
        var procedure = new ListComponentsProcedure(
                new QualifiedName(new String[] {"dbms"}, "components"), "2025.01.0", "community", customConfigVersion);

        try (var result = procedure.apply(null, new AnyValue[0], null)) {
            var row = filterByComponentName(Iterators.asList(result), "Cypher");

            var versions = (ListValue) row[1];
            assertEquals(1, versions.intSize());
            assertEquals("5", ((TextValue) versions.value(0)).stringValue());
            assertEquals("", ((TextValue) row[2]).stringValue());
        }
    }

    private static AnyValue[] filterByComponentName(List<AnyValue[]> data, String name) {
        return data.stream()
                .filter(row -> {
                    if (row[0] instanceof TextValue cell) {
                        return name.equals(cell.stringValue());
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow();
    }
}

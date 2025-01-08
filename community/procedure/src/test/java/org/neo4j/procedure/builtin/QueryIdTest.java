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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.neo4j.kernel.api.exceptions.InvalidArgumentsException;

class QueryIdTest {
    private static final String PROCEDURE_NAME = "proc";
    private static final String ARGUMENT_NAME = "arg";

    @Test
    void parsesQueryIds() throws InvalidArgumentsException {
        assertThat(QueryId.parse("query-14", "arg", "proc")).isEqualTo(14L);
    }

    @Test
    void doesNotParseNegativeQueryIds() {
        var e = assertThrows(
                InvalidArgumentsException.class, () -> QueryId.parse("query--12", ARGUMENT_NAME, PROCEDURE_NAME));
        assertThat(e).hasMessageContaining("Negative ids are not supported (expected format: query-<id>)");
        assertGql(e, "query--12");
    }

    @Test
    void doesNotParseWrongPrefix() {
        var e = assertThrows(
                InvalidArgumentsException.class, () -> QueryId.parse("querr-12", ARGUMENT_NAME, PROCEDURE_NAME));
        assertThat(e).hasMessageContaining("Expected prefix query-");
        assertGql(e, "querr-12");
    }

    @Test
    void doesNotParseRandomText() {
        var e = assertThrows(
                InvalidArgumentsException.class, () -> QueryId.parse("blarglbarf", ARGUMENT_NAME, PROCEDURE_NAME));
        assertThat(e).hasMessageContaining("Expected prefix query-");
        assertGql(e, "blarglbarf");
    }

    @Test
    void doesNotParseTrailingRandomText() {
        var e = assertThrows(
                InvalidArgumentsException.class, () -> QueryId.parse("query-12  ", ARGUMENT_NAME, PROCEDURE_NAME));
        assertThat(e).hasMessageContaining("Could not parse id query-12   (expected format: query-<id>)");
        assertGql(e, "query-12  ");
    }

    @Test
    void doesNotParseEmptyText() {
        var e = assertThrows(InvalidArgumentsException.class, () -> QueryId.parse("", ARGUMENT_NAME, PROCEDURE_NAME));
        assertThat(e).hasMessageContaining("Expected prefix query-");
        assertGql(e, "");
    }

    private void assertGql(InvalidArgumentsException e, String providedQueryId) {
        assertThat(e.gqlStatus()).isEqualTo("52N16");
        assertThat(e.statusDescription())
                .isEqualTo(
                        "error: procedure exception - invalid procedure argument list. Invalid arguments to procedure.");
        assertThat(e.cause()).isPresent();
        var gqlCause = e.cause().get();
        assertThat(gqlCause.gqlStatus()).isEqualTo("52N22");
        assertThat(gqlCause.statusDescription())
                .isEqualTo(
                        "error: procedure exception - invalid procedure argument. Invalid argument `%s` for `%s` on procedure %s(). The expected format of `%s` is query-<id>."
                                .formatted(providedQueryId, ARGUMENT_NAME, PROCEDURE_NAME, ARGUMENT_NAME));
    }
}

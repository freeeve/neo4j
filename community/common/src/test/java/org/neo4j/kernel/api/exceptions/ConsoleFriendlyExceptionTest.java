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
package org.neo4j.kernel.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConsoleFriendlyExceptionTest {
    @Test
    void nullCauseIsOK() throws IOException {
        var boom = "Boom";
        var exception = new TestException(boom, null);
        assertThat(exception.willPrettyPrint()).isTrue();
        assertThat(exception.getMessage()).contains(boom);

        try (var output = new ByteArrayOutputStream()) {
            exception.prettyPrint(new PrintStream(output));
            assertThat(output.toString(StandardCharsets.UTF_8))
                    .contains(ConsoleFriendlyException.DIVIDER, boom, ConsoleFriendlyException.DIVIDER);
        }
    }

    @Test
    void causeMessageNotPrintedTwice() throws IOException {
        var boom = "Boom";
        var exception = new TestException(new IllegalStateException(boom));
        assertThat(exception.willPrettyPrint()).isTrue();
        assertThat(exception.getMessage()).contains(boom);

        try (var output = new ByteArrayOutputStream()) {
            exception.prettyPrint(new PrintStream(output));
            assertThat(output.toString(StandardCharsets.UTF_8))
                    .contains(ConsoleFriendlyException.DIVIDER, boom, ConsoleFriendlyException.DIVIDER)
                    .containsOnlyOnce(boom);
        }
    }

    private static class TestException extends ConsoleFriendlyException {
        private TestException(Exception cause) {
            super(cause, true);
        }

        private TestException(String message, Exception cause) {
            super(message, cause, true);
        }
    }
}

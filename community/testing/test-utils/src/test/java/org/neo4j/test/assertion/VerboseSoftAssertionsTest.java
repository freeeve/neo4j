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
package org.neo4j.test.assertion;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class VerboseSoftAssertionsTest {
    @Test
    public void shouldDisplayTheErrorCauseAndTheCauseFirstStackTraceElements() {
        assertThatThrownBy(() -> {
                    SoftAssertions softly = new VerboseSoftAssertions();
                    try {
                        throw new RuntimeException("abc");
                    } catch (RuntimeException e) {
                        softly.fail("def", e);
                    }
                    softly.assertAll();
                })
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith(format("%nThe following assertion failed:%n" + "1) def"))
                .hasMessageContaining(format(
                        "cause message: abc%n" + "cause first five stack trace elements:%n" + "\tat %s.",
                        this.getClass().getName()));
    }
}

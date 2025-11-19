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
package org.neo4j.io.fs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.io.fs.filename.LexicographicalFileSequence;

class LexicographicalFileSequenceTest {

    @Test
    void selectNamesFromStream() {
        List<String> expected = List.of("filename.001", "filename.002", "filename.003", "filename.004", "filename.005");

        var sequence = LexicographicalFileSequence.of("filename");
        var actual = sequence.stream().limit(5).toList();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void selectNamesFromIterator() {
        List<String> expected = List.of("filename.001", "filename.002", "filename.003", "filename.004", "filename.005");

        var sequence = LexicographicalFileSequence.of("filename");
        var it = sequence.iterator();
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < 5 && it.hasNext(); i++) {
            actual.add(it.next());
        }
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void independentIterators() {
        var sequence = LexicographicalFileSequence.of("filename");

        var it1 = sequence.iterator();
        var it2 = sequence.iterator();

        it1.next();
        assertThat(it1.next()).isNotEqualTo(it2.next());
    }

    @ParameterizedTest(name = "numDigits = {0}, start = {1}, end = {2}")
    @MethodSource
    void respectsNumDigitsBound(int numDigits, String expectedStart, String expectedEnd) {
        var sequence = LexicographicalFileSequence.of("filename", ".", numDigits);
        var it = sequence.iterator();

        assertThat(it.next()).isEqualTo(expectedStart);
        String actual = null;
        while (it.hasNext()) {
            actual = it.next();
        }
        assertThat(actual).isEqualTo(expectedEnd);
    }

    public static Stream<Arguments> respectsNumDigitsBound() {
        return Stream.of(
                Arguments.of(1, "filename.1", "filename.9"),
                Arguments.of(2, "filename.01", "filename.99"),
                Arguments.of(3, "filename.001", "filename.999"));
    }
}

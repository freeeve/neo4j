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
package org.neo4j.csv.reader;

import static java.util.Arrays.copyOfRange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.neo4j.csv.reader.HeaderSkipper.NO_SKIP;

import org.junit.jupiter.api.Test;
import org.neo4j.csv.reader.Source.Chunk;

class ClosestNewLineChunkerTest {
    @Test
    void shouldBackUpChunkToClosestNewline() throws Exception {
        // GIVEN
        CharReadable reader = Readables.wrap("1234567\n8901234\n5678901234");
        // (next chunks):                                   ^            ^
        // (actual chunks):                             ^        ^
        try (ClosestNewLineChunker source = new ClosestNewLineChunker(reader, 12, NO_SKIP)) {
            // WHEN
            Chunk chunk = source.newChunk();
            assertThat(source.nextChunk(chunk)).isTrue();
            assertThat(charactersOf(chunk)).containsExactly("1234567\n".toCharArray());
            assertThat(source.nextChunk(chunk)).isTrue();
            assertThat(charactersOf(chunk)).containsExactly("8901234\n".toCharArray());
            assertThat(source.nextChunk(chunk)).isTrue();
            assertThat(charactersOf(chunk)).containsExactly("5678901234".toCharArray());

            // THEN
            assertThat(source.nextChunk(chunk)).isFalse();
        }
    }

    @Test
    void shouldFailIfNoNewlineInChunk() throws Exception {
        // GIVEN
        CharReadable reader = Readables.wrap("1234567\n89012345678901234");
        // (next chunks):                                   ^
        // (actual chunks):                             ^
        try (ClosestNewLineChunker source = new ClosestNewLineChunker(reader, 12, NO_SKIP)) {
            // WHEN
            Chunk chunk = source.newChunk();
            assertThat(source.nextChunk(chunk)).isTrue();
            assertThat(charactersOf(chunk)).containsExactly("1234567\n".toCharArray());
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> assertThat(source.nextChunk(chunk)).isFalse());
        }
    }

    private static char[] charactersOf(Chunk chunk) {
        return copyOfRange(chunk.data(), chunk.startPosition(), chunk.startPosition() + chunk.length());
    }
}

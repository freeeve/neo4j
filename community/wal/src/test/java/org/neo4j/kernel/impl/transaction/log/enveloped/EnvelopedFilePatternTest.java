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
package org.neo4j.kernel.impl.transaction.log.enveloped;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class EnvelopedFilePatternTest {

    @Test
    public void shouldMatchCorrectVersion() {
        // given
        var baseName = "raft-log";
        var fileName = "raft-log.123";
        var pattern = EnvelopedFilePattern.envelopedFilePattern(baseName);

        var matcher = pattern.matcher(fileName);
        assertTrue(matcher.matches(), "Pattern should match the file name");
        assertEquals("123", matcher.group("VERSION"), "Version should be extracted correctly");
    }

    @Test
    public void shouldNotMatchWrongBaseName() {
        var baseName = "raft-log";
        var fileName = "raftttt-logggg.123";
        Pattern pattern = EnvelopedFilePattern.envelopedFilePattern(baseName);

        Matcher matcher = pattern.matcher(fileName);
        assertFalse(matcher.matches(), "Pattern should not match a different base name");
    }

    @Test
    public void shouldMatchExpectedVersionFormats() {
        var baseName = "raft-log";
        Pattern pattern = EnvelopedFilePattern.envelopedFilePattern(baseName);

        assertTrue(pattern.matcher("raft-log.0").matches());
        assertTrue(pattern.matcher("raft-log.1").matches());
        assertTrue(pattern.matcher("raft-log.10").matches());
        assertTrue(pattern.matcher("raft-log.11").matches());
    }

    @Test
    public void shouldNotMatchInvalidVersionFormats() {
        var baseName = "raft-log";
        Pattern pattern = EnvelopedFilePattern.envelopedFilePattern(baseName);

        assertFalse(pattern.matcher("raft-log.01").matches());
        assertFalse(pattern.matcher("raft-log.001").matches());
        assertFalse(pattern.matcher("raft-log.").matches());
        assertFalse(pattern.matcher("raft-log.-1").matches());
        assertFalse(pattern.matcher("raft-log.1a").matches());
        assertFalse(pattern.matcher("raft-log.a1").matches());
        assertFalse(pattern.matcher("raft-log.ab").matches());
    }
}

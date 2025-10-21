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
package org.neo4j.fleetmanagement.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.neo4j.fleetmanagement.utils.PatternCompiler;

public class MetricsPatternTest {
    @Test
    void shouldMatchExactPattern() {
        // Given
        String patternStr = "metrics.test.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // Then
        assertTrue(pattern.matcher("metrics.test.value").matches());
        assertFalse(pattern.matcher("metrics.test.other").matches());
    }

    @Test
    void shouldMatchPatternWithNamedGroups() {
        // Given
        String patternStr = "metrics.<name>.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // When
        var matcher = pattern.matcher("metrics.test.value");

        // Then
        assertTrue(matcher.matches());
        assertEquals("test", matcher.group("name"));
    }

    @Test
    void shouldMatchPatternWithMultipleNamedGroups() {
        // Given
        String patternStr = "metrics.<group>.<name>.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // When
        var matcher = pattern.matcher("metrics.system.test.value");

        // Then
        assertTrue(matcher.matches());
        assertEquals("system", matcher.group("group"));
        assertEquals("test", matcher.group("name"));
    }

    @Test
    void shouldMatchPatternWithWildcards() {
        // Given
        String patternStr = "**.<name>.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // Then
        assertTrue(pattern.matcher("prefix.test.value").matches());
        assertTrue(pattern.matcher("a.b.c.test.value").matches());
        assertFalse(pattern.matcher("test.value").matches()); // Missing wildcard content
    }

    @Test
    void shouldMatchPatternWithMultipleWildcards() {
        // Given
        String patternStr = "**.<name>.*.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // When
        var matcher = pattern.matcher("a.b.c.test.e.value");

        // Then
        assertTrue(matcher.matches());
        assertEquals("test", matcher.group("name"));
        assertTrue(pattern.matcher("prefix.test.middle.value").matches());
        assertFalse(pattern.matcher("test.value").matches()); // Missing wildcard content
    }

    @Test
    void shouldRequireAllSegments() {
        // Given
        String patternStr = "metrics.<name>.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // Then
        assertTrue(pattern.matcher("metrics.test.value").matches());
        assertFalse(pattern.matcher("metrics.value").matches()); // Missing name
        assertFalse(pattern.matcher("metrics.test").matches()); // Missing value
    }

    @Test
    void shouldHandleMultipleWildcardSegments() {
        // Given
        String patternStr = "**.<database>.*.<type>";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // Then
        assertTrue(pattern.matcher("prefix.neo4j.middle.committed").matches());
        assertTrue(pattern.matcher("a.b.c.system.d.active").matches());
        assertFalse(pattern.matcher("prefix.neo4j").matches()); // Missing type
        assertFalse(pattern.matcher("prefix.type").matches()); // Missing database
    }

    @Test
    void shouldEscapeDotsProperly() {
        // Given
        String patternStr = "metrics.test.<database>.value";
        Pattern pattern = PatternCompiler.constructPattern(patternStr);

        // Then
        assertTrue(pattern.matcher("metrics.test.neo4j.value").matches());
        assertFalse(pattern.matcher("metricsxtestxneo4jxvalue").matches()); // Different separator
        assertFalse(pattern.matcher("metrics.test.neo4j_value").matches()); // Wrong separator
    }
}

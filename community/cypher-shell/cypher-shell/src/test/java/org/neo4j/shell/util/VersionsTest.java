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
package org.neo4j.shell.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.info.JvmChecker;
import org.neo4j.shell.startup.CypherShellBoot;

class VersionsTest {

    @Test
    void shouldMatchJavaVersionsWithKernel() {
        // Test to make sure that when the Java versions change, that Cypher shell doesn't get forgotten
        assertEquals(CypherShellBoot.SUPPORTED_JVM_VERSIONS, JvmChecker.SUPPORTED_JVM_VERSIONS);
    }

    @Test
    void shouldWorkForEmptyString() throws Versions.FailedToParseException {
        assertEquals(0, Versions.version("").compareTo(Versions.version("0.0.0")));
        assertEquals(0, Versions.majorVersion(""));
        assertEquals(0, Versions.minorVersion(""));
        assertEquals(0, Versions.patch(""));
    }

    @Test
    void shouldWorkForReleaseVersion() throws Versions.FailedToParseException {
        String versionString = "3.4.5";
        assertEquals(0, Versions.version(versionString).compareTo(Versions.version("3.4.5")));
        assertEquals(3, Versions.majorVersion(versionString));
        assertEquals(4, Versions.minorVersion(versionString));
        assertEquals(5, Versions.patch(versionString));
    }

    @Test
    void shouldWorkForPreReleaseVersion() throws Versions.FailedToParseException {
        String versionString = "3.4.55-beta99";
        assertEquals(0, Versions.version(versionString).compareTo(Versions.version("3.4.55")));
        assertEquals(3, Versions.majorVersion(versionString));
        assertEquals(4, Versions.minorVersion(versionString));
        assertEquals(55, Versions.patch(versionString));
        assertEquals(OptionalInt.empty(), Versions.preRelease(versionString));
        assertTrue(Versions.version(versionString).compareTo(Versions.version("3.4.55-2025041")) < 0);
        versionString = "5.27.0-2025040";
        assertNotEquals(0, Versions.version(versionString).compareTo(Versions.version("5.27.0")));
        assertTrue(Versions.version(versionString).compareTo(Versions.version("5.27.0-2025030")) > 0);
        assertTrue(Versions.version(versionString).compareTo(Versions.version("5.27.0-2025050")) < 0);
        assertFalse(Versions.version(versionString).compareTo(Versions.version("5.27.0-2025040")) < 0);
        assertEquals(5, Versions.majorVersion(versionString));
        assertEquals(27, Versions.minorVersion(versionString));
        assertEquals(0, Versions.patch(versionString));
        assertEquals(OptionalInt.of(2025040), Versions.preRelease(versionString));
    }

    @Test
    void throwOnNull() {
        assertThatThrownBy(() -> Versions.version(null)).isExactlyInstanceOf(Versions.FailedToParseException.class);
    }

    @Test
    void versionToString() throws Versions.FailedToParseException {
        assertThat(Versions.version("5.27.0-2025030").toString()).isEqualTo("5.27.0-2025030");
        assertThat(Versions.version("5.27.0").toString()).isEqualTo("5.27.0");
    }

    @Test
    void throwOnMalformed() {
        assertThatThrownBy(() -> Versions.version("a.b.c")).isExactlyInstanceOf(Versions.FailedToParseException.class);
        assertThatThrownBy(() -> Versions.version("1.2.3.4"))
                .isExactlyInstanceOf(Versions.FailedToParseException.class);
    }
}

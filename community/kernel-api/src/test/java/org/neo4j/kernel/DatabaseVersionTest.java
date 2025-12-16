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
package org.neo4j.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DatabaseVersionTest {

    @Test
    void shouldProvideHigherThanAsExpected() {
        var formatVersions = DatabaseVersion.values();

        for (var from : formatVersions) {
            for (var to : formatVersions) {
                assertThat(from.isHigherThan(to)).isEqualTo(from.identifier() > to.identifier());
            }
        }
    }

    @Test
    void shouldThrowIfVersionByteDoesNotExist() {
        assertThatThrownBy(() -> DatabaseVersion.fromVersionNumber((byte) -1))
                .hasMessageContaining("DatabaseVersion with version number -1 does not exist");
        assertThatThrownBy(() -> DatabaseVersion.fromVersionNumber((byte) 100))
                .hasMessageContaining("DatabaseVersion with version number 100 does not exist");
        assertThatThrownBy(() -> DatabaseVersion.fromVersionNumber(
                        (byte) (DatabaseVersion.getLatestVersion().identifier() + 1)))
                .hasMessageContaining("DatabaseVersion with version number 3 does not exist");
    }

    @Test
    void shouldProvideLatestVersion() {
        assertThat(DatabaseVersion.getLatestVersion()).isEqualTo(DatabaseVersion.V2);
    }

    private static Arguments[] expectedVersionContent() {
        return new Arguments[] {
            Arguments.argumentSet("V0", (byte) 0, (byte) -1, -1, (byte) -1),
            Arguments.argumentSet("V1", (byte) 1, (byte) 127, 5, (byte) 11),
            Arguments.argumentSet("V2", (byte) 2, (byte) 127, 7, (byte) 11)
        };
    }

    @Test
    void shouldOnlyContainItemsInList() {
        assertThat(Arrays.stream(DatabaseVersion.values()).map(DatabaseVersion::identifier))
                .containsExactly(Arrays.stream(expectedVersionContent())
                        .map(ar -> (byte) ar.get()[0])
                        .toArray(Byte[]::new));
    }

    @ParameterizedTest
    @MethodSource("expectedVersionContent")
    void shouldProvideLatestVersionByte(
            byte expectedVersionByte, byte kernelVersion, int serializedVersion, byte logFormatVersion) {
        var version = DatabaseVersion.fromVersionNumber(expectedVersionByte);
        assertThat(version.identifier()).isEqualTo(expectedVersionByte);
        assertThat(version.kernelVersion()).isEqualTo(kernelVersion);
        assertThat(version.contentMarshallerVersion()).isEqualTo(serializedVersion);
        assertThat(version.getLogFormatHeader()).isEqualTo(logFormatVersion);
    }
}

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
package org.neo4j.cloud.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StorageSchemeResolverTest {

    @ParameterizedTest
    @MethodSource("isSchemeBased")
    void isSchemeBased(String path, boolean isSchemeBased) {
        assertThat(StorageSchemeResolver.isSchemeBased(path)).isEqualTo(isSchemeBased);
    }

    private static Stream<Arguments> isSchemeBased() {
        return Stream.of(
                Arguments.of("s3://stuff", true),
                Arguments.of("s3://some/thing", true),
                Arguments.of("gs://stuff", true),
                Arguments.of("gs://some/thing", true),
                Arguments.of("azb://stuff", true),
                Arguments.of("azb://some/thing", true),
                Arguments.of("file:///", true),
                Arguments.of("file:///stuff", true),
                Arguments.of("file:///some/thing", true),
                Arguments.of("/stuff", false),
                Arguments.of("/some/thing", false),
                Arguments.of("Z:\\stuff", false),
                Arguments.of("Z:\\some\\thing", false));
    }
}

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
package org.neo4j.storageengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoreIdGeneratorTest {
    @Test
    void shouldGenerateRandomSeedWithExpectedData() {
        var storageEngineName = "a";
        var formatName = "b";
        var majorVersion = 1;
        var minorVersion = 2;

        var storeIds = StoreIdGenerator.UNIQUE_ID.generateNewStoreId(
                storageEngineName, formatName, majorVersion, minorVersion);

        var storeId = storeIds.storeId();
        assertThat(storeId.getStorageEngineName()).isEqualTo(storageEngineName);
        assertThat(storeId.getFormatName()).isEqualTo(formatName);
        assertThat(storeId.getMajorVersion()).isEqualTo(majorVersion);
        assertThat(storeId.getMinorVersion()).isEqualTo(minorVersion);
    }

    @Test
    void shouldNotGenerateSameIdTwice() {
        var storageEngineName = "a";
        var formatName = "b";
        var majorVersion = 1;
        var minorVersion = 2;
        var first = StoreIdGenerator.UNIQUE_ID.generateNewStoreId(
                storageEngineName, formatName, majorVersion, minorVersion);

        var second = StoreIdGenerator.UNIQUE_ID.generateNewStoreId(
                storageEngineName, formatName, majorVersion, minorVersion);

        assertThat(first.storeId()).isNotEqualTo(second.storeId());
        assertThat(first.externalStoreId()).isNotEqualTo(second.externalStoreId());
    }
}

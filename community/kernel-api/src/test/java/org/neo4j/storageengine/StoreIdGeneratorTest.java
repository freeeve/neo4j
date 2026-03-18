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

import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.DatabaseCreationOptions;

class StoreIdGeneratorTest {
    @Test
    void shouldGenerateRandomSeedWithExpectedData() {
        var storageEngineName = "a";
        var formatName = "b";
        var majorVersion = 1;
        var minorVersion = 2;

        var storeIds = StoreIds.generateNewStoreId(
                storageEngineName,
                formatName,
                majorVersion,
                minorVersion,
                DatabaseCreationOptions.EMPTY_CREATION_OPTIONS);

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
        var first = StoreIds.generateNewStoreId(
                storageEngineName,
                formatName,
                majorVersion,
                minorVersion,
                DatabaseCreationOptions.EMPTY_CREATION_OPTIONS);

        var second = StoreIds.generateNewStoreId(
                storageEngineName,
                formatName,
                majorVersion,
                minorVersion,
                DatabaseCreationOptions.EMPTY_CREATION_OPTIONS);

        assertThat(first.storeId()).isNotEqualTo(second.storeId());
        assertThat(first.externalStoreId()).isNotEqualTo(second.externalStoreId());
    }

    @Test
    void shouldGenerateWithOptionsIfGiven() {
        var storageEngineName = "a";
        var formatName = "b";
        var majorVersion = 1;
        var minorVersion = 2;
        DatabaseCreationOptions options =
                new DatabaseCreationOptions(Optional.empty(), Optional.of(1L), Optional.of(ZonedDateTime.now()));
        var first = StoreIds.generateNewStoreId(storageEngineName, formatName, majorVersion, minorVersion, options);

        var second = StoreIds.generateNewStoreId(storageEngineName, formatName, majorVersion, minorVersion, options);

        assertThat(first.storeId()).isEqualTo(second.storeId());
        assertThat(first.externalStoreId()).isEqualTo(second.externalStoreId());
    }
}

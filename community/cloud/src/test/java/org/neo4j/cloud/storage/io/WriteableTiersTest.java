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
package org.neo4j.cloud.storage.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WriteableTiersTest {
    @Test
    void invalidTiers() {
        assertThatThrownBy(() -> new WriteableTiers(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one tier must be specified");
    }

    @Test
    void invalidSpec() {
        assertThatThrownBy(() -> WriteableTiers.parse(2, "1:2,3;4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("Illegal spec for writeable tier", "3;4");
    }

    @Test
    void invalidChunksPerTier() {
        assertThatThrownBy(() -> WriteableTiers.parse(2, "x:2")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> WriteableTiers.parse(2, "-1:2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("Expected positive int value");
    }

    @Test
    void invalidChunkSizes() {
        assertThatThrownBy(() -> WriteableTiers.parse(2, "2:x")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> WriteableTiers.parse(2, "2:-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("Expected positive long value");
    }

    @Test
    void invalidChunkSizesForMultipleTiers() {
        assertThatThrownBy(() -> WriteableTiers.parse(2, "1:4,2:5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("The batch size of tier 2", "[5]", "[2]");
    }

    @Test
    void maxCalculations() {
        var tiers = WriteableTiers.parse(5, "2:5 , 4:10 , 8:20,16:40");
        assertThat(tiers.totalNumberOfTiers()).isEqualTo(4);
        assertThat(tiers.maxNumberOfBatches()).isEqualTo(2 + 4 + 8 + 16);
        assertThat(tiers.maxDataSize()).isEqualTo((2 * 5) + (4 * 10) + (8 * 20) + (16 * 40));
    }
}

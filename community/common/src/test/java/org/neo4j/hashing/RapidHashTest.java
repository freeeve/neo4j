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
package org.neo4j.hashing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RapidHashTest {
    @Test
    void someHashes() {
        assertThat(RapidHash.hash("test")).isEqualTo(945056766619593L);
        assertThat(RapidHash.hash("1234567890")).isEqualTo(8300421173460395678L);
        assertThat(RapidHash.hash("!8rC)XL6T&)}4*g+dGeJR#/VYDF0[DW{jQxSaXy}6:Yc5_B/S/_M&[aVP4Q-d4fe,+,;u)"
                        + "DAV/Q$JpSW4z0Bcez7XV-CuccBiHC93qeMJ&c2JAhu&pV4%)G/u#R5kawS"))
                .isEqualTo(-2512066029163530560L);
    }
}

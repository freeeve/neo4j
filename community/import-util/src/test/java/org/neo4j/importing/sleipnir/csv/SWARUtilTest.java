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
package org.neo4j.importing.sleipnir.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.importing.sleipnir.csv.SWARUtil.broadcast;
import static org.neo4j.importing.sleipnir.csv.SWARUtil.eqMask;
import static org.neo4j.importing.sleipnir.csv.SWARUtil.prefixXor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SWARUtilTest {
    @Test
    void broadcastToAllBytes() {
        assertThat(broadcast(0x11)).isEqualTo(0x1111111111111111L);
        assertThat(broadcast(-64)).isEqualTo(0xc0c0c0c0c0c0c0c0L);
        assertThat(broadcast(0x0)).isEqualTo(0x0L);
    }

    @Test
    void findNeedle() {
        assertThat(eqMask(0x112233L, broadcast(0x22))).isEqualTo(0b10);
        assertThat(eqMask(0x112233L, broadcast(0x4))).isEqualTo(0);

        assertThat(eqMask(stringToLong("abababab"), broadcast('b'))).isEqualTo(0b10101010);
        assertThat(eqMask(stringToLong("a b c d "), broadcast(' '))).isEqualTo(0b10101010);
    }

    @Test
    void cumulativeBitwiseXor() {
        assertThat(prefixXor(0b00100100)).isEqualTo(0b00011100);

        long msk = 0b00001000_10000111_00000011_00000000_10000100_01100000_10000011_11000010L;
        long res = 0b11111000_01111101_00000001_00000000_01111100_00100000_01111110_10111110L;
        assertThat(prefixXor(msk)).isEqualTo(res);
    }

    private static long stringToLong(String str) {
        return ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(str.getBytes(StandardCharsets.UTF_8))
                .flip()
                .getLong();
    }
}

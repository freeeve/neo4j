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
package org.neo4j.cypher.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UUIDGenerationTest {

    @Test
    void testWorkingGenerationGivesVersion7() {
        // Should not throw for valid currentTimeMillis() timestamp
        long timestamp = System.currentTimeMillis();
        UUID u = CypherFunctions.ofEpochMillis(timestamp);
        assertEquals(7, u.version());
    }

    @Test
    void testThrowsForInvalidTimestamp() {
        var value = 1L << 48;
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CypherFunctions.ofEpochMillis(value);
        });
    }

    @Test
    void testThrowsForNegativeTimestamp() {
        var value = -0xFEDCBA987654L;
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CypherFunctions.ofEpochMillis(value);
        });
    }

    @Test
    void testVariantIsIETF() {
        UUID u = CypherFunctions.ofEpochMillis(System.currentTimeMillis());
        assertEquals(2, u.variant());
    }

    @Test
    void testTimestampRoundTrips() {
        long timestamp = System.currentTimeMillis();
        UUID u = CypherFunctions.ofEpochMillis(timestamp);
        long extracted = u.getMostSignificantBits() >>> 16;
        assertEquals(timestamp, extracted);
    }

    @Test
    void testUniquenessWithSameTimestamp() {
        long timestamp = System.currentTimeMillis();
        Set<UUID> uuids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            uuids.add(CypherFunctions.ofEpochMillis(timestamp));
        }
        assertEquals(1000, uuids.size());
    }

    @Test
    void testTemporalOrdering() {
        UUID earlier = CypherFunctions.ofEpochMillis(1000L);
        UUID later = CypherFunctions.ofEpochMillis(2000L);
        assertTrue(earlier.compareTo(later) < 0);
    }

    @Test
    void testBoundaryTimestamps() {
        UUID atEpoch = CypherFunctions.ofEpochMillis(0L);
        assertEquals(7, atEpoch.version());
        assertEquals(2, atEpoch.variant());

        UUID atMax = CypherFunctions.ofEpochMillis((1L << 48) - 1);
        assertEquals(7, atMax.version());
        assertEquals(2, atMax.variant());
    }
}

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
package org.neo4j.io.pagecache.impl.muninn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EvictionClockArmTest {

    @Test
    void startClockWithZeroPage() {
        EvictionClockArm arm = new EvictionClockArm(5);
        assertEquals(0, arm.nextPage());
    }

    @Test
    void startClockWithCustomArm() {
        EvictionClockArm arm = new EvictionClockArm(10, 5);
        assertEquals(6, arm.nextPage());
        assertEquals(7, arm.nextPage());
    }

    @Test
    void switchToZeroAfterReachingEnd() {
        EvictionClockArm arm = new EvictionClockArm(3, 1);
        assertEquals(2, arm.nextPage());
        assertEquals(0, arm.nextPage());
        assertEquals(1, arm.nextPage());
    }

    @Test
    void pageCycles() {
        EvictionClockArm arm = new EvictionClockArm(3);
        int[] expected = {0, 1, 2, 0, 1, 2};
        for (int exp : expected) {
            assertEquals(exp, arm.nextPage());
        }
    }

    @Test
    void startClockOnTheLastPage() {
        EvictionClockArm arm = new EvictionClockArm(7, 6);
        assertEquals(0, arm.nextPage());
        assertEquals(1, arm.nextPage());
    }
}

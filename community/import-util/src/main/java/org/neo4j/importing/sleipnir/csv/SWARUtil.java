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

/**
 * SIMD within a register, or SWAR, is a technique for performing parallel operations on data contained
 * in a processor register.
 * <p>
 * This class is specialized for doing operations on individual bytes of longs, effectively providing
 * 8 lanes of 8 bit each.
 */
public final class SWARUtil {
    private static final long MOST_SIGNIFICANT_BITS = 0x8080808080808080L;
    private static final long LEAST_SIGNIFICANT_BITS = 0x0101010101010101L;

    private SWARUtil() {}

    /**
     * Broadcast a byte to all 8 bytes of a long.
     *
     * @param b byte to broadcast.
     * @return a long with all bytes set to the provided byte.
     */
    public static long broadcast(long b) {
        return (b & 0xffL) * LEAST_SIGNIFICANT_BITS;
    }

    /**
     * Get a bitmask of where in the {@code haystack} the {@code needle} exists, in the 8 least significant bits.
     * <p>
     * For example, searching for {@code 0x22}:
     * <pre>
     *     eqMask(0x1122334433221100, 0x2222222222222222) -> 0b01000100
     * </pre>
     *
     * @param haystack the haystack to search.
     * @param needle the needle to find.
     * @return A bitmask where the needle is found.
     */
    public static long eqMask(long haystack, long needle) {
        long x = haystack ^ needle;
        long matchMask = ((x >>> 1) | MOST_SIGNIFICANT_BITS) - x;
        return moveMask(matchMask);
    }

    /**
     * Takes the most significant bit from each 8-bit element in a 64-bit integer vector to create
     * an 8-bit mask value. Intel equivalent:
     * <pre>
     * int _mm_movemask_pi8(__m64 a)
     * </pre>
     *
     * @param mask a SWAR mask.
     * @return an 8-bit bitmap of the provided mask.
     */
    public static long moveMask(long mask) {
        return ((mask & MOST_SIGNIFICANT_BITS) * 0x02040810204081L) >>> 56;
    }

    /**
     * Perform a "cumulative bitwise xor" flipping bits each time a 1 is encountered.
     * <p>
     * For example, prefixXor(00100100) == 00011100
     * <p>
     * Ideally this should be done by using the {@code clmul} instruction, but this is not available in java.
     *
     * @param bitmask to perform prefix xor on.
     * @return the prefix xor'ed bitmask.
     */
    public static long prefixXor(long bitmask) {
        bitmask ^= bitmask << 1;
        bitmask ^= bitmask << 2;
        bitmask ^= bitmask << 4;
        bitmask ^= bitmask << 8;
        bitmask ^= bitmask << 16;
        bitmask ^= bitmask << 32;
        return bitmask;
    }

    /**
     * Take 64 bytes and return a bitmask of all occurrences of the {@code needle}.
     *
     * @param needle pattern to search for.
     * @param l0 byte 1 to 8.
     * @param l1 byte 9 to 16.
     * @param l2 byte 17 to 24.
     * @param l3 byte 25 to 32.
     * @param l4 byte 33 to 40.
     * @param l5 byte 41 to 48.
     * @param l6 byte 49 to 56.
     * @param l7 byte 57 to 64.
     * @return a bitmask of all occurrences of the {@code needle}.
     */
    static long eqMask(long needle, long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7) {
        return eqMask(l0, needle)
                | eqMask(l1, needle) << 8
                | eqMask(l2, needle) << 16
                | eqMask(l3, needle) << 24
                | eqMask(l4, needle) << 32
                | eqMask(l5, needle) << 40
                | eqMask(l6, needle) << 48
                | eqMask(l7, needle) << 56;
    }
}

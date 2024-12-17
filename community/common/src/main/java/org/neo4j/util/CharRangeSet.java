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
package org.neo4j.util;

import java.util.Arrays;
import java.util.Objects;

/** Immutable set of chars based on ranges. */
public class CharRangeSet {
    private final char[] ranges; // from 0, to 0 (inclusive), from 1, to 1 (inclusive), ...
    private final char max;

    public CharRangeSet(char[] ranges) {
        Preconditions.checkArgument(ranges.length % 2 == 0, "length needs to be even");
        Preconditions.checkArgument(isSorted(ranges), "ranges are sorted");
        this.ranges = ranges;
        this.max = ranges.length != 0 ? ranges[ranges.length - 1] : 0;
    }

    public boolean contains(int codepoint) {
        return codepoint >= 0 && codepoint <= max && contains((char) codepoint);
    }

    public boolean contains(char c) {
        // Could be a binary search, but I didn't bother because c is heavily skewed towards lower values.
        for (int i = 0; i < ranges.length; i += 2) {
            if (c <= ranges[i + 1]) return c >= ranges[i];
        }
        return false;
    }

    public int size() {
        int size = 0;
        for (int i = 0; i < ranges.length; i += 2) size += ranges[i + 1] - ranges[i] + 1;
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CharRangeSet that = (CharRangeSet) o;
        return Objects.deepEquals(ranges, that.ranges);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ranges);
    }

    @Override
    public String toString() {
        return "CharRangeSet{" + "ranges=" + Arrays.toString(ranges) + '}';
    }

    private static boolean isSorted(char[] array) {
        for (int i = 0; i < array.length; i += 2) {
            if (array[i] > array[i + 1]) {
                return false;
            }
            if (i + 2 < array.length && array[i + 1] >= array[i + 2]) {
                return false;
            }
        }
        return true;
    }
}

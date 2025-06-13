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
package org.neo4j.collection;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;

import java.util.Arrays;

/**
 * Specialized methods for operations on primitive arrays.
 *
 * For set operations (union, intersect, symmetricDifference), input and output arrays
 * are arrays containing unique values in sorted ascending order.
 */
public final class PrimitiveArrays {

    private PrimitiveArrays() {
        // No instances allowed
    }

    /**
     * Deduplicates values in the sorted {@code values} array.
     *
     * @param values sorted array of long values.
     * @return the provided array if no duplicates were found, otherwise a new shorter array w/o duplicates.
     */
    public static int[] deduplicate(int[] values) {
        if (values.length < 2) {
            return values;
        }
        int lastValue = values[0];
        int uniqueIndex = 1;
        for (int i = 1; i < values.length; i++) {
            int currentValue = values[i];
            if (currentValue != lastValue) {
                values[uniqueIndex] = currentValue;
                lastValue = currentValue;
                uniqueIndex++;
            }
        }
        return uniqueIndex < values.length ? Arrays.copyOf(values, uniqueIndex) : values;
    }

    /**
     * Compute union of two sets of integers represented as sorted arrays.
     *
     * @param lhs
     *         a set of integers, represented as a sorted array.
     * @param rhs
     *         a set of integers, represented as a sorted array.
     * @return a set of integers, represented as a sorted array.
     */
    // NOTE: this implementation was measured to be faster than an implementation
    // with countUnique for arrays on size 100+.
    public static int[] union(int[] lhs, int[] rhs) {
        if (lhs == null || rhs == null) {
            return lhs == null ? rhs : lhs;
        }

        assert isSortedSet(lhs) && isSortedSet(rhs);
        if (lhs.length < rhs.length) {
            return union(rhs, lhs);
        }
        int[] merged = null;
        int m = 0;
        int l = 0;
        for (int r = 0; l <= lhs.length && r < rhs.length; ) {
            while (l < lhs.length && lhs[l] < rhs[r]) {
                if (merged != null) {
                    merged[m++] = lhs[l];
                }
                l++;
            }
            if (l == lhs.length) {
                if (merged == null) {
                    merged = Arrays.copyOf(lhs, lhs.length + rhs.length - r);
                    m = l;
                }
                System.arraycopy(rhs, r, merged, m, rhs.length - r);
                m += rhs.length - r;
                break;
            }
            if (lhs[l] > rhs[r]) {
                if (merged == null) {
                    merged = Arrays.copyOf(lhs, lhs.length + rhs.length - r);
                    m = l;
                }
                merged[m++] = rhs[r++];
            } else // i.e. ( lhs[l] == rhs[r] )
            {
                if (merged != null) {
                    merged[m++] = lhs[l];
                }
                l++;
                r++;
            }
        }
        if (merged == null) {
            return lhs;
        }
        if (l < lhs.length) // get tail of lhs
        {
            System.arraycopy(lhs, l, merged, m, lhs.length - l);
            m += lhs.length - l;
        }
        if (m < merged.length) // truncate extra elements
        {
            merged = Arrays.copyOf(merged, m);
        }
        return merged;
    }

    /**
     * Compute the intersection of two sorted long array sets.
     * @param left a sorted array set
     * @param right another sorted array set
     * @return the intersection, represented as a sorted long array
     */
    public static int[] intersect(int[] left, int[] right) {
        if (left == null || right == null) {
            return EMPTY_INT_ARRAY;
        }

        assert isSortedSet(left) && isSortedSet(right);

        long uniqueCounts = countUnique(left, right);
        if (uniqueCounts == 0) // complete intersection
        {
            return right;
        }
        if (right(uniqueCounts) == right.length || left(uniqueCounts) == left.length) // non-intersecting
        {
            return EMPTY_INT_ARRAY;
        }

        int[] intersect = new int[left.length - left(uniqueCounts)];

        int cursor = 0;
        int l = 0;
        int r = 0;
        while (l < left.length && r < right.length) {
            if (left[l] == right[r]) {
                intersect[cursor++] = left[l];
                l++;
                r++;
            } else if (left[l] < right[r]) {
                l++;
            } else {
                r++;
            }
        }

        assert cursor == intersect.length;
        return intersect;
    }
    /**
     * Compute the symmetric difference (set XOR basically) of two sorted long array sets.
     * @param left a sorted array set
     * @param right another sorted array set
     * @return the symmetric difference, represented as a sorted long array
     */
    public static int[] symmetricDifference(int[] left, int[] right) {
        if (left == null || right == null) {
            return left == null ? right : left;
        }

        assert isSortedSet(left) && isSortedSet(right);

        long uniqueCounts = countUnique(left, right);
        if (uniqueCounts == 0) // complete intersection
        {
            return EMPTY_INT_ARRAY;
        }

        int[] difference = new int[left(uniqueCounts) + right(uniqueCounts)];

        int cursor = 0;
        int l = 0;
        int r = 0;
        while (l < left.length && r < right.length) {
            if (left[l] == right[r]) {
                l++;
                r++;
            } else if (left[l] < right[r]) {
                difference[cursor++] = left[l];
                l++;
            } else {
                difference[cursor++] = right[r];
                r++;
            }
        }
        while (l < left.length) {
            difference[cursor++] = left[l];
            l++;
        }
        while (r < right.length) {
            difference[cursor++] = right[r];
            r++;
        }

        assert cursor == difference.length;
        return difference;
    }

    public record RemovalsAndAdditions(int[] removals, int[] additions) {
        public static final RemovalsAndAdditions NO_CHANGES =
                new RemovalsAndAdditions(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RemovalsAndAdditions(int[] otherRemovals, int[] otherAdditions))) {
                return false;
            }
            return Arrays.equals(removals, otherRemovals) && Arrays.equals(additions, otherAdditions);
        }

        @Override
        public String toString() {
            return "AdditionsAndRemovals{" + "additions="
                    + Arrays.toString(additions) + ", removals="
                    + Arrays.toString(removals) + '}';
        }
    }

    public static RemovalsAndAdditions toRemovalsAndAdditions(int[] before, int[] after) {
        boolean beforeIsEmpty = nullOrEmpty(before);
        boolean afterIsEmpty = nullOrEmpty(after);
        if (beforeIsEmpty || afterIsEmpty) {
            return new RemovalsAndAdditions(
                    beforeIsEmpty ? EMPTY_INT_ARRAY : before, afterIsEmpty ? EMPTY_INT_ARRAY : after);
        }

        assert isSortedSet(before) && isSortedSet(after);

        long uniqueCounts = countUnique(before, after);
        if (uniqueCounts == 0) {
            return RemovalsAndAdditions.NO_CHANGES;
        }

        int leftCount = left(uniqueCounts);
        int[] removals = leftCount == 0 ? EMPTY_INT_ARRAY : new int[leftCount];
        int rightCount = right(uniqueCounts);
        int[] additions = rightCount == 0 ? EMPTY_INT_ARRAY : new int[rightCount];

        int removalsCursor = 0;
        int additionsCursor = 0;
        int l = 0;
        int r = 0;
        while (l < before.length && r < after.length) {
            if (before[l] == after[r]) {
                l++;
                r++;
            } else if (before[l] < after[r]) {
                removals[removalsCursor++] = before[l];
                l++;
            } else {
                additions[additionsCursor++] = after[r];
                r++;
            }
        }
        while (l < before.length) {
            removals[removalsCursor++] = before[l];
            l++;
        }
        while (r < after.length) {
            additions[additionsCursor++] = after[r];
            r++;
        }

        assert removalsCursor == removals.length;
        assert additionsCursor == additions.length;
        return new RemovalsAndAdditions(removals, additions);
    }

    private static boolean nullOrEmpty(int[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Compute the number of unique values in two sorted long array sets
     * @param left a sorted array set
     * @param right another sorted array set
     * @return int pair packed into long
     */
    static long countUnique(int[] left, int[] right) {
        int l = 0;
        int r = 0;
        int uniqueInLeft = 0;
        int uniqueInRight = 0;
        while (l < left.length && r < right.length) {
            if (left[l] == right[r]) {
                l++;
                r++;
            } else if (left[l] < right[r]) {
                uniqueInLeft++;
                l++;
            } else {
                uniqueInRight++;
                r++;
            }
        }
        uniqueInLeft += left.length - l;
        uniqueInRight += right.length - r;
        return intPair(uniqueInLeft, uniqueInRight);
    }

    public static boolean contains(int[] list, int value) {
        for (int x : list) {
            if (value == x) {
                return true;
            }
        }
        return false;
    }

    public static int[] subtract(int[] sortedItems, int... sortedItemsToRemove) {
        if (sortedItems == null) {
            return null;
        }
        if (sortedItemsToRemove == null) {
            return sortedItems;
        }

        int[] result = new int[sortedItems.length];
        int i1 = 0;
        int ri = 0;
        for (int i2 = 0; i1 < sortedItems.length && i2 < sortedItemsToRemove.length; ) {
            int item = sortedItems[i1];
            int itemToRemove = sortedItemsToRemove[i2];
            if (item < itemToRemove) {
                result[ri++] = item;
                i1++;
            } else {
                if (item == itemToRemove) {
                    i1++;
                }
                i2++;
            }
        }
        int remaining = sortedItems.length - i1;
        if (remaining > 0) {
            System.arraycopy(sortedItems, i1, result, ri, remaining);
            ri += remaining;
        }
        return ri < result.length ? Arrays.copyOf(result, ri) : result;
    }

    private static long intPair(int left, int right) {
        return (((long) left) << Integer.SIZE) | right;
    }

    private static int left(long pair) {
        return (int) (pair >> Integer.SIZE);
    }

    private static int right(long pair) {
        return (int) (pair & 0xFFFF_FFFFL);
    }

    private static boolean isSortedSet(int[] set) {
        for (int i = 0; i < set.length - 1; i++) {
            assert set[i] < set[i + 1] : "Array is not a sorted set: has " + set[i] + " before " + set[i + 1];
        }
        return true;
    }
}

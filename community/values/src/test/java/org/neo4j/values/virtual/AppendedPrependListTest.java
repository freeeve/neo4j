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
package org.neo4j.values.virtual;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.internal.helpers.collection.Iterators.iteratorsEqual;
import static org.neo4j.values.storable.Values.NO_VALUE;
import static org.neo4j.values.storable.Values.doubleValue;
import static org.neo4j.values.storable.Values.floatValue;
import static org.neo4j.values.storable.Values.intValue;
import static org.neo4j.values.storable.Values.longValue;
import static org.neo4j.values.virtual.ListValue.LIST_DEPTH_COMPACTION_THRESHOLD;
import static org.neo4j.values.virtual.VirtualValues.fromArray;
import static org.neo4j.values.virtual.VirtualValues.list;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.BooleanValue;

class AppendedPrependListTest {

    @Test
    void shouldContainsOnAppendList() {
        // Given
        // When
        ListValue list = list(longValue(1L)).append(longValue(2L)).append(longValue(3L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), BooleanValue.FALSE);
    }

    @Test
    void shouldContainsOnAppendListWithNulls() {
        // Given
        // When
        ListValue list = list(longValue(1L)).append(NO_VALUE).append(longValue(3L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), NO_VALUE);
    }

    @Test
    void shouldContainsOnAppendListAroundLongerInner() {
        // Given
        // When
        ListValue list = list(longValue(1L), longValue(2L), longValue(3L))
                .append(longValue(4L))
                .append(longValue(5L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(6L)), BooleanValue.FALSE);
    }

    @Test
    void shouldContainsOnAppendListAroundLongerInnerWithNulls() {
        // Given
        // When
        ListValue list = list(longValue(1L), NO_VALUE, longValue(3L))
                .append(longValue(4L))
                .append(longValue(5L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(6L)), NO_VALUE);
    }

    @Test
    void shouldContainsOnPrependList() {
        // Given
        // When
        ListValue list = list(longValue(1L)).prepend(longValue(2L)).prepend(longValue(3L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), BooleanValue.FALSE);
    }

    @Test
    void shouldContainsOnPrependListWithNulls() {
        // Given
        // When
        ListValue list = list(longValue(1L)).prepend(NO_VALUE).prepend(longValue(3L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), NO_VALUE);
    }

    @Test
    void shouldContainsOnPrependListAroundLongerInner() {
        // Given
        // When
        ListValue list = list(longValue(1L), longValue(2L), longValue(3L))
                .prepend(longValue(4L))
                .prepend(longValue(5L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(6L)), BooleanValue.FALSE);
    }

    @Test
    void shouldContainsOnPrependListAroundLongerInnerWithNulls() {
        // Given
        // When
        ListValue list = list(longValue(1L), NO_VALUE, longValue(3L))
                .prepend(longValue(4L))
                .prepend(longValue(5L));

        // Then
        assertEquals(list.ternaryContains(longValue(1L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(4L)), BooleanValue.TRUE);
        assertEquals(list.ternaryContains(longValue(6L)), NO_VALUE);
    }

    @Test
    void shouldAppendToList() {
        // Given
        ListValue inner = list(
                longValue(5L),
                longValue(6L),
                longValue(7L),
                longValue(8L),
                longValue(9L),
                longValue(10L),
                longValue(11L));

        // When
        ListValue appended = inner.append(longValue(12L));

        // Then
        ListValue expected = list(
                longValue(5L),
                longValue(6L),
                longValue(7L),
                longValue(8L),
                longValue(9L),
                longValue(10L),
                longValue(11L),
                longValue(12L));
        assertListValuesEquals(expected, appended);
    }

    @Test
    void shouldAppendNoValue() {
        // Given
        ListValue inner = list(longValue(5L), longValue(6L));

        // When
        ListValue appended = inner.append(NO_VALUE);

        // Then
        ListValue expected = list(longValue(5L), longValue(6L), NO_VALUE);
        assertListValuesEquals(expected, appended);
    }

    @Test
    void shouldReverseAppendToList() {
        // Given
        ListValue inner = list(
                longValue(5L),
                longValue(6L),
                longValue(7L),
                longValue(8L),
                longValue(9L),
                longValue(10L),
                longValue(11L));

        // When
        ListValue appended = inner.append(longValue(12L)).reverse();

        // Then
        ListValue expected = list(
                longValue(12L),
                longValue(11L),
                longValue(10L),
                longValue(9L),
                longValue(8L),
                longValue(7L),
                longValue(6L),
                longValue(5L));
        assertListValuesEquals(expected, appended);
    }

    @Test
    void shouldPrependToList() {
        // Given
        ListValue inner = list(
                longValue(5L),
                longValue(6L),
                longValue(7L),
                longValue(8L),
                longValue(9L),
                longValue(10L),
                longValue(11L));

        // When
        ListValue prepend = inner.prepend(longValue(4L));

        // Then
        ListValue expected = list(
                longValue(4L),
                longValue(5L),
                longValue(6L),
                longValue(7L),
                longValue(8L),
                longValue(9L),
                longValue(10L),
                longValue(11L));
        assertListValuesEquals(expected, prepend);
    }

    @Test
    void shouldPrependNoValue() {
        // Given
        ListValue inner = list(longValue(5L), longValue(6L));

        // When
        ListValue prepended = inner.prepend(NO_VALUE);

        // Then
        ListValue expected = list(NO_VALUE, longValue(5L), longValue(6L));
        assertListValuesEquals(expected, prepended);
    }

    @Test
    void shouldBeStorableIfPrependedMatchesInnerStorable() {
        // Given
        ListValue inner = list(longValue(5L), longValue(6L));

        // When
        ListValue prepended = inner.prepend(longValue(4L));

        // Then
        assertEquals(list(longValue(4L), longValue(5L), longValue(6L)), fromArray(prepended.toStorableArray()));
    }

    @Test
    void shouldBeStorableIfAppendedMatchesInnerStorable() {
        // Given
        ListValue inner = list(longValue(5L), longValue(6L));

        // When
        ListValue appended = inner.append(longValue(7L));

        // Then
        assertEquals(list(longValue(5L), longValue(6L), longValue(7L)), fromArray(appended.toStorableArray()));
    }

    @Test
    void shouldBeStorableIfAppendedMatchesInnerStorableWithDifferentButCompatibleTypes() {
        // Given
        ListValue inner = list(longValue(5L), longValue(6L));

        // When
        ListValue appended = inner.append(intValue(7));

        // Then
        assertEquals(list(longValue(5L), longValue(6L), longValue(7L)), fromArray(appended.toStorableArray()));
    }

    @Test
    void foo() {
        // Given
        ListValue inner = list(doubleValue(5), doubleValue(6));

        // When
        ListValue prepended = inner.prepend(floatValue(4f));

        // Then
        assertEquals(list(longValue(4L), longValue(5L), longValue(6L)), fromArray(prepended.toStorableArray()));
    }

    @Test
    void shouldCompactToArrayWhenOverThreshold() {
        var expected = new ArrayList<AnyValue>();
        expected.add(intValue(0));

        ListValue inner = list(intValue(0));
        for (int i = 1; i <= LIST_DEPTH_COMPACTION_THRESHOLD; i++) {
            if (i % 2 == 0) {
                inner = inner.append(intValue(i));
                expected.add(intValue(i));
            } else {
                inner = inner.prepend(intValue(i));
                expected.addFirst(intValue(i));
            }
        }

        assertInstanceOf(ListValue.ArrayListValue.class, inner);
        assertArrayEquals(expected.toArray(), inner.asArray());
    }

    @Test
    void shouldReCompactToArrayWhenOverThreshold() {
        var expected = new ArrayList<AnyValue>();
        expected.add(intValue(0));

        ListValue inner = list(intValue(0));
        for (int i = 1; i <= LIST_DEPTH_COMPACTION_THRESHOLD * 2; i++) {
            if (i % 2 == 0) {
                inner = inner.append(intValue(i));
                expected.add(intValue(i));
            } else {
                inner = inner.prepend(intValue(i));
                expected.addFirst(intValue(i));
            }
        }

        assertInstanceOf(ListValue.ArrayListValue.class, inner);
        assertArrayEquals(expected.toArray(), inner.asArray());
    }

    @Test
    void shouldBeAbleToTraverseQuiteLongList() {
        var expected = new ArrayList<AnyValue>();
        expected.add(intValue(0));

        ListValue inner = list(intValue(0));
        for (int i = 1; i <= 10000; i++) {
            if (i % 3 == 0) {
                inner = inner.append(intValue(i));
                expected.add(intValue(i));
            } else if (i % 3 == 1) {
                inner = inner.prepend(intValue(i));
                expected.addFirst(intValue(i));
            } else {
                inner = inner.appendAll(list(intValue(-i), intValue(i)));
                expected.add(intValue(-i));
                expected.add(intValue(i));
            }
        }

        int i = 0;
        for (AnyValue x : inner) {
            assertEquals(expected.get(i), x);
            i++;
        }
    }

    @Test
    void dropUnwrapsPrependList() {
        var base = list(intValue(0));
        var input = base.prepend(intValue(1)).prepend(intValue(2));

        var dropped = input.drop(2);
        assertSame(dropped, base);
    }

    @Test
    void takeUnwrapsAppendList() {
        var base = list(intValue(0));
        var input = base.append(intValue(1)).append(intValue(2));

        var taken = input.take(1);
        assertSame(taken, base);
    }

    @Test
    void sliceUnwrapsPrependList() {
        var base = list(intValue(0), intValue(1), intValue(2));
        var input = base.prepend(intValue(3));

        var sliced = input.slice(1, 2);
        assertListValuesEquals(list(intValue(0)), sliced);
    }

    @Test
    void sliceUnwrapsAppendList() {
        var base = list(intValue(0), intValue(1), intValue(2));
        var input = base.append(intValue(3));

        var sliced = input.slice(1, 2);
        assertListValuesEquals(list(intValue(1)), sliced);
    }

    @Test
    void sliceUnwrapsPrependAppendList() {
        var base = list(intValue(0));
        var input = base.prepend(intValue(1)).append(intValue(2));

        var sliced = input.slice(1, 2);
        assertSame(sliced, base);
    }

    private static void assertListValuesEquals(ListValue expected, ListValue appended) {
        assertEquals(expected, appended);
        assertEquals(expected.hashCode(), appended.hashCode());
        assertArrayEquals(expected.asArray(), appended.asArray());
        assertTrue(iteratorsEqual(expected.iterator(), appended.iterator()));
    }
}

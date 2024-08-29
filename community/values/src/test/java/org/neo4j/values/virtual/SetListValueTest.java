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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.neo4j.values.storable.Values.FALSE;
import static org.neo4j.values.storable.Values.NO_VALUE;
import static org.neo4j.values.storable.Values.TRUE;
import static org.neo4j.values.storable.Values.longValue;
import static org.neo4j.values.virtual.VirtualValues.list;

import org.junit.jupiter.api.Test;
import org.neo4j.values.AnyValue;

class SetListValueTest {

    @Test
    void shouldHandleDuplicateValues() {
        var set = setListOf(longValue(1), longValue(2), longValue(1), longValue(3));

        var expected = list(longValue(1L), longValue(2L), longValue(3L));

        assertThat(set).isEqualTo(expected);
        assertThat(set).hasSameHashCodeAs(expected);
    }

    @Test
    void shouldSupportRandomAccess() {
        var set = setListOf(longValue(1), longValue(2), longValue(1), longValue(3));

        assertThat(set.value(0)).isEqualTo(longValue(1));
        assertThat(set.value(1)).isEqualTo(longValue(2));
        assertThat(set.value(2)).isEqualTo(longValue(3));
        assertThatThrownBy(() -> set.value(3)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void shouldContainsOnSetList() {
        // Given
        // When
        ListValue list = setListOf(longValue(1L), longValue(2L));

        // Then
        assertThat(list.ternaryContains(longValue(1L))).isEqualTo(TRUE);
        assertThat(list.ternaryContains(longValue(3L))).isEqualTo(FALSE);
    }

    @Test
    void shouldContainsOnNestedListsWithNulls() {
        // Given
        // When
        ListValue list = setListOf(
                VirtualValues.list(longValue(1L), NO_VALUE, longValue(3L)),
                VirtualValues.list(longValue(1L), longValue(2L)),
                VirtualValues.list(longValue(2L), longValue(3L)));

        // Then
        assertThat(list.ternaryContains(VirtualValues.list(longValue(1L), longValue(2L))))
                .isEqualTo(TRUE);
        assertThat(list.ternaryContains(VirtualValues.list(longValue(2L), longValue(3L))))
                .isEqualTo(TRUE);
        assertThat(list.ternaryContains(VirtualValues.list(longValue(1L), longValue(3L))))
                .isEqualTo(FALSE);
        assertThat(list.ternaryContains(VirtualValues.list(longValue(1L), NO_VALUE, longValue(3L))))
                .isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(VirtualValues.list(NO_VALUE, NO_VALUE, NO_VALUE)))
                .isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(VirtualValues.list(NO_VALUE, NO_VALUE))).isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(VirtualValues.list(NO_VALUE))).isEqualTo(FALSE);
    }

    @Test
    void shouldContainsOnNestedMapsWithNulls() {
        // Given
        // When
        ListValue list = setListOf(
                mapValueOf("k1", longValue(1L), "k2", NO_VALUE, "k3", longValue(3L)),
                mapValueOf("k2", longValue(2L), "k3", longValue(3L)),
                mapValueOf("k1", longValue(1L), "k2", longValue(2L)));

        // Then
        assertThat(list.ternaryContains(mapValueOf("k1", longValue(1L), "k2", longValue(2L))))
                .isEqualTo(TRUE);
        assertThat(list.ternaryContains(mapValueOf("k2", longValue(2L), "k3", longValue(3L))))
                .isEqualTo(TRUE);
        assertThat(list.ternaryContains(mapValueOf("k1", longValue(1L), "k3", longValue(3L))))
                .isEqualTo(FALSE);
        assertThat(list.ternaryContains(mapValueOf("k1", longValue(1L), "k2", NO_VALUE, "k3", longValue(3L))))
                .isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(mapValueOf("k1", NO_VALUE, "k2", NO_VALUE, "k3", NO_VALUE)))
                .isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(mapValueOf("k1", NO_VALUE, "k2", NO_VALUE, "k3", NO_VALUE, "k4", NO_VALUE)))
                .isEqualTo(FALSE);
        assertThat(list.ternaryContains(mapValueOf("k1", NO_VALUE, "k2", NO_VALUE, "k4", NO_VALUE)))
                .isEqualTo(FALSE);
        assertThat(list.ternaryContains(mapValueOf("k1", NO_VALUE, "k2", NO_VALUE)))
                .isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(mapValueOf("k2", NO_VALUE, "k3", NO_VALUE)))
                .isEqualTo(NO_VALUE);
        assertThat(list.ternaryContains(mapValueOf("k1", NO_VALUE, "k3", NO_VALUE)))
                .isEqualTo(FALSE);
        assertThat(list.ternaryContains(mapValueOf("k1", NO_VALUE))).isEqualTo(FALSE);
    }

    @Test
    void shouldDropInSetList() {
        var set = setListOf(longValue(1), longValue(2), longValue(1), longValue(3))
                .drop(2);

        var expected = list(longValue(3L));

        assertThat(set).isEqualTo(expected);
        assertThat(set).hasSameHashCodeAs(expected);
    }

    // NOTE: iterating though a reversed set list will be very slow (O(N^2)). At the moment of writing
    // this, no production code should never do this but we should improve this at some point.
    @Test
    void shouldReversSetList() {
        var set = setListOf(longValue(1), longValue(2), longValue(1), longValue(3))
                .reverse();

        var expected = list(longValue(3L), longValue(2L), longValue(1L));

        assertThat(set).isEqualTo(expected);
        assertThat(set).hasSameHashCodeAs(expected);
    }

    private SetListValue setListOf(AnyValue... values) {
        var builder = SetListValue.builder();
        for (AnyValue value : values) {
            builder.add(value);
        }
        return builder.build();
    }

    private MapValue mapValueOf(Object... kv) {
        var b = new MapValueBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            b.add((String) kv[i], (AnyValue) kv[i + 1]);
        }
        return b.build();
    }
}

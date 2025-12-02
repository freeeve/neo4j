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
package org.neo4j.values;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.values.storable.Values.intValue;
import static org.neo4j.values.storable.Values.stringValue;
import static org.neo4j.values.virtual.VirtualValues.list;

import org.junit.jupiter.api.Test;

public class SequenceValueTest {
    @Test
    public void flattenIsStackSafe() {
        final var bottom = stringValue("hello");
        var list = list(bottom);
        for (int i = 0; i < 100_000; ++i) list = list(list);
        assertThat(list.flatten(Integer.MAX_VALUE)).isEqualTo(list(bottom));
    }

    @Test
    void flattenWithZeroDepth() {
        assertThat(list().flatten(0)).isEqualTo(list());
        assertThat(list(list()).flatten(0)).isEqualTo(list(list()));
        assertThat(list(list(list())).flatten(0)).isEqualTo(list(list(list())));
        assertThat(list(stringValue("hej")).flatten(0)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(stringValue("hej"))).flatten(0)).isEqualTo(list(list(stringValue("hej"))));
        assertThat(list(list(), list(stringValue("hej"))).flatten(0)).isEqualTo(list(list(), list(stringValue("hej"))));
    }

    @Test
    void flattenWithDepthOne() {
        assertThat(list().flatten(1)).isEqualTo(list());
        assertThat(list(list()).flatten(1)).isEqualTo(list());
        assertThat(list(list(list())).flatten(1)).isEqualTo(list(list()));
        assertThat(list(stringValue("hej")).flatten(1)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(stringValue("hej"))).flatten(1)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(), list(stringValue("hej")), list()).flatten(1)).isEqualTo(list(stringValue("hej")));
        assertThat(list(intValue(1), list(list(intValue(2))), list(list(list(intValue(3)))))
                        .flatten(1))
                .isEqualTo(list(intValue(1), list(intValue(2)), list(list(intValue(3)))));
        assertThat(list(
                                intValue(1),
                                list(list(intValue(2)), intValue(3)),
                                list(intValue(4), list(intValue(5), list(intValue(6), intValue(7)), intValue(8))),
                                intValue(9),
                                intValue(10))
                        .flatten(1))
                .isEqualTo(list(
                        intValue(1),
                        list(intValue(2)),
                        intValue(3),
                        intValue(4),
                        list(intValue(5), list(intValue(6), intValue(7)), intValue(8)),
                        intValue(9),
                        intValue(10)));
    }

    @Test
    void flattenWithDepthTwo() {
        assertThat(list().flatten(2)).isEqualTo(list());
        assertThat(list(list()).flatten(2)).isEqualTo(list());
        assertThat(list(list(list())).flatten(2)).isEqualTo(list());
        assertThat(list(stringValue("hej")).flatten(2)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(stringValue("hej"))).flatten(2)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(), list(stringValue("hej")), list()).flatten(2)).isEqualTo(list(stringValue("hej")));
        assertThat(list(intValue(1), list(list(intValue(2))), list(list(list(intValue(3)))))
                        .flatten(2))
                .isEqualTo(list(intValue(1), intValue(2), list(intValue(3))));
        assertThat(list(
                                intValue(1),
                                list(list(intValue(2)), intValue(3)),
                                list(intValue(4), list(intValue(5), list(intValue(6), intValue(7)), intValue(8))),
                                intValue(9),
                                intValue(10))
                        .flatten(2))
                .isEqualTo(list(
                        intValue(1),
                        intValue(2),
                        intValue(3),
                        intValue(4),
                        intValue(5),
                        list(intValue(6), intValue(7)),
                        intValue(8),
                        intValue(9),
                        intValue(10)));
    }

    @Test
    void flattenWithDepthThree() {
        assertThat(list().flatten(3)).isEqualTo(list());
        assertThat(list(list()).flatten(3)).isEqualTo(list());
        assertThat(list(list(list())).flatten(3)).isEqualTo(list());
        assertThat(list(stringValue("hej")).flatten(3)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(stringValue("hej"))).flatten(3)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(), list(stringValue("hej")), list()).flatten(3)).isEqualTo(list(stringValue("hej")));
        assertThat(list(intValue(1), list(list(intValue(2))), list(list(list(intValue(3)))))
                        .flatten(3))
                .isEqualTo(list(intValue(1), intValue(2), intValue(3)));
        assertThat(list(
                                intValue(1),
                                list(list(intValue(2)), intValue(3)),
                                list(intValue(4), list(intValue(5), list(intValue(6), intValue(7)), intValue(8))),
                                intValue(9),
                                intValue(10))
                        .flatten(3))
                .isEqualTo(list(
                        intValue(1),
                        intValue(2),
                        intValue(3),
                        intValue(4),
                        intValue(5),
                        intValue(6),
                        intValue(7),
                        intValue(8),
                        intValue(9),
                        intValue(10)));
    }

    @Test
    void flattenWithDepthFour() {
        assertThat(list().flatten(4)).isEqualTo(list());
        assertThat(list(list()).flatten(4)).isEqualTo(list());
        assertThat(list(list(list())).flatten(4)).isEqualTo(list());
        assertThat(list(stringValue("hej")).flatten(4)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(stringValue("hej"))).flatten(4)).isEqualTo(list(stringValue("hej")));
        assertThat(list(list(), list(stringValue("hej")), list()).flatten(4)).isEqualTo(list(stringValue("hej")));
        assertThat(list(intValue(1), list(list(intValue(2))), list(list(list(intValue(3)))))
                        .flatten(4))
                .isEqualTo(list(intValue(1), intValue(2), intValue(3)));
        assertThat(list(
                                intValue(1),
                                list(list(intValue(2)), intValue(3)),
                                list(intValue(4), list(intValue(5), list(intValue(6), intValue(7)), intValue(8))),
                                intValue(9),
                                intValue(10))
                        .flatten(4))
                .isEqualTo(list(
                        intValue(1),
                        intValue(2),
                        intValue(3),
                        intValue(4),
                        intValue(5),
                        intValue(6),
                        intValue(7),
                        intValue(8),
                        intValue(9),
                        intValue(10)));
    }
}

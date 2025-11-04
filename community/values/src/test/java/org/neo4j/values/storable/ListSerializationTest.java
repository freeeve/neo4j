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
package org.neo4j.values.storable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.neo4j.values.virtual.VirtualValues;

public class ListSerializationTest {
    @Test
    void shouldSerializeSimpleSequenceNicely() {
        double[] values = {1.3, 5.1235351, 876.111, 567890.345678, 1.11, 0.59};
        var mySeq = new DoubleArray(values);
        assertEquals(
                "[1.3, 5.1235351, 876.111, ...]", ValueRepresentation.serializeList(mySeq, new DoubleValue(5.1235351)));
        assertEquals("[..., 1.11, 0.59]", ValueRepresentation.serializeList(mySeq, new DoubleValue(0.59)));
        assertEquals(
                "[..., 876.111, 567890.345678, 1.11, ...]",
                ValueRepresentation.serializeList(mySeq, new DoubleValue(567890.345678)));
        String[] shortValues = {"abcd", "12-34"};
        var shortSeq = Values.stringArray(shortValues);
        assertEquals(
                "['abcd', '12-34']",
                ValueRepresentation.serializeList(shortSeq, new StringWrappingStringValue("abcd")));
        assertEquals(
                "['abcd', '12-34']",
                ValueRepresentation.serializeList(shortSeq, new StringWrappingStringValue("12-34")));
    }

    @Test
    void shouldSerializeNestedSequenceNicely() {
        var nestedSequences = VirtualValues.list(
                VirtualValues.list(new IntValue(1), new IntValue(4), new IntValue(7)),
                VirtualValues.list(new IntValue(5), new IntValue(6)),
                VirtualValues.list(new IntValue(1), new IntValue(2), new IntValue(3)),
                VirtualValues.list(new IntValue(7)));
        assertEquals(
                "[..., [5, 6], [1, 2, ...], [7]]",
                ValueRepresentation.serializeList(
                        nestedSequences, VirtualValues.list(new IntValue(1), new IntValue(2), new IntValue(3))));
        var nestedSequences2 = VirtualValues.list(
                new IntValue(3),
                VirtualValues.list(new IntValue(1000), new IntValue(2000), new IntValue(1)),
                VirtualValues.list(new IntValue(1), new IntValue(2), new IntValue(3)),
                new IntValue(7));
        assertEquals(
                "[3, [1000, 2000, ...], ...]", ValueRepresentation.serializeList(nestedSequences2, new IntValue(3)));
    }

    @Test
    void shouldSerializeNestedEmptySequenceNicely() {
        var nestedSequences = VirtualValues.list(
                VirtualValues.list(new IntValue(1), new IntValue(4), new IntValue(7)),
                VirtualValues.EMPTY_LIST,
                VirtualValues.list(new IntValue(1), new IntValue(2), new IntValue(3)),
                VirtualValues.EMPTY_LIST,
                VirtualValues.list(new IntValue(7)));
        assertEquals(
                "[..., [], [1, 2, ...], [], ...]",
                ValueRepresentation.serializeList(
                        nestedSequences, VirtualValues.list(new IntValue(1), new IntValue(2), new IntValue(3))));
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.values.storable.Values.float32Vector;
import static org.neo4j.values.storable.Values.float64Vector;
import static org.neo4j.values.storable.Values.int16Vector;
import static org.neo4j.values.storable.Values.int32Vector;
import static org.neo4j.values.storable.Values.int64Vector;
import static org.neo4j.values.storable.Values.int8Vector;

import org.junit.jupiter.api.Test;
import org.neo4j.exceptions.CypherTypeException;
import org.neo4j.kernel.impl.util.ValueUtils;
import org.neo4j.values.SequenceValue;
import org.neo4j.values.virtual.ListValueBuilder;

class VectorUtilsTest {
    @Test
    void shouldParseInt8Vector() {
        assertThat(VectorUtils.int8Vector(sequenceVector(1, 2, 3, 4)))
                .isEqualTo(int8Vector((byte) 1, (byte) 2, (byte) 3, (byte) 4));
        assertThat(VectorUtils.int8Vector(sequenceVector(
                        (int) Byte.MIN_VALUE - 1, Byte.MIN_VALUE, Byte.MAX_VALUE, (int) Byte.MAX_VALUE + 1)))
                .isEqualTo(int8Vector(Byte.MAX_VALUE, Byte.MIN_VALUE, Byte.MAX_VALUE, Byte.MIN_VALUE));
        assertThat(VectorUtils.int8Vector(sequenceVector(1, 2.0, 3.0, 4.0)))
                .isEqualTo(int8Vector((byte) 1, (byte) 2, (byte) 3, (byte) 4));
        assertThatThrownBy(() -> VectorUtils.int8Vector(sequenceVector(1, 2, "three", 4)))
                .isInstanceOf(CypherTypeException.class);
    }

    @Test
    void shouldParseInt16Vector() {
        assertThat(VectorUtils.int16Vector(sequenceVector(1, 2, 3, 4)))
                .isEqualTo(int16Vector((short) 1, (short) 2, (short) 3, (short) 4));
        assertThat(VectorUtils.int16Vector(sequenceVector(
                        (int) Short.MIN_VALUE - 1, Short.MIN_VALUE, Short.MAX_VALUE, (int) Short.MAX_VALUE + 1)))
                .isEqualTo(int16Vector(Short.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Short.MIN_VALUE));
        assertThat(VectorUtils.int16Vector(sequenceVector(1, 2.0, 3.0, 4.0)))
                .isEqualTo(int16Vector((short) 1, (short) 2, (short) 3, (short) 4));
        assertThatThrownBy(() -> VectorUtils.int16Vector(sequenceVector(1, 2, "three", 4)))
                .isInstanceOf(CypherTypeException.class);
    }

    @Test
    void shouldParseInt32Vector() {
        assertThat(VectorUtils.int32Vector(sequenceVector(1, 2, 3, 4))).isEqualTo(int32Vector(1, 2, 3, 4));
        assertThat(VectorUtils.int32Vector(sequenceVector(
                        (long) Integer.MIN_VALUE - 1L,
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        (long) Integer.MAX_VALUE + 1L)))
                .isEqualTo(int32Vector(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertThat(VectorUtils.int32Vector(sequenceVector(1, 2.0, 3.0, 4.0))).isEqualTo(int32Vector(1, 2, 3, 4));
        assertThatThrownBy(() -> VectorUtils.int32Vector(sequenceVector(1, 2, "three", 4)))
                .isInstanceOf(CypherTypeException.class);
    }

    @Test
    void shouldParseInt64Vector() {
        assertThat(VectorUtils.int64Vector(sequenceVector(1, 2, 3, 4))).isEqualTo(int64Vector(1, 2, 3, 4));
        assertThat(VectorUtils.int64Vector(
                        sequenceVector(Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE)))
                .isEqualTo(int64Vector(Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE));
        assertThat(VectorUtils.int64Vector(sequenceVector(1, 2.0, 3.0, 4.0))).isEqualTo(int64Vector(1, 2, 3, 4));
        assertThatThrownBy(() -> VectorUtils.int64Vector(sequenceVector(1, 2, "three", 4)))
                .isInstanceOf(CypherTypeException.class);
    }

    @Test
    void shouldParseFloat32Vector() {
        assertThat(VectorUtils.float32Vector(sequenceVector(1, 2, 3, 4))).isEqualTo(float32Vector(1, 2, 3, 4));
        assertThat(VectorUtils.float32Vector(sequenceVector(Float.MIN_VALUE, Float.MAX_VALUE)))
                .isEqualTo(float32Vector(Float.MIN_VALUE, Float.MAX_VALUE));
        assertThat(VectorUtils.float32Vector(sequenceVector(1, 2.0, 3.0, 4.0))).isEqualTo(float32Vector(1, 2, 3, 4));
        assertThatThrownBy(() -> VectorUtils.float32Vector(sequenceVector(1, 2, "three", 4)))
                .isInstanceOf(CypherTypeException.class);

        assertThatThrownBy(() -> VectorUtils.float32Vector(sequenceVector(Float.POSITIVE_INFINITY)))
                .isInstanceOf(org.neo4j.exceptions.ArithmeticException.class);
        assertThatThrownBy(() -> VectorUtils.float32Vector(sequenceVector(Float.NEGATIVE_INFINITY)))
                .isInstanceOf(org.neo4j.exceptions.ArithmeticException.class);
    }

    @Test
    void shouldParseFloat64Vector() {
        assertThat(VectorUtils.float64Vector(sequenceVector(1, 2, 3, 4))).isEqualTo(float64Vector(1, 2, 3, 4));
        assertThat(VectorUtils.float64Vector(sequenceVector(4.9E-324, 1.7976931348623157E308)))
                .isEqualTo(float64Vector(Double.MIN_VALUE, Double.MAX_VALUE));
        assertThat(VectorUtils.float64Vector(sequenceVector(1, 2.0, 3.0, 4.0))).isEqualTo(float64Vector(1, 2, 3, 4));
        assertThatThrownBy(() -> VectorUtils.float64Vector(sequenceVector(1, 2, "three", 4)))
                .isInstanceOf(CypherTypeException.class);

        assertThatThrownBy(() -> VectorUtils.float64Vector(sequenceVector(Double.POSITIVE_INFINITY)))
                .isInstanceOf(org.neo4j.exceptions.ArithmeticException.class);
        assertThatThrownBy(() -> VectorUtils.float64Vector(sequenceVector(Double.NEGATIVE_INFINITY)))
                .isInstanceOf(org.neo4j.exceptions.ArithmeticException.class);
    }

    private SequenceValue sequenceVector(Object... values) {
        var builder = ListValueBuilder.newListBuilder(values.length);
        for (Object value : values) {
            builder.add(ValueUtils.of(value));
        }
        return builder.build();
    }
}

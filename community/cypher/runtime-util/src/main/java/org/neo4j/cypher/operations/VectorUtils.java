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

import static java.lang.String.format;

import java.util.List;
import org.neo4j.exceptions.CypherTypeException;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.values.AnyValue;
import org.neo4j.values.SequenceValue;
import org.neo4j.values.storable.ArrayValue;
import org.neo4j.values.storable.ByteArray;
import org.neo4j.values.storable.DoubleArray;
import org.neo4j.values.storable.FloatArray;
import org.neo4j.values.storable.IntArray;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.LongArray;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.ShortArray;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;
import org.neo4j.values.storable.VectorValue;
import org.neo4j.values.virtual.ListValue;

final class VectorUtils {
    private VectorUtils() {
        throw new UnsupportedOperationException("Do not instantiate");
    }

    static VectorValue vectorFromListValue(ListValue listValue) {
        try {
            return vectorFromArrayValue(listValue.toStorableArray());
        } catch (CypherTypeException e) {
            throw CypherTypeException.functionArgumentWrongType(
                    format("Invalid input for function 'vector()': Expected a List of Numbers but got %s", listValue),
                    "vector",
                    listValue.toString(),
                    List.of("LIST<INTEGER | FLOAT>"),
                    listValue.getTypeName());
        }
    }

    private static VectorValue vectorFromArrayValue(ArrayValue arrayValue) {
        return switch (arrayValue) {
            case ByteArray byteArray -> Values.int8Vector(byteArray.asObject());
            case ShortArray shortArray -> Values.int16Vector(shortArray.asObject());
            case IntArray intArray -> Values.int32Vector(intArray.asObject());
            case LongArray longArray -> Values.int64Vector(longArray.asObject());
            case FloatArray floatArray -> Values.float32Vector(floatArray.asObject());
            case DoubleArray doubleArray -> Values.float64Vector(doubleArray.asObject());
            default -> throw invalidVectorType(arrayValue);
        };
    }

    static VectorValue int8Vector(SequenceValue vectorSequence) {
        if (vectorSequence instanceof ByteArray bytes) {
            return Values.int8Vector(bytes.asObjectCopy());
        } else {
            int index = 0;
            int length = vectorSequence.intSize();
            byte[] values = new byte[length];
            for (AnyValue value : vectorSequence) {
                if (value instanceof NumberValue number) {
                    values[index++] = (byte) number.longValue();
                } else {
                    throw invalidVectorType(value);
                }
            }
            return Values.int8Vector(values);
        }
    }

    static VectorValue int16Vector(SequenceValue vectorSequence) {
        if (vectorSequence instanceof ShortArray shorts) {
            return Values.int16Vector(shorts.asObjectCopy());
        } else {
            int index = 0;
            int length = vectorSequence.intSize();
            short[] values = new short[length];
            for (AnyValue value : vectorSequence) {
                if (value instanceof NumberValue number) {
                    values[index++] = (short) number.longValue();
                } else {
                    throw invalidVectorType(value);
                }
            }
            return Values.int16Vector(values);
        }
    }

    static VectorValue int32Vector(SequenceValue vectorSequence) {
        if (vectorSequence instanceof IntArray ints) {
            return Values.int32Vector(ints.asObjectCopy());
        } else {
            int index = 0;
            int length = vectorSequence.intSize();
            int[] values = new int[length];
            for (AnyValue value : vectorSequence) {
                if (value instanceof NumberValue number) {
                    values[index++] = (int) number.longValue();
                } else {
                    throw invalidVectorType(value);
                }
            }
            return Values.int32Vector(values);
        }
    }

    static VectorValue int64Vector(SequenceValue vectorSequence) {
        if (vectorSequence instanceof LongArray longs) {
            return Values.int64Vector(longs.asObjectCopy());
        } else {
            int index = 0;
            int length = vectorSequence.intSize();
            long[] values = new long[length];
            for (AnyValue value : vectorSequence) {
                if (value instanceof NumberValue number) {
                    values[index++] = number.longValue();
                } else {
                    throw invalidVectorType(value);
                }
            }
            return Values.int64Vector(values);
        }
    }

    static VectorValue float32Vector(SequenceValue vectorSequence) {
        if (vectorSequence instanceof FloatArray floats) {
            return Values.float32Vector(floats.asObjectCopy());
        } else {
            int index = 0;
            int length = vectorSequence.intSize();
            float[] values = new float[length];
            for (AnyValue value : vectorSequence) {
                if (value instanceof NumberValue number) {
                    values[index++] = assertNoOverflow((float) number.doubleValue());
                } else {
                    throw invalidVectorType(value);
                }
            }
            return Values.float32Vector(values);
        }
    }

    static VectorValue float64Vector(SequenceValue vectorSequence) {
        if (vectorSequence instanceof DoubleArray doubles) {
            return Values.float64Vector(doubles.asObjectCopy());
        } else {
            int index = 0;
            int length = vectorSequence.intSize();
            double[] values = new double[length];
            for (AnyValue value : vectorSequence) {
                if (value instanceof NumberValue number) {
                    values[index++] = assertNoOverflow(number.doubleValue());
                } else {
                    throw invalidVectorType(value);
                }
            }
            return Values.float64Vector(values);
        }
    }

    static float assertNoOverflow(float value) {
        if (!Float.isFinite(value)) {
            throw org.neo4j.exceptions.ArithmeticException.floatOverflow(
                    Float.toString(value), "Coercing to a 32 bit Float");
        }
        return value;
    }

    static double assertNoOverflow(double value) {
        if (!Double.isFinite(value)) {
            throw org.neo4j.exceptions.ArithmeticException.floatOverflow(
                    Double.toString(value), "Coercing to a 64 bit Float");
        }
        return value;
    }

    static Value assertDimension(VectorValue vectorValue, AnyValue dimension) {
        long dimensionValue = getDimension(dimension);
        if (vectorValue.dimensions() != dimensionValue) {
            throw new CypherTypeException(format(
                    "Expected a vector of dimension %d, but got: %d;", dimensionValue, vectorValue.dimensions()));
        }
        return vectorValue;
    }

    static long getDimension(AnyValue dimension) {
        if (!(dimension instanceof IntegralValue dimensionValue)) {
            throw CypherTypeException.functionArgumentWrongType(
                    format("Invalid input for function 'vector()': Expected an integer but got %s", dimension),
                    "vector",
                    dimension.toString(),
                    List.of("INTEGER"),
                    dimension.getTypeName());
        }

        if (dimensionValue.longValue() < 0 || dimensionValue.longValue() > 4096) {
            throw InvalidArgumentException.argumentOutOfRange(
                    "vector", "dimension", 0, 4096, dimensionValue.longValue());
        }

        return dimensionValue.longValue();
    }

    static CypherTypeException invalidVectorType(AnyValue value) {
        return CypherTypeException.functionArgumentWrongType(
                "Invalid input for function 'vector()': Expected a NUMBER, got: " + value,
                "vector",
                value.prettyPrint(),
                List.of("INTEGER", "FLOAT"),
                CypherTypeValueMapper.valueType(value));
    }

    static CypherTypeException invalidVector(AnyValue vector) {
        return CypherTypeException.functionArgumentWrongType(
                format("Invalid input for function 'vector()': Expected a string or list but got %s", vector),
                "vector",
                vector.toString(),
                List.of("STRING", "LIST<INTEGER | FLOAT>"),
                vector.getTypeName());
    }
}

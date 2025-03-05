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

import static org.neo4j.values.storable.Values.NO_VALUE;
import static org.neo4j.values.storable.Values.doubleValue;
import static org.neo4j.values.storable.Values.longValue;
import static org.neo4j.values.storable.Values.numberValue;

import java.util.List;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.expressions.Expression;
import org.neo4j.cypher.internal.expressions.ListLiteral;
import org.neo4j.cypher.internal.expressions.Literal;
import org.neo4j.cypher.internal.expressions.NumberLiteral;
import org.neo4j.cypher.internal.parser.AstParserFactory;
import org.neo4j.cypher.internal.parser.AstParserFactory$;
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory;
import org.neo4j.exceptions.CypherTypeException;
import org.neo4j.exceptions.SyntaxException;
import org.neo4j.kernel.impl.util.ValueUtils;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.ArrayValue;
import org.neo4j.values.storable.ByteArray;
import org.neo4j.values.storable.DoubleArray;
import org.neo4j.values.storable.FloatArray;
import org.neo4j.values.storable.IntArray;
import org.neo4j.values.storable.LongArray;
import org.neo4j.values.storable.ShortArray;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.ListValueBuilder;
import scala.Option;
import scala.collection.immutable.Seq;

/**
 * Utility class for parsing strings from the cypher runtime.
 * <p>
 * Internally uses the cypher parser so that we can make sure we are consistent with the language itself
 */
@SuppressWarnings("deprecation")
abstract class CypherRuntimeParser {
    private CypherRuntimeParser() {
        throw new UnsupportedOperationException();
    }

    // NOTE: we are using the Cypher25 parser only, so far we only parse very simple things like numbers and lists
    //      of numbers so the version shouldn't matter. If we ever do more advanced stuff that will be depending on the
    //      version we will probably need to make this a dynamic class and provide the version as a dependency.
    private static final AstParserFactory parserFactory = AstParserFactory$.MODULE$.apply(CypherVersion.Cypher25);

    static Value parseVector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        var builder = ListValueBuilder.newListBuilder(length);
        var iterator = expressions.iterator();
        while (iterator.hasNext()) {
            builder.add(numberValue(asNumber(iterator.next())));
        }

        var list = builder.build();
        ArrayValue array = list.toStorableArray();
        return switch (array) {
            case ByteArray byteArray -> Values.int8Vector(byteArray.asObject());
            case ShortArray shortArray -> Values.int16Vector(shortArray.asObject());
            case IntArray intArray -> Values.int32Vector(intArray.asObject());
            case LongArray longArray -> Values.int64Vector(longArray.asObject());
            case FloatArray floatArray -> Values.float32Vector(floatArray.asObject());
            case DoubleArray doubleArray -> Values.float64Vector(doubleArray.asObject());
            default -> throw invalidType(array);
        };
    }

    static Value parseInt8Vector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        byte[] bytes = new byte[length];
        var iterator = expressions.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            bytes[i++] = asByte(iterator.next());
        }
        return Values.int8Vector(bytes);
    }

    static Value parseInt16Vector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        short[] shorts = new short[length];
        var iterator = expressions.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            shorts[i++] = asShort(iterator.next());
        }
        return Values.int16Vector(shorts);
    }

    static Value parseInt32Vector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        int[] ints = new int[length];
        var iterator = expressions.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ints[i++] = asInt(iterator.next());
        }
        return Values.int32Vector(ints);
    }

    static Value parseInt64Vector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        long[] longs = new long[length];
        var iterator = expressions.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            longs[i++] = asLong(iterator.next());
        }
        return Values.int64Vector(longs);
    }

    static Value parseFloat32Vector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        float[] floats = new float[length];
        var iterator = expressions.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            floats[i++] = safeAsFloat(iterator.next());
        }
        return Values.float32Vector(floats);
    }

    static Value parseFloat64Vector(String expression) {
        var expressions = asList(expression);
        int length = expressions.size();
        double[] doubles = new double[length];
        var iterator = expressions.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            doubles[i++] = safeAsDouble(iterator.next());
        }
        return Values.float64Vector(doubles);
    }

    public static Value parseAsDoubleOrElseNoValue(String expression) {
        try {
            var res = parse(expression);
            if (res instanceof NumberLiteral number) {
                return doubleValue(number.value().doubleValue());
            } else {
                return NO_VALUE;
            }
        } catch (NumberFormatException | SyntaxException ignore) {
            // NOTE: This is here because of backwards compatability so that we support some
            //      extra formats not supported by Cypher itself, i.e. 0000046
            return parseAsDoubleWithJava(expression);
        }
    }

    public static Value parseAsLongOrElseNoValue(String expression) {
        try {
            var res = parse(expression);
            if (res instanceof NumberLiteral number) {
                return longValue(number.value().longValue());
            } else {
                return NO_VALUE;
            }
        } catch (NumberFormatException | SyntaxException ignore) {
            // NOTE: This is here because of backwards compatability so that we support some
            //      extra formats not supported by Cypher itself, i.e. 0000046
            return parseAsLongWithJava(expression);
        }
    }

    private static Value parseAsLongWithJava(String expression) {
        return longValue(Long.parseLong(expression));
    }

    private static Value parseAsDoubleWithJava(String expression) {
        try {
            return doubleValue(Double.parseDouble(expression));
        } catch (NumberFormatException e) {
            return NO_VALUE;
        }
    }

    private static Seq<Expression> asList(String stringList) {
        var expression = parse(stringList);
        if (expression instanceof ListLiteral listLiteral) {
            return listLiteral.expressions();
        } else if (expression instanceof Literal literal) {
            throw invalidType(ValueUtils.of(literal.value()));
        } else {
            throw invalidType(expression);
        }
    }

    private static float safeAsFloat(Expression expression) {
        var result = asNumber(expression).floatValue();
        if (!Float.isFinite(result)) {
            throw org.neo4j.exceptions.ArithmeticException.floatOverflow(
                    Float.toString(result), "Parse as 32 bit Float");
        }
        return result;
    }

    private static double safeAsDouble(Expression expression) {
        var result = asNumber(expression).doubleValue();
        if (!Double.isFinite(result)) {
            throw org.neo4j.exceptions.ArithmeticException.floatOverflow(
                    Double.toString(result), "Parse as 64 bit Float");
        }
        return result;
    }

    private static byte asByte(Expression expression) {
        return asNumber(expression).byteValue();
    }

    private static short asShort(Expression expression) {
        return asNumber(expression).shortValue();
    }

    private static int asInt(Expression expression) {
        return asNumber(expression).intValue();
    }

    private static long asLong(Expression expression) {
        return asNumber(expression).longValue();
    }

    private static Number asNumber(Expression expression) {
        if (expression instanceof NumberLiteral numberLiteral) {
            return numberLiteral.value();
        } else if (expression instanceof Literal literal) {
            throw invalidType(ValueUtils.of(literal.value()));
        } else {
            throw invalidType(expression);
        }
    }

    private static Expression parse(String expression) {
        return parserFactory
                .apply(expression, new Neo4jCypherExceptionFactory(expression, Option.empty()), Option.empty())
                .expression();
    }

    private static CypherTypeException invalidType(AnyValue value) {
        return CypherTypeException.functionArgumentWrongType(
                "Invalid input for function 'VECTOR()': Expected a NUMBER, got: " + value,
                "VECTOR",
                value.prettyPrint(),
                List.of("INTEGER", "FLOAT"),
                CypherTypeValueMapper.valueType(value));
    }

    private static CypherTypeException invalidType(Expression badInput) {
        return CypherTypeException.functionArgumentWrongType(
                "Invalid input for function 'VECTOR': Expected a NUMBER, got: " + badInput.asCanonicalStringVal(),
                "VECTOR",
                badInput.asCanonicalStringVal(),
                List.of("INTEGER", "FLOAT"),
                "ANY");
    }
}

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
package org.neo4j.values.utils;

import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueRepresentation;
import org.neo4j.values.storable.VectorValue;

/// This class duplicates much of `CypherTypeValueMapper` in `org.neo4j.values.utils`
/// because we don't have access to that class (and the Scala it uses) at this level.
public class ValueTypeNames {
    public static String nameOfType(Value value) {
        return ofRepresentation(value.valueRepresentation(), value);
    }

    public static String ofRepresentation(ValueRepresentation valueRepresentation, Value value) {
        return switch (valueRepresentation) {
            case UNKNOWN -> "UNKNOWN";
            case ANYTHING -> "ANY";
            case GEOMETRY_ARRAY -> listOf(ValueRepresentation.GEOMETRY);
            case ZONED_DATE_TIME_ARRAY -> listOf(ValueRepresentation.ZONED_DATE_TIME);
            case LOCAL_DATE_TIME_ARRAY -> listOf(ValueRepresentation.LOCAL_DATE_TIME);
            case DATE_ARRAY -> listOf(ValueRepresentation.DATE);
            case ZONED_TIME_ARRAY -> listOf(ValueRepresentation.ZONED_TIME);
            case LOCAL_TIME_ARRAY -> listOf(ValueRepresentation.LOCAL_TIME);
            case DURATION_ARRAY -> listOf(ValueRepresentation.DURATION);
            case TEXT_ARRAY -> listOf(ValueRepresentation.UTF16_TEXT);
            case BOOLEAN_ARRAY -> listOf(ValueRepresentation.BOOLEAN);
            case INT64_ARRAY -> listOf(ValueRepresentation.INT64);
            case INT32_ARRAY -> listOf(ValueRepresentation.INT32);
            case INT16_ARRAY -> listOf(ValueRepresentation.INT16);
            case INT8_ARRAY -> listOf(ValueRepresentation.INT8);
            case FLOAT64_ARRAY -> listOf(ValueRepresentation.FLOAT64);
            case FLOAT32_ARRAY -> listOf(ValueRepresentation.FLOAT32);
            case GEOMETRY -> "POINT";
            case ZONED_DATE_TIME -> "ZONED DATETIME";
            case LOCAL_DATE_TIME -> "LOCAL DATETIME";
            case DATE -> "DATE";
            case ZONED_TIME -> "ZONED TIME";
            case LOCAL_TIME -> "LOCAL TIME";
            case DURATION -> "DURATION";
            case UTF16_TEXT -> "STRING";
            case UTF8_TEXT -> "STRING";
            case BOOLEAN -> "BOOLEAN";
            case INT64 -> "INTEGER";
            case INT32 -> "INTEGER";
            case INT16 -> "INTEGER";
            case INT8 -> "INTEGER";
            case FLOAT64 -> "FLOAT";
            case FLOAT32 -> "FLOAT";
            case INT8_VECTOR -> vectorOf("INTEGER8", value);
            case INT16_VECTOR -> vectorOf("INTEGER16", value);
            case INT32_VECTOR -> vectorOf("INTEGER32", value);
            case INT64_VECTOR -> vectorOf("INTEGER", value);
            case FLOAT32_VECTOR -> vectorOf("FLOAT32", value);
            case FLOAT64_VECTOR -> vectorOf("FLOAT", value);
            case NO_VALUE -> "NULL";
        };
    }

    private static String listOf(ValueRepresentation memberRepresentation) {
        return String.format("LIST<%s>", ofRepresentation(memberRepresentation, null));
    }

    private static String vectorOf(String memberTypeName, Value value) {
        String dimensions = "";
        if (value instanceof VectorValue vector) {
            dimensions = String.valueOf(vector.dimensions());
        }
        return String.format("VECTOR<%s>(%s)", memberTypeName, dimensions);
    }
}

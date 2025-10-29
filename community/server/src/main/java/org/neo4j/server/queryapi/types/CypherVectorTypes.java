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
package org.neo4j.server.queryapi.types;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.neo4j.driver.internal.InternalFloat32Vector;
import org.neo4j.driver.internal.InternalFloat64Vector;
import org.neo4j.driver.internal.InternalInt16Vector;
import org.neo4j.driver.internal.InternalInt32Vector;
import org.neo4j.driver.internal.InternalInt64Vector;
import org.neo4j.driver.internal.InternalInt8Vector;
import org.neo4j.driver.internal.value.VectorValue;

/**
 * "Official" Cypher Vector types
 */
public enum CypherVectorTypes {
    INT8(CypherVectorTypes::readInt8),
    INT16(CypherVectorTypes::readInt16),
    INT32(CypherVectorTypes::readInt32),
    INT64(CypherVectorTypes::readInt64),
    FLOAT32(CypherVectorTypes::readFloat32),
    FLOAT64(CypherVectorTypes::readFloat64);

    private final Function<String[], VectorValue> reader;

    CypherVectorTypes(Function<String[], VectorValue> reader) {
        this.reader = reader;
    }

    public static Optional<CypherVectorTypes> safeValueOf(String value) {
        try {
            return Optional.of(CypherVectorTypes.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static List<String> getTypeNames() {
        return Arrays.stream(CypherVectorTypes.values())
                .map(CypherVectorTypes::name)
                .toList();
    }

    public VectorValue read(String[] coordinates) {
        return reader.apply(coordinates);
    }

    private static VectorValue readInt8(String[] coordinates) {
        var bytes = new byte[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            bytes[i] = Byte.parseByte(coordinates[i]);
        }
        return new VectorValue(new InternalInt8Vector(bytes));
    }

    private static VectorValue readInt16(String[] coordinates) {
        var int16s = new short[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            int16s[i] = Short.parseShort(coordinates[i]);
        }
        return new VectorValue(new InternalInt16Vector(int16s));
    }

    private static VectorValue readInt32(String[] coordinates) {
        var int32s = new int[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            int32s[i] = Integer.parseInt(coordinates[i]);
        }
        return new VectorValue(new InternalInt32Vector(int32s));
    }

    private static VectorValue readInt64(String[] coordinates) {
        var int64s = new long[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            int64s[i] = Long.parseLong(coordinates[i]);
        }
        return new VectorValue(new InternalInt64Vector(int64s));
    }

    private static VectorValue readFloat32(String[] coordinates) {
        var float32s = new float[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            float32s[i] = Float.parseFloat(coordinates[i]);
        }
        return new VectorValue(new InternalFloat32Vector(float32s));
    }

    private static VectorValue readFloat64(String[] coordinates) {
        var float64s = new double[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            float64s[i] = Double.parseDouble(coordinates[i]);
        }
        return new VectorValue(new InternalFloat64Vector(float64s));
    }
}

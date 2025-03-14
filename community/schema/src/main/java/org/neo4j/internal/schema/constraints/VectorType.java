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
package org.neo4j.internal.schema.constraints;

import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.neo4j.graphdb.schema.PropertyType;
import org.neo4j.values.storable.VectorValue;

public final class VectorType implements ConstrainableType {
    private static final String CYPHER_USER_DESCRIPTION = "VECTOR<%s>(%d)";

    private final Coordinate coordinate;
    private final int dimensions;

    private VectorType(Coordinate coordinate, int dimensions) {
        if (dimensions < VectorValue.MIN_VECTOR_DIMENSIONS || dimensions > VectorValue.MAX_VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException("Required %d <= %d <= %d"
                    .formatted(VectorValue.MIN_VECTOR_DIMENSIONS, dimensions, VectorValue.MAX_VECTOR_DIMENSIONS));
        }
        this.coordinate = coordinate;
        this.dimensions = dimensions;
    }

    public int dimensions() {
        return dimensions;
    }

    public Coordinate coordinate() {
        return coordinate;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[inner=%s, dimensions=%d]".formatted(coordinate, dimensions);
    }

    @Override
    public boolean equals(Object other) {
        // Since we intern values, we only consider identity when testing for equality.
        return super.equals(other);
    }

    @Override
    public Ordering order() {
        return coordinate.order();
    }

    @Override
    public String userDescription() {
        return CYPHER_USER_DESCRIPTION.formatted(coordinate.userDescription(), dimensions);
    }

    public static VectorType int8Vector(int dimension) {
        return getInterned(Coordinate.INTEGER8, dimension);
    }

    public static VectorType int16Vector(int dimension) {
        return getInterned(Coordinate.INTEGER16, dimension);
    }

    public static VectorType int32Vector(int dimension) {
        return getInterned(Coordinate.INTEGER32, dimension);
    }

    public static VectorType int64Vector(int dimension) {
        return getInterned(Coordinate.INTEGER64, dimension);
    }

    public static VectorType float32Vector(int dimension) {
        return getInterned(Coordinate.FLOAT32, dimension);
    }

    public static VectorType float64Vector(int dimension) {
        return getInterned(Coordinate.FLOAT64, dimension);
    }

    @Override
    public PropertyType toPublicApi() {
        throw new NotImplementedException("Vectors are not yet represented in Public API");
    }

    public enum Coordinate {
        INTEGER8("INTEGER8", Ordering.VECTOR_INT8_ORDER),
        INTEGER16("INTEGER16", Ordering.VECTOR_INT16_ORDER),
        INTEGER32("INTEGER32", Ordering.VECTOR_INT32_ORDER),
        INTEGER64("INTEGER64", Ordering.VECTOR_INT64_ORDER),
        FLOAT32("FLOAT32", Ordering.VECTOR_FLOAT32_ORDER),
        FLOAT64("FLOAT64", Ordering.VECTOR_FLOAT64_ORDER);

        private final Ordering order;
        private final String userDescription;

        Coordinate(String userDescription, Ordering order) {
            this.order = order;
            this.userDescription = userDescription;
        }

        public String userDescription() {
            return userDescription;
        }

        public Ordering order() {
            return order;
        }
    }

    private static final Map<Coordinate, ConcurrentHashMap<Integer, VectorType>> INTERNED =
            Lists.fixedSize.with(Coordinate.values()).toMap(c -> c, c -> new ConcurrentHashMap<>());

    private static VectorType getInterned(Coordinate inner, int dimension) {
        /* To avoid that each Value has a distinct VectorType we intern the created values. */
        var map = INTERNED.get(inner);
        assert map != null; // Created statically from all InnerTypes
        return map.computeIfAbsent(dimension, (ignored) -> new VectorType(inner, dimension));
    }

    private static final String SERIALIZE_PATTERN = "VECTOR[coordinate=%s, dimensions=%d]";
    private static final Pattern DESERIALIZE_PATTERN =
            Pattern.compile("VECTOR\\[coordinate=(\\w+), dimensions=(\\d+)\\]");

    @Override
    public String serialize() {
        return SERIALIZE_PATTERN.formatted(coordinate, dimensions);
    }

    public static VectorType deserialize(String str) throws IllegalArgumentException {
        var matcher = DESERIALIZE_PATTERN.matcher(str);
        if (matcher.matches()) {
            return getInterned(Coordinate.valueOf(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }
        throw new IllegalArgumentException(str);
    }
}

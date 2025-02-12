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

import static java.lang.String.format;

import java.util.Arrays;
import org.neo4j.hashing.HashFunction;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.values.ValueMapper;

public final class Int16Vector extends IntegralVector {
    private static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(Int16Vector.class);

    private final short[] coordinates;

    Int16Vector(short... coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public int dimensions() {
        return coordinates.length;
    }

    @Override
    public String getTypeName() {
        return "Int16Vector";
    }

    @Override
    public ValueRepresentation valueRepresentation() {
        return ValueRepresentation.INT16_VECTOR;
    }

    @Override
    public boolean equals(Value other) {
        if (other instanceof Int16Vector v) {
            return Arrays.equals(this.coordinates, v.coordinates);
        }
        return false;
    }

    @Override
    protected int unsafeCompareTo(Value other) {
        Int16Vector v = (Int16Vector) other;
        if (this.coordinates.length > v.coordinates.length) {
            return 1;
        } else if (this.coordinates.length < v.coordinates.length) {
            return -1;
        }

        var x = 0;
        for (int i = 0; i < this.coordinates.length; i++) {
            x = Short.compare(this.coordinates[i], v.coordinates[i]);
            if (x != 0) {
                return x;
            }
        }

        return 0;
    }

    @Override
    public <E extends Exception> void writeTo(ValueWriter<E> writer) throws E {}

    @Override
    public <T> T map(ValueMapper<T> mapper) {
        return null;
    }

    @Override
    public long updateHash(HashFunction hashFunction, long hash) {
        int len = dimensions();
        hash = hashFunction.update(hash, len);
        for (int i = 0; i < len; i++) {
            hash = hashFunction.update(hash, coordinates[i]);
        }
        return hash;
    }

    @Override
    protected int computeHashToMemoize() {
        return NumberValues.hash(coordinates);
    }

    @Override
    public long estimatedHeapUsage() {
        return SHALLOW_SIZE + HeapEstimator.sizeOf(coordinates);
    }

    @Override
    public String toString() {
        return format("%s%s", getTypeName(), Arrays.toString(coordinates));
    }
}

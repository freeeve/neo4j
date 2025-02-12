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

import static org.neo4j.values.storable.Values.NO_VALUE;

import java.util.Objects;
import org.neo4j.values.storable.NumberValue;

public interface VectorCandidate {
    float floatValue(int index);

    double doubleValue(int index);

    int dimensions();

    static VectorCandidate maybeFrom(AnyValue candidate) {
        if (candidate == null || candidate == NO_VALUE) {
            return null;
        }

        return switch (candidate) {
            case final VectorCandidate vectorCandidate -> vectorCandidate;
            case final SequenceValue sequenceValue -> new SequenceValueVectorCandidate(sequenceValue);
            default -> null;
        };
    }

    static VectorCandidate from(AnyValue candidate) {
        final var vectorCandidate = maybeFrom(candidate);
        if (vectorCandidate == null) {
            Objects.requireNonNull(candidate, "Value cannot be null");
            throw new IllegalArgumentException("Value is not a valid vector candidate. Provided: " + candidate);
        }
        return vectorCandidate;
    }

    record SequenceValueVectorCandidate(SequenceValue sequence) implements VectorCandidate {

        @Override
        public float floatValue(int index) {
            return sequence.value(index) instanceof final NumberValue number ? number.floatValue() : Float.NaN;
        }

        @Override
        public double doubleValue(int index) {
            return sequence.value(index) instanceof final NumberValue number ? number.doubleValue() : Double.NaN;
        }

        @Override
        public int dimensions() {
            return sequence.intSize();
        }
    }
}

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

package org.neo4j.kernel.api.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.kernel.api.vector.VectorUtilSupportTest.origin;
import static org.neo4j.kernel.api.vector.VectorUtilSupportTest.vector;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.values.VectorCandidate;

/**
 * Functions which are simply wrappers around {@link VectorUtil} method are tested in
 * {@link VectorUtilTest} rather than here.
 */
public class GQLVectorNormTest {

    @ParameterizedTest
    @MethodSource
    void nonFiniteNormInvalid(VectorNorm normalization, VectorCandidate vector, boolean expectedValidity) {
        boolean valid;
        try {
            valid = normalization.valid(vector);
        } catch (AssertionError e) {
            // asserts are usually disabled in production, though not in testing;
            // if the assert triggers it would be invalid
            valid = false;
        }
        assertThat(valid).isEqualTo(expectedValidity);
    }

    static Stream<Arguments> nonFiniteNormInvalid() {
        final float manhattanMax = Float.MAX_VALUE / 4;
        final float overManhattanMax = Math.nextUp(manhattanMax);
        final float underManhattanMax = Math.nextDown(manhattanMax);
        final VectorCandidate manhattanMaxVector = vector(manhattanMax, manhattanMax, manhattanMax, manhattanMax);
        final VectorCandidate overManhattanMaxVector =
                vector(overManhattanMax, overManhattanMax, overManhattanMax, overManhattanMax);
        final VectorCandidate underManhattanMaxVector =
                vector(underManhattanMax, underManhattanMax, underManhattanMax, underManhattanMax);

        final float euclideanMax = (float) Math.sqrt(manhattanMax);
        final float overEuclideanMax = Math.nextUp(euclideanMax);
        final float underEuclideanMax = Math.nextDown(euclideanMax);
        final VectorCandidate euclideanMaxVector = vector(euclideanMax, euclideanMax, euclideanMax, euclideanMax);
        final VectorCandidate overEuclideanMaxVector =
                vector(overEuclideanMax, overEuclideanMax, overEuclideanMax, overEuclideanMax);
        final VectorCandidate underEuclideanMaxVector =
                vector(underEuclideanMax, underEuclideanMax, underEuclideanMax, underEuclideanMax);

        return Stream.of(
                Arguments.of(GQLVectorNorm.EUCLIDEAN, underManhattanMaxVector, false),
                Arguments.of(GQLVectorNorm.EUCLIDEAN, manhattanMaxVector, false),
                Arguments.of(GQLVectorNorm.EUCLIDEAN, overManhattanMaxVector, false),
                Arguments.of(GQLVectorNorm.EUCLIDEAN, underEuclideanMaxVector, true),
                Arguments.of(GQLVectorNorm.EUCLIDEAN, euclideanMaxVector, true),
                Arguments.of(GQLVectorNorm.EUCLIDEAN, overEuclideanMaxVector, false),
                Arguments.of(GQLVectorNorm.MANHATTAN, underManhattanMaxVector, true),
                Arguments.of(GQLVectorNorm.MANHATTAN, manhattanMaxVector, true),
                Arguments.of(GQLVectorNorm.MANHATTAN, overManhattanMaxVector, false),
                Arguments.of(GQLVectorNorm.MANHATTAN, underEuclideanMaxVector, true),
                Arguments.of(GQLVectorNorm.MANHATTAN, euclideanMaxVector, true),
                Arguments.of(GQLVectorNorm.MANHATTAN, overEuclideanMaxVector, true));
    }

    @ParameterizedTest
    @EnumSource
    void originInvalid(GQLVectorNorm normalization) {
        assertThat(normalization.valid(origin(42))).isFalse();
    }
}

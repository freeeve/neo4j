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

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.values.AnyValue;
import org.neo4j.values.VectorCandidate;
import org.neo4j.values.storable.Values;
import org.neo4j.values.storable.VectorValue;
import org.neo4j.values.virtual.VirtualValues;

class VectorUtilSupportTest {

    @Nested
    class DefaultVectorUtilSupportTest extends TestBase {
        DefaultVectorUtilSupportTest() {
            super(new DefaultVectorUtilSupport());
        }
    }

    @ExtendWith(RandomExtension.class)
    abstract static class TestBase {
        static final Percentage CLOSENESS = Percentage.withPercentage(0.1);

        protected final VectorUtilSupport impl;

        @Inject
        private RandomSupport random;

        TestBase(VectorUtilSupport impl) {
            this.impl = impl;
        }

        @RepeatedTest(10)
        void scale() {
            final VectorCandidate vector = randomVector(random.random());
            final float scale = randomFloatFor(random.random(), vector.dimensions());
            final VectorCandidate scaled = impl.scale(vector, scale);
            assertThat(scaled.dimensions()).isEqualTo(vector.dimensions());
            assertThat(scaled).as("vector scaled by " + scale).satisfies(s -> {
                for (int i = 0; i < s.dimensions(); i++) {
                    assertThat(s.floatValue(i)).isCloseTo(scale * vector.floatValue(i), CLOSENESS);
                }
            });
        }

        @ParameterizedTest
        @MethodSource
        void dotProduct(VectorCandidate vector1, VectorCandidate vector2, float expected) {
            final float dot = impl.dotProduct(vector1, vector2);
            assertThat(dot).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> dotProduct() {
            return Stream.of(
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), origin(4), 0.f),
                    Arguments.of(basisUnitVector(42, 2), basisUnitVector(42, 23), 0.f),
                    Arguments.of(basisUnitVector(42, 37), basisUnitVector(42, 4), 0.f),
                    Arguments.of(basisUnitVector(42, 2), basisUnitVector(42, 2), 1.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), basisUnitVector(4, 0), 1.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), basisUnitVector(4, 1), 2.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), basisUnitVector(4, 2), 3.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), basisUnitVector(4, 3), 4.f),
                    Arguments.of(vector(-12.f, 16), vector(12.f, 9.f), 0.f),
                    Arguments.of(vector(-6.f, 8.f), vector(5.f, 12.f), 66.f),
                    Arguments.of(vector(9.f, 2.f, 7.f), vector(4.f, 8.f, 10.f), 122.f));
        }

        @ParameterizedTest
        @MethodSource
        void cosine(VectorCandidate vector1, VectorCandidate vector2, float expected) {
            final float cosine = impl.cosine(vector1, vector2);
            assertThat(cosine).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> cosine() {
            return Stream.of(
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), origin(4), Float.NaN),
                    Arguments.of(basisUnitVector(42, 2), basisUnitVector(42, 23), 0.f),
                    Arguments.of(basisUnitVector(42, 37), basisUnitVector(42, 4), 0.f),
                    Arguments.of(basisUnitVector(42, 2), basisUnitVector(42, 2), 1.f),
                    Arguments.of(vector(1.f, 0.f), vector(1.f, (float) Math.sqrt(3.f)), 0.5f),
                    Arguments.of(vector(1.f, 0.f), vector(1.f, 1.f), (float) (Math.sqrt(2.f) / 2.f)),
                    Arguments.of(vector(-6.f, 8.f), vector(5.f, 12.f), (float) Math.cos(Math.toRadians(59.5))));
        }

        @ParameterizedTest
        @MethodSource
        void l1Distance(VectorCandidate vector1, VectorCandidate vector2, float expected) {
            final float distance = impl.l1Distance(vector1, vector2);
            assertThat(distance).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> l1Distance() {
            return Stream.of(
                    Arguments.of(origin(42), origin(42), 0.f),
                    Arguments.of(basisUnitVector(42, 23), basisUnitVector(42, 23), 0.f),
                    Arguments.of(basisUnitVector(42, 0), origin(42), 1.f),
                    Arguments.of(vector(1.f, 0.f, 0.f, 0.f), origin(4), 1.f),
                    Arguments.of(vector(1.f, 1.f, 0.f, 0.f), origin(4), 2.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 0.f), origin(4), 3.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 1.f), origin(4), 4.f),
                    Arguments.of(vector(4.f, 0.f, 0.f, 0.f), origin(4), 4.f),
                    Arguments.of(vector(4.f, 4.f, 0.f, 0.f), origin(4), 8.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 0.f), origin(4), 12.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 4.f), origin(4), 16.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), origin(4), 10.f),
                    Arguments.of(vector(-1.f, 2.f, -3.f, 4.f), origin(4), 10.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), vector(-1.f, 2.f, -3.f, 4.f), 8.f),
                    Arguments.of(vector(-0.5f, 1.5f, -4.f, -2.f), vector(1.f, 2.25f, -0.75f, 1.125f), 8.625f));
        }

        @ParameterizedTest
        @MethodSource
        void l1Norm(VectorCandidate vector, float expected) {
            final float normalization = impl.l1Norm(vector);
            assertThat(normalization).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> l1Norm() {
            return Stream.of(
                    Arguments.of(origin(42), 0.f),
                    Arguments.of(basisUnitVector(42, 23), 1.f),
                    Arguments.of(vector(1.f, 0.f, 0.f, 0.f), 1.f),
                    Arguments.of(vector(1.f, 1.f, 0.f, 0.f), 2.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 0.f), 3.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 1.f), 4.f),
                    Arguments.of(vector(4.f, 0.f, 0.f, 0.f), 4.f),
                    Arguments.of(vector(4.f, 4.f, 0.f, 0.f), 8.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 0.f), 12.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 4.f), 16.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), 10.f),
                    Arguments.of(vector(-1.f, 2.f, -3.f, 4.f), 10.f),
                    Arguments.of(vector(1.f, 2.25f, -0.75f, 1.125f), 5.125f));
        }

        @ParameterizedTest
        @MethodSource
        void squareL2Distance(VectorCandidate vector1, VectorCandidate vector2, float expected) {
            final float squareDistance = impl.squareL2Distance(vector1, vector2);
            assertThat(squareDistance).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> squareL2Distance() {
            return Stream.of(
                    Arguments.of(origin(42), origin(42), 0.f),
                    Arguments.of(basisUnitVector(42, 23), basisUnitVector(42, 23), 0.f),
                    Arguments.of(basisUnitVector(42, 0), origin(42), 1.f),
                    Arguments.of(vector(1.f, 0.f, 0.f, 0.f), origin(4), 1.f),
                    Arguments.of(vector(1.f, 1.f, 0.f, 0.f), origin(4), 2.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 0.f), origin(4), 3.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 1.f), origin(4), 4.f),
                    Arguments.of(vector(4.f, 0.f, 0.f, 0.f), origin(4), 16.f),
                    Arguments.of(vector(4.f, 4.f, 0.f, 0.f), origin(4), 32.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 0.f), origin(4), 48.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 4.f), origin(4), 64.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), origin(4), 30.f),
                    Arguments.of(vector(-1.f, 2.f, -3.f, 4.f), origin(4), 30.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), vector(-1.f, 2.f, -3.f, 4.f), 40.f),
                    Arguments.of(vector(-0.5f, 1.5f, -4.f, -2.f), vector(1.f, 2.25f, -0.75f, 1.125f), 23.140625f),
                    Arguments.of(vector(16.f, 23.f, 42.f, 55.55f), vector(13.f, 23.f, 46.f, 55.55f), 25.f));
        }

        @ParameterizedTest
        @MethodSource
        void squareL2Norm(VectorCandidate vector, float expected) {
            final float squareDistance = impl.squareL2Norm(vector);
            assertThat(squareDistance).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> squareL2Norm() {
            return Stream.of(
                    Arguments.of(origin(42), 0.f),
                    Arguments.of(basisUnitVector(42, 23), 1.f),
                    Arguments.of(vector(1.f, 0.f, 0.f, 0.f), 1.f),
                    Arguments.of(vector(1.f, 1.f, 0.f, 0.f), 2.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 0.f), 3.f),
                    Arguments.of(vector(1.f, 1.f, 1.f, 1.f), 4.f),
                    Arguments.of(vector(4.f, 0.f, 0.f, 0.f), 16.f),
                    Arguments.of(vector(4.f, 4.f, 0.f, 0.f), 32.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 0.f), 48.f),
                    Arguments.of(vector(4.f, 4.f, 4.f, 4.f), 64.f),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), 30.f),
                    Arguments.of(vector(-1.f, 2.f, -3.f, 4.f), 30.f),
                    Arguments.of(vector(1.f, 2.25f, -0.75f, 1.125f), 7.890625f));
        }

        @ParameterizedTest
        @MethodSource
        void hammingDistance(VectorCandidate vector1, VectorCandidate vector2, int expected) {
            final int hamming = impl.hammingDistance(vector1, vector2);
            assertThat(hamming).isCloseTo(expected, CLOSENESS);
        }

        static Stream<Arguments> hammingDistance() {
            return Stream.of(
                    Arguments.of(origin(42), origin(42), 0),
                    Arguments.of(basisUnitVector(42, 23), basisUnitVector(42, 23), 0),
                    Arguments.of(basisUnitVector(42, 0), origin(42), 1),
                    Arguments.of(vector(1.f, 0.f, 0.f, 0.f), origin(4), 1),
                    Arguments.of(vector(1.f, 1.f, 0.f, 0.f), origin(4), 2),
                    Arguments.of(vector(1.f, 1.f, 1.f, 0.f), origin(4), 3),
                    Arguments.of(vector(1.f, 1.f, 1.f, 1.f), origin(4), 4),
                    Arguments.of(vector(4.f, 0.f, 0.f, 0.f), origin(4), 1),
                    Arguments.of(vector(4.f, 4.f, 0.f, 0.f), origin(4), 2),
                    Arguments.of(vector(4.f, 4.f, 4.f, 0.f), origin(4), 3),
                    Arguments.of(vector(4.f, 4.f, 4.f, 4.f), origin(4), 4),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), origin(4), 4),
                    Arguments.of(vector(-1.f, 2.f, -3.f, 4.f), origin(4), 4),
                    Arguments.of(vector(1.f, 2.f, 3.f, 4.f), vector(-1.f, 2.f, -3.f, 4.f), 2));
        }
    }

    static float randomFloatFor(Random random, int dimensions) {
        final float bound = Math.nextDown((float) (Math.sqrt(Float.MAX_VALUE) / dimensions));
        return random.nextFloat(bound);
    }

    static int randomDimensions(Random random) {
        return random.nextInt(VectorValue.MIN_VECTOR_DIMENSIONS, 1 + VectorValue.MAX_VECTOR_DIMENSIONS);
    }

    static float[] randomRawVector(Random random) {
        return randomRawVector(random, randomDimensions(random));
    }

    static float[] randomRawVector(Random random, int dimensions) {
        boolean origin = true;
        final float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            final float element = randomFloatFor(random, dimensions);
            origin &= element == 0.f;
            vector[i] = element;
        }
        if (origin) {
            final int axis = random.nextInt(dimensions);
            float element;
            do {
                element = randomFloatFor(random, dimensions);
            } while (element != 0.f);
            vector[axis] = element;
        }
        return vector;
    }

    static VectorCandidate vectorCandidate(float... elements) {
        return VectorCandidate.maybeFrom(VirtualValues.list(IntStream.range(0, elements.length)
                .mapToObj(i -> Values.floatValue(elements[i]))
                .toArray(AnyValue[]::new)));
    }

    static VectorCandidate vector(float... elements) {
        return Values.float32Vector(elements);
    }

    static VectorCandidate randomVector(Random random) {
        return vector(randomRawVector(random));
    }

    static VectorCandidate randomVector(Random random, int dimensions) {
        return vector(randomRawVector(random, dimensions));
    }

    static VectorCandidate origin(int dimensions) {
        final float[] vector = new float[dimensions];
        Arrays.fill(vector, 0.f);
        return vector(vector);
    }

    static VectorCandidate basisUnitVector(int dimensions, int basis) {
        final float[] vector = new float[dimensions];
        Arrays.fill(vector, 0.f);
        vector[basis] = 1.f;
        return vector(vector);
    }
}

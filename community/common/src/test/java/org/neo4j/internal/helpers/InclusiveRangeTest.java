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
package org.neo4j.internal.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class InclusiveRangeTest {
    private static final int MIN = -5;
    private static final int MAX = 15;
    private static final int HALF = Math.ceilDiv(MAX - MIN, 2);
    private static final InclusiveRange<Integer> RANGE = new InclusiveRange<>(MIN, MAX);

    @ParameterizedTest
    @MethodSource
    void isBefore(Integer value) {
        assertThat(RANGE.isBefore(value)).isTrue();
    }

    static IntStream isBefore() {
        return IntStream.rangeClosed(MAX + 1, MAX + HALF);
    }

    @ParameterizedTest
    @NullSource
    @MethodSource
    void isNotBefore(Integer value) {
        assertThat(RANGE.isBefore(value)).isFalse();
    }

    static IntStream isNotBefore() {
        return IntStream.concat(isAfter(), contains());
    }

    @ParameterizedTest
    @MethodSource
    void isAfter(Integer value) {
        assertThat(RANGE.isAfter(value)).isTrue();
    }

    static IntStream isAfter() {
        return IntStream.range(MIN - HALF, MIN);
    }

    @ParameterizedTest
    @NullSource
    @MethodSource
    void isNotAfter(Integer value) {
        assertThat(RANGE.isAfter(value)).isFalse();
    }

    static IntStream isNotAfter() {
        return IntStream.concat(contains(), isBefore());
    }

    @ParameterizedTest
    @MethodSource
    void contains(Integer value) {
        assertThat(RANGE.contains(value)).isTrue();
    }

    static IntStream contains() {
        return IntStream.rangeClosed(MIN, MAX);
    }

    @ParameterizedTest
    @NullSource
    @MethodSource
    void doesNotContain(Integer value) {
        assertThat(RANGE.contains(value)).isFalse();
    }

    static IntStream doesNotContain() {
        return IntStream.concat(isBefore(), isAfter());
    }
}

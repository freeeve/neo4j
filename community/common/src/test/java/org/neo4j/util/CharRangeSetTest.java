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
package org.neo4j.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;
import org.eclipse.collections.api.set.primitive.CharSet;
import org.eclipse.collections.impl.factory.primitive.CharLists;
import org.eclipse.collections.impl.factory.primitive.CharSets;
import org.junit.jupiter.api.Test;

class CharRangeSetTest {

    @Test
    void empty() {
        final var set = new CharRangeSet(new char[0]);
        assertThat(set.size()).isZero();
        assertThat(set.contains(new Random().nextInt())).isFalse();
    }

    @Test
    void randomRanges() {
        final var seed = new Random().nextLong();
        final var rand = new Random(seed);

        final var baseline = randomCharSet(rand);
        final var set = from(baseline);

        assertThat(set.size()).describedAs("seed %s: %s", seed, set).isEqualTo(baseline.size());
        for (char i = 0; i < Character.MAX_VALUE; ++i) {
            assertThat(set.contains(i))
                    .describedAs("seed %s: %s contains %s", seed, set, i)
                    .isEqualTo(baseline.contains(i));
        }
        assertThat(set.contains(-1)).describedAs("seed %s: %s", seed, set).isFalse();
        assertThat(set.contains(((int) Character.MAX_VALUE) + 1))
                .describedAs("seed %s: %s", seed, set)
                .isFalse();
    }

    private CharSet randomCharSet(Random rand) {
        final var result = CharSets.mutable.empty();
        final var max = rand.nextInt(Character.MAX_VALUE) + 1;
        final var sizeHint = rand.nextInt(5000);
        for (int i = 0; i < sizeHint; ++i) result.add((char) rand.nextInt(max));
        return result;
    }

    private CharRangeSet from(CharSet set) {
        final var array = set.toArray();
        Arrays.sort(array);
        final var result = CharLists.mutable.empty();

        for (int i = 0; i < array.length; ++i) {
            final var from = array[i];
            while (array[i] == array[i] + 1) {
                ++i;
            }
            result.addAll(from, array[i]);
        }
        return new CharRangeSet(result.toArray());
    }
}

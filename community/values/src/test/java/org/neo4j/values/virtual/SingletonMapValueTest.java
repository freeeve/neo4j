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
package org.neo4j.values.virtual;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.neo4j.values.storable.Values.NO_VALUE;
import static org.neo4j.values.storable.Values.intValue;

import org.junit.jupiter.api.Test;

class SingletonMapValueTest {

    @Test
    void shouldGetFromMap() {
        // given
        var map = new SingletonMapValue("a", intValue(1));

        // then
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.isEmpty()).isFalse();
        assertThat(map.get("a")).isEqualTo(intValue(1));
        assertThat(map.get("b")).isEqualTo(NO_VALUE);
    }

    @Test
    void shouldContainsKey() {
        // given
        var map = new SingletonMapValue("a", intValue(1));

        // then
        assertThat(map.containsKey("a")).isTrue();
        assertThat(map.containsKey("b")).isFalse();
    }

    @Test
    void shouldForEach() {
        // given
        var map = new SingletonMapValue("a", intValue(1));

        // then
        var seen = new boolean[] {false};
        map.foreach((key, value) -> {
            assertThat(key).isEqualTo("a");
            assertThat(value).isEqualTo(intValue(1));
            seen[0] = true;
        });
        assertThat(seen[0]).isTrue();
    }
}

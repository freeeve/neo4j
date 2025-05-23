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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.values.storable.Values;

class SortedKeysMapValueTest {

    @Test
    void shouldGetFromMap() {
        // given
        var map = mapValue(Map.of("a", 1, "b", 2, "c", 3));

        // then
        assertThat(map.get("a")).isEqualTo(intValue(1));
        assertThat(map.get("b")).isEqualTo(intValue(2));
        assertThat(map.get("c")).isEqualTo(intValue(3));
        assertThat(map.get("d")).isEqualTo(NO_VALUE);
    }

    @Test
    void shouldContainsKey() {
        // given
        var map = mapValue(50, 100);

        // then
        for (int i = 0; i < 200; i++) {
            assertThat(map.containsKey(Integer.toString(i))).isEqualTo(i >= 50 && i <= 100);
        }
    }

    @Test
    void shouldForEach() {
        // given
        var map = mapValue(10, 1000);

        // then
        var mapBuilder = new MapValueBuilder();
        map.foreach(mapBuilder::add);

        assertThat(mapBuilder.build()).isEqualTo(map);
    }

    private MapValue mapValue(int start, int end) {
        var res = new HashMap<String, Integer>();
        for (int i = start; i <= end; i++) {
            res.put(Integer.toString(i), i);
        }
        return mapValue(res);
    }

    private MapValue mapValue(Map<String, ?> map) {
        String[] keys = map.keySet().toArray(new String[0]);
        Arrays.sort(keys);

        var res = new SortedKeysMapValue(keys);
        for (int i = 0; i < keys.length; i++) {
            res.internalPut(i, Values.of(map.get(keys[i])));
        }
        return res;
    }
}

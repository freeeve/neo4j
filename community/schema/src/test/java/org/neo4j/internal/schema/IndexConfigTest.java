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
package org.neo4j.internal.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.Value;

class IndexConfigTest {
    @Test
    void addingAndGetting() {
        IndexConfig config = IndexConfig.empty();
        config = config.withIfAbsent("a", BooleanValue.TRUE);
        assertThat(config.<BooleanValue>get("a").booleanValue()).isTrue();

        config = config.withIfAbsent("a", BooleanValue.FALSE);
        assertThat(config.<BooleanValue>get("a").booleanValue()).isTrue();

        assertThat(config.<BooleanValue>get("b")).isNull();
        assertThat(config.getOrDefault("b", BooleanValue.FALSE).booleanValue()).isFalse();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldNotBePossibleToMutateIndexConfigFromAsMap() {
        IndexConfig config = IndexConfig.empty();
        config = config.withIfAbsent("a", BooleanValue.TRUE);
        config = config.withIfAbsent("b", BooleanValue.TRUE);

        Map<String, Value> map = config.asMap();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> map.remove("a"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> map.put("b", BooleanValue.FALSE));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> map.put("c", BooleanValue.TRUE));

        assertThat(config.<BooleanValue>get("a").booleanValue()).isTrue();
        assertThat(config.<BooleanValue>get("b").booleanValue()).isTrue();
        assertThat(config.<BooleanValue>get("c")).isNull();
    }
}

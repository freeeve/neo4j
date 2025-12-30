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

import static org.neo4j.memory.HeapEstimator.sizeOf;

import java.util.HashMap;
import java.util.Map;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.values.AnyValue;

public class MapValueBuilder {
    private final Map<String, AnyValue> map;
    private long payloadSize;

    public MapValueBuilder() {
        this.map = new HashMap<>();
    }

    public MapValueBuilder(int expectedSize) {
        this.map = HashMap.newHashMap(expectedSize);
    }

    public AnyValue add(String key, AnyValue value) {
        AnyValue put = map.put(key, value);
        if (put == null) {
            payloadSize += sizeOf(key) + value.estimatedHeapUsage();
        } else {
            payloadSize += value.estimatedHeapUsage() - put.estimatedHeapUsage();
        }
        return put;
    }

    public void clear() {
        map.clear();
        payloadSize = 0;
    }

    public MapValue build() {
        int size = map.size();
        if (size == 0) {
            return MapValue.EMPTY;
        } else if (size == 1) {
            var first = Iterables.first(map.entrySet());
            return new SingletonMapValue(first.getKey(), first.getValue());
        } else {
            return new MapValue.MapWrappingMapValue(map, payloadSize);
        }
    }
}

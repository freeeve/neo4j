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

import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;
import static org.neo4j.memory.HeapEstimator.sizeOf;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.util.List;
import org.neo4j.function.ThrowingBiConsumer;
import org.neo4j.values.AnyValue;

public final class SingletonMapValue extends MapValue {
    private static final long SHALLOW_SIZE = shallowSizeOfInstance(SingletonMapValue.class);

    private final String key;
    private final AnyValue value;

    public SingletonMapValue(String key, AnyValue value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public Iterable<String> keySet() {
        return List.of(key);
    }

    @Override
    public <E extends Exception> void foreach(ThrowingBiConsumer<String, AnyValue, E> f) throws E {
        f.accept(key, value);
    }

    @Override
    public boolean containsKey(String key) {
        return this.key.equals(key);
    }

    @Override
    public AnyValue get(String key) {
        return this.key.equals(key) ? value : NO_VALUE;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public long estimatedHeapUsage() {
        return SHALLOW_SIZE + sizeOf(key) + value.estimatedHeapUsage();
    }
}

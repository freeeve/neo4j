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

import static org.apache.commons.lang3.ArrayUtils.isSorted;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfObjectArray;
import static org.neo4j.memory.HeapEstimator.sizeOf;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.util.Arrays;
import org.neo4j.function.ThrowingBiConsumer;
import org.neo4j.values.AnyValue;

/**
 * Special case of MapValue intended used for example from generated code.
 * <br>
 * In this case we know the keys up front and can thus presort the keys at compile time
 * and use {@link #internalPut(int, AnyValue)} to populate the map, or provide keys and values directly.
 * Note that {@link #get(String)} and {@link #containsKey(String)} are O(log(n)) rather than O(n).
 */
public final class SortedKeysMapValue extends MapValue {
    private static final long COMPILED_MAP_VALUE_SHALLOW_SIZE = shallowSizeOfInstance(SortedKeysMapValue.class);
    // assumes sorted keys
    private final String[] keys;
    private final AnyValue[] values;

    private static final long NOT_MEMOIZED = -1;
    private volatile long memoizedEstimatedHeapUsage;

    public SortedKeysMapValue(String[] keys) {
        this(keys, new AnyValue[keys.length]);
    }

    public SortedKeysMapValue(String[] keys, AnyValue[] values) {
        assert keys.length == values.length;
        assert isSorted(keys);
        this.keys = keys;
        this.values = values;
        this.memoizedEstimatedHeapUsage = NOT_MEMOIZED;
    }

    @Override
    public Iterable<String> keySet() {
        return Arrays.asList(keys);
    }

    @Override
    public <E extends Exception> void foreach(ThrowingBiConsumer<String, AnyValue, E> f) throws E {
        for (int i = 0; i < keys.length; i++) {
            f.accept(keys[i], values[i]);
        }
    }

    /**
     * This populates the map, before this has been called for all items the map is in an undefined state.
     */
    public void internalPut(int index, AnyValue value) {
        assert memoizedEstimatedHeapUsage == NOT_MEMOIZED;
        values[index] = value;
    }

    @Override
    public boolean containsKey(String key) {
        return Arrays.binarySearch(keys, key) >= 0;
    }

    @Override
    public AnyValue get(String key) {
        var index = Arrays.binarySearch(keys, key);
        return index >= 0 ? values[index] : NO_VALUE;
    }

    @Override
    public int size() {
        return keys.length;
    }

    @Override
    public boolean isEmpty() {
        return keys.length == 0;
    }

    @Override
    public long estimatedHeapUsage() {
        long tmp = memoizedEstimatedHeapUsage;
        if (tmp == NOT_MEMOIZED) {
            int length = keys.length;
            tmp = COMPILED_MAP_VALUE_SHALLOW_SIZE + 2 * shallowSizeOfObjectArray(length);
            int i = 0;
            while (i < length) {
                tmp += values[i].estimatedHeapUsage() + sizeOf(keys[i]);
                i += 1;
            }
            memoizedEstimatedHeapUsage = tmp;
        }
        return tmp;
    }
}

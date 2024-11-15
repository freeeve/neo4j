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
import static org.neo4j.values.SequenceValue.IterationPreference.ITERATION;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.util.Iterator;
import java.util.LinkedHashSet;
import org.github.jamm.Unmetered;
import org.neo4j.values.AnyValue;
import org.neo4j.values.Equality;
import org.neo4j.values.SequenceValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueRepresentation;
import org.neo4j.values.storable.Values;

/**
 * A SetListValue is specialized ListValue that can be used for `collect(DISTINCT ...)`.
 * <p>
 * Uses a LinkedHashSet<T> internally so that insertion order is preserved. Note that it is not allowed
 * to store a NO_VALUE in the set.
 */
public final class SetListValue extends ListValue {
    private static final long SET_LIST_VALUE_SHALLOW_SIZE = shallowSizeOfInstance(SetListValue.class);

    public static final class Builder {
        private static final long LINKED_SET_SHALLOW_SIZE = shallowSizeOfInstance(LinkedHashSet.class);
        private long estimatedHeapUsage;
        private ValueRepresentation valueRepresentation;
        private final LinkedHashSet<AnyValue> set = new LinkedHashSet<>();

        private Builder() {
            estimatedHeapUsage = LINKED_SET_SHALLOW_SIZE;
            valueRepresentation = ValueRepresentation.ANYTHING;
        }

        public void add(AnyValue value) {
            assert value != NO_VALUE;
            if (set.add(value)) {
                estimatedHeapUsage += value.estimatedHeapUsage();
                valueRepresentation = valueRepresentation.coerce(value.valueRepresentation());
            }
            ;
        }

        public SetListValue build() {
            return new SetListValue(set, estimatedHeapUsage, valueRepresentation);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Unmetered
    private final ValueRepresentation itemRepresentation;

    private final LinkedHashSet<AnyValue> set;
    private final long payload;

    SetListValue(LinkedHashSet<AnyValue> set, long payload, ValueRepresentation itemRepresentation) {
        this.itemRepresentation = itemRepresentation;
        this.set = set;
        this.payload = payload;
    }

    @Override
    public ValueRepresentation itemValueRepresentation() {
        return itemRepresentation;
    }

    @Override
    public long estimatedHeapUsage() {
        return SET_LIST_VALUE_SHALLOW_SIZE + payload;
    }

    @Override
    public long actualSize() {
        return set.size();
    }

    @Override
    public AnyValue value(long offset) {
        long i = 0;
        for (var e : this) {
            if (i++ == offset) {
                return e;
            }
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Value ternaryContains(AnyValue value) {
        if (value instanceof SequenceValue || value instanceof MapValue) {
            return ternaryContainsMayHaveNull(value);
        } else {
            return ternaryContainsSafe(value);
        }
    }

    private Value ternaryContainsSafe(AnyValue value) {
        return value != NO_VALUE ? Values.booleanValue(set.contains(value)) : NO_VALUE;
    }

    private Value ternaryContainsMayHaveNull(AnyValue value) {
        if (set.contains(value)) {
            // TODO: this is here since [1, NULL, 2] IN SET should return NO_VALUE if it is stored in the
            //      set. I think we can do this nicer, stay tuned.
            return value.ternaryEquals(value) == Equality.TRUE ? Values.TRUE : NO_VALUE;
        } else if (set.contains(NO_VALUE)) {
            return NO_VALUE;
        } else {
            return super.ternaryContains(value);
        }
    }

    @Override
    public ListValue distinct() {
        return this;
    }

    @Override
    public Iterator<AnyValue> iterator() {
        return set.iterator();
    }

    @Override
    public IterationPreference iterationPreference() {
        return ITERATION;
    }
}

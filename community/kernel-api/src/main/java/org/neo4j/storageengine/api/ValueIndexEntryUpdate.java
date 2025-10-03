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
package org.neo4j.storageengine.api;

import static java.lang.String.format;

import java.util.Arrays;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.SchemaDescriptorSupplier;
import org.neo4j.values.storable.Value;

public final class ValueIndexEntryUpdate extends IndexEntryUpdate {
    private final Value[] before;
    private final Value[] values;

    ValueIndexEntryUpdate(long entityId, IndexDescriptor indexKey, UpdateMode updateMode, Value[] values) {
        this(entityId, indexKey, updateMode, null, values);
    }

    ValueIndexEntryUpdate(
            long entityId, IndexDescriptor indexKey, UpdateMode updateMode, Value[] before, Value[] values) {
        super(entityId, indexKey, updateMode);
        validateValuesLength(indexKey, before, values);

        this.before = before;
        this.values = values;
    }

    public static ValueIndexEntryUpdate add(long entityId, IndexDescriptor indexKey, Value... values) {
        return new ValueIndexEntryUpdate(entityId, indexKey, UpdateMode.ADDED, values);
    }

    public static ValueIndexEntryUpdate remove(long entityId, IndexDescriptor indexKey, Value... values) {
        return new ValueIndexEntryUpdate(entityId, indexKey, UpdateMode.REMOVED, values);
    }

    public static ValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value before, Value after) {
        return new ValueIndexEntryUpdate(
                entityId, indexKey, UpdateMode.CHANGED, new Value[] {before}, new Value[] {after});
    }

    public static ValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value[] before, Value[] after) {
        return new ValueIndexEntryUpdate(entityId, indexKey, UpdateMode.CHANGED, before, after);
    }

    public Value[] values() {
        return values;
    }

    public Value[] beforeValues() {
        if (before == null) {
            throw new UnsupportedOperationException("beforeValues is only valid for `UpdateMode.CHANGED");
        }
        return before;
    }

    @Override
    public long roughSizeOfUpdate() {
        return heapSizeOf(values) + (updateMode() == UpdateMode.CHANGED ? heapSizeOf(before) : 0);
    }

    @Override
    protected boolean valueEquals(IndexEntryUpdate o) {
        if (!(o instanceof ValueIndexEntryUpdate that)) {
            return false;
        }
        if (!Arrays.equals(before, that.before)) {
            return false;
        }
        return Arrays.equals(values, that.values);
    }

    @Override
    protected int valueHash() {
        int result = Arrays.hashCode(before);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    protected String valueToString() {
        return String.format("beforeValues=%s, values=%s", Arrays.toString(before), Arrays.toString(values));
    }

    @Override
    public String toString() {
        return "ValueIndexEntryUpdate{" + "entity=" + getEntityId() + ", before=" + Arrays.toString(before)
                + ", values=" + Arrays.toString(values) + ", updateMode=" + updateMode() + ", index:" + indexKey()
                + '}';
    }

    @Override
    public IndexEntryUpdate withEntityId(long entityId) {
        return switch (updateMode()) {
            case ADDED -> add(entityId, indexKey(), values);
            case CHANGED -> change(entityId, indexKey(), before, values);
            case REMOVED -> remove(entityId, indexKey(), values);
        };
    }

    private static void validateValuesLength(SchemaDescriptorSupplier indexKey, Value[] before, Value[] values) {
        // we do not support partial index entries
        assert indexKey.schema().getPropertyIds().length == values.length
                : format(
                        "ValueIndexEntryUpdate values must be of same length as index compositeness. "
                                + "Index on %s, but got values %s",
                        indexKey.schema().toString(), Arrays.toString(values));
        assert before == null || before.length == values.length;
    }

    private static long heapSizeOf(Value[] values) {
        long size = 0;
        if (values != null) {
            for (Value value : values) {
                if (value != null) {
                    size += value.estimatedHeapUsage();
                }
            }
        }
        return size;
    }
}

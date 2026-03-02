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
package org.neo4j.kernel.impl.index.schema;

import java.util.StringJoiner;
import java.util.UUID;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.values.storable.UUIDValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;
import org.neo4j.values.storable.Values;

class UUIDType extends Type {
    // Affected key state:
    // long0 (msb)
    // long1 (lsb)

    UUIDType(byte typeId) {
        super(ValueGroup.UUID, typeId, UUIDValue.MIN_VALUE, UUIDValue.MAX_VALUE);
    }

    @Override
    int valueSize(GenericKey<?> state) {
        return Types.SIZE_UUID;
    }

    @Override
    void copyValue(GenericKey<?> to, GenericKey<?> from) {
        to.long0 = from.long0;
        to.long1 = from.long1;
    }

    @Override
    Value asValue(GenericKey<?> state) {
        return asValue(state.long0, state.long1);
    }

    @Override
    int compareValue(GenericKey<?> left, GenericKey<?> right) {
        return compare(left.long0, left.long1, right.long0, right.long1);
    }

    @Override
    void putValue(PageCursor cursor, GenericKey<?> state) {
        put(cursor, state.long0, state.long1);
    }

    @Override
    boolean readValue(PageCursor cursor, int size, GenericKey<?> into) {
        return read(cursor, into);
    }

    static UUIDValue asValue(long long0, long long1) {
        return Values.uuidValue(long0, long1);
    }

    static UUID asValueRaw(long long0, long long1) {
        return new UUID(long0, long1);
    }

    static int compare(long this_long0, long this_long1, long that_long0, long that_long1) {
        return UUIDValue.compareUUIDs(this_long0, this_long1, that_long0, that_long1);
    }

    static void put(PageCursor cursor, long long0, long long1) {
        cursor.putLong(long0);
        cursor.putLong(long1);
    }

    static boolean read(PageCursor cursor, GenericKey<?> into) {
        into.writeUUID(cursor.getLong(), cursor.getLong());
        return true;
    }

    static void write(GenericKey<?> state, long msb, long lsb) {
        state.long0 = msb;
        state.long1 = lsb;
    }

    @Override
    protected void addTypeSpecificDetails(StringJoiner joiner, GenericKey<?> state) {
        joiner.add("long0=" + state.long0);
        joiner.add("long1=" + state.long1);
    }
}

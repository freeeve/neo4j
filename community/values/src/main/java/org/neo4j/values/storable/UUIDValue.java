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
package org.neo4j.values.storable;

import static java.lang.String.format;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;

import java.util.UUID;
import org.neo4j.hashing.HashFunction;
import org.neo4j.values.Comparison;
import org.neo4j.values.ValueMapper;

public class UUIDValue extends ScalarValue {
    private static final long INSTANCE_SIZE =
            shallowSizeOfInstance(UUIDValue.class) + shallowSizeOfInstance(UUID.class);
    public static final UUIDValue MIN_VALUE = new UUIDValue(new UUID(Long.MIN_VALUE, Long.MIN_VALUE));
    public static final UUIDValue MAX_VALUE = new UUIDValue(new UUID(Long.MAX_VALUE, Long.MAX_VALUE));
    public static final String TYPE_NAME = "UUID";

    private final UUID uuid;

    UUIDValue(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public boolean equals(Value other) {
        return other instanceof UUIDValue && uuid.equals(((UUIDValue) other).uuid);
    }

    @Override
    protected int unsafeCompareTo(Value otherValue) {
        UUIDValue other = (UUIDValue) otherValue;
        return compareUUIDs(uuid, other.uuid);
    }

    @Override
    public Comparison unsafeTernaryCompareTo(Value otherValue) {
        // UUID values are not comparable under Comparability semantics,
        // unless they are equal.
        if (equals(otherValue)) {
            return Comparison.EQUAL;
        } else {
            return Comparison.UNDEFINED;
        }
    }

    @Override
    public boolean isIncomparableType() {
        return true;
    }

    public static int compareUUIDs(UUID o1, UUID o2) {
        return compareUUIDs(
                o1.getMostSignificantBits(),
                o1.getLeastSignificantBits(),
                o2.getMostSignificantBits(),
                o2.getLeastSignificantBits());
    }

    public long getMostSignificantBits() {
        return uuid.getMostSignificantBits();
    }

    public long getLeastSignificantBits() {
        return uuid.getLeastSignificantBits();
    }

    public static int compareUUIDs(long o1Msb, long o1Lsb, long o2Msb, long o2Lsb) {
        int msbCompare = Long.compareUnsigned(o1Msb, o2Msb);
        return msbCompare != 0 ? msbCompare : Long.compareUnsigned(o1Lsb, o2Lsb);
    }

    @Override
    public <E extends Exception> void writeTo(ValueWriter<E> writer) throws E {
        writeUUID(writer, uuid);
    }

    static <E extends Exception> void writeUUID(ValueWriter<E> writer, UUID uuid) throws E {
        writer.writeUUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    @Override
    public UUID asObjectCopy() {
        return uuid;
    }

    @Override
    public String prettyPrint() {
        return uuid.toString();
    }

    @Override
    protected int computeHash() {
        return uuid.hashCode();
    }

    @Override
    public <T> T map(ValueMapper<T> mapper) {
        return mapper.mapUUID(this);
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public ValueRepresentation valueRepresentation() {
        return ValueRepresentation.UUID;
    }

    @Override
    public long updateHash(HashFunction hashFunction, long hash) {
        return hashFunction.update(hash, hashCode());
    }

    @Override
    public long estimatedHeapUsage() {
        return INSTANCE_SIZE;
    }

    @Override
    public String toString() {
        return format("%s(%s)", getTypeName(), uuid);
    }
}

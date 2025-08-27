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
package org.neo4j.internal.helpers.collection;

import java.lang.reflect.Array;
import java.util.function.Function;

/**
 * Class implementing an efficient (array-based) enum lookup keyed by byte
 * @param <T> the enum class of the elements
 */
public class ByteToEnum<T extends Enum<T>> {
    private final T[] table;

    /**
     *
     * @param type the enum class of the {@code ByteToEnum}
     * @param byteFunction function to fetch/calculate the byte value of each enum instance
     */
    @SuppressWarnings("unchecked")
    public ByteToEnum(Class<T> type, Function<T, Byte> byteFunction) {
        this.table = (T[]) Array.newInstance(type, 1 << Byte.SIZE);
        for (T e : type.getEnumConstants()) {
            table[Byte.toUnsignedInt(byteFunction.apply(e))] = e;
        }
    }

    /**
     * Retrieve the enum value corresponding to a byte
     * @param index byte with which to look up the enum
     * @return the enum value corresponding to the supplied index
     */
    public T get(byte index) {
        return table[Byte.toUnsignedInt(index)];
    }
}

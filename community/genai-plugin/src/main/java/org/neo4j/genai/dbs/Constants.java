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
package org.neo4j.genai.dbs;

import java.util.Map;
import java.util.Optional;

/**
 * A common interface for holders of constants for configuring vector embeddings and mappings through generic {@link java.util.Map maps}.
 */
public interface Constants {

    /**
     * {@return the key of this constant to be used in maps etc}
     */
    String key();

    /**
     * {@return an optional default value}
     */
    Optional<Object> defaultValue();

    default <T> T get(Class<T> type, Map<String, Object> config) {
        var value = Optional.ofNullable(config.get(this.key()))
                .or(this::defaultValue)
                .orElse(null);
        if (type == Boolean.class || type == boolean.class) {
            value = toBoolean(value);
        }
        return type.cast(value);
    }

    private static boolean toBoolean(Object value) {
        return value != null
                && (!(value instanceof Number) || (((Number) value).longValue()) != 0L)
                && (!(value instanceof String)
                        || (!value.equals("")
                                && !((String) value).equalsIgnoreCase("false")
                                && !((String) value).equalsIgnoreCase("no")
                                && !((String) value).equalsIgnoreCase("0")))
                && (!(value instanceof Boolean) || !value.equals(false));
    }
}

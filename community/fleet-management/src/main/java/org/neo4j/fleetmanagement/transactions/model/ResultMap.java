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

package org.neo4j.fleetmanagement.transactions.model;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultMap extends HashMap<String, Object> {
    public ResultMap(Map<String, Object> m) {
        this.putAll(m);
    }

    public String getString(String key, String fallback) {
        Object value = this.get(key);
        return value != null ? value.toString() : fallback;
    }

    public String getString(String key) throws IllegalArgumentException {
        Object value = this.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
        return value.toString();
    }

    public List<String> getStringList(String key) {
        Object value = this.get(key);
        if (value instanceof List) {
            try {
                return (List<String>) value;
            } catch (ClassCastException e) {
                throw new IllegalArgumentException(
                        "Value for '" + key + "' cannot be converted to List<String>: " + value, e);
            }
        }
        throw new IllegalArgumentException("Value for '" + key + "' cannot be converted to List<String>: "
                + value.getClass().getName());
    }

    public boolean getBoolean(String key) throws IllegalArgumentException {
        Object value = this.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    public long getLong(String key) throws IllegalArgumentException {
        Object value = this.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value for '" + key + "' cannot be converted to long: " + value);
        }
    }

    public Integer getInteger(String key) throws IllegalArgumentException {
        Object value = this.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value for '" + key + "' cannot be converted to Integer: " + value);
        }
    }

    public Integer getInteger(String key, Integer fallback) throws IllegalArgumentException {
        Object value = this.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value for '" + key + "' cannot be converted to Integer: " + value);
        }
    }

    public ZonedDateTime getZonedDateTime(String key) throws IllegalArgumentException {
        Object value = this.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
        if (value instanceof ZonedDateTime) {
            return (ZonedDateTime) value;
        }
        try {
            return ZonedDateTime.parse(value.toString());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Value for '" + key + "' cannot be converted to ZonedDateTime: " + value);
        }
    }
}

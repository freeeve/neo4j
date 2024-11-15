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

public record RequestConfig(boolean allResults, boolean metaAsSubKey) {

    static RequestConfig of(Map<String, Object> config) {
        return new RequestConfig(config == null ? Map.of() : config);
    }

    private RequestConfig(Map<String, Object> config) {
        this(Keys.ALL_RESULTS.get(Boolean.class, config), Keys.META_AS_SUBKEY.get(Boolean.class, config));
    }

    public enum Keys implements Constants {
        HEADERS("headers"),
        TOKEN("token"),
        AUTHORIZATION("Authorization"),
        ALL_RESULTS("allResults"),
        META_AS_SUBKEY("metaAsSubKey", true),
        WAIT("wait", false);

        private final String key;
        private final Object defaultValue;

        Keys(String value) {
            this(value, null);
        }

        Keys(String value, Object defaultValue) {
            this.key = value;
            this.defaultValue = defaultValue;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Optional<Object> defaultValue() {
            return Optional.ofNullable(this.defaultValue);
        }
    }
}

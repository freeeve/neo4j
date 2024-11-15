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

public record RowMappingConfig(String idKey, String textKey, String vectorKey, String metadataKey, String scoreKey) {

    static RowMappingConfig of(Map<String, Object> config) {
        return new RowMappingConfig(config == null ? Map.of() : config);
    }

    private RowMappingConfig(Map<String, Object> config) {
        this(
                Keys.ID.get(String.class, config),
                Keys.TEXT.get(String.class, config),
                Keys.VECTOR.get(String.class, config),
                Keys.METADATA.get(String.class, config),
                Keys.SCORE.get(String.class, config));
    }

    /**
     * Constants that are used in the configuration {@link Map maps} passed to the procedures of {@link VectorDatabases}.
     */
    public enum Keys implements Constants {
        FIELDS("fields"),
        VECTOR("vectorKey", "vector"),
        METADATA("metadataKey", "metadata"),
        SCORE("scoreKey", "score"),
        TEXT("textKey", "text"),
        ID("idKey", "id"),
        MAPPING("mapping", Map.of());

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

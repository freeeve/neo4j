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

public record EntityMappingConfig(
        String metadataKey,
        String entityKey,
        String nodeLabel,
        String relType,
        String embeddingKey,
        String similarity,
        MappingMode mode) {

    static EntityMappingConfig of(Map<String, Object> config, Boolean forceReadOnly) {
        return new EntityMappingConfig(config == null ? Map.of() : config, forceReadOnly);
    }

    private EntityMappingConfig(Map<String, Object> mapping, Boolean forceReadOnly) {

        this(
                Keys.METADATA.get(String.class, mapping),
                Keys.ENTITY.get(String.class, mapping),
                Keys.NODE_LABEL.get(String.class, mapping),
                Keys.REL_TYPE.get(String.class, mapping),
                Keys.EMBEDDING.get(String.class, mapping),
                Keys.SIMILARITY.get(String.class, mapping),
                Boolean.TRUE.equals(forceReadOnly)
                        ? MappingMode.READ_ONLY
                        : MappingMode.valueOf(Keys.MODE.get(String.class, mapping)));
    }

    public boolean readOnly() {
        return mode() == MappingMode.READ_ONLY;
    }

    enum MappingMode {
        READ_ONLY,
        UPDATE_EXISTING,
        CREATE_IF_MISSING
    }

    /**
     * Constants for the keys used to configure the entity- and result-set mapping via {@link Map maps} passed to the procedures of {@link VectorDatabases}.
     */
    public enum Keys implements Constants {
        METADATA("metadataKey"),
        ENTITY("entityKey"),
        NODE_LABEL("nodeLabel"),
        REL_TYPE("relType"),
        EMBEDDING("embeddingKey"),
        SIMILARITY("similarity", "cosine"),
        MODE("mode", MappingMode.UPDATE_EXISTING.toString());

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
            return Optional.ofNullable(defaultValue);
        }
    }
}

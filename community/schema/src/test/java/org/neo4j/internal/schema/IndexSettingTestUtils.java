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
package org.neo4j.internal.schema;

import org.neo4j.graphdb.schema.IndexSetting;

public class IndexSettingTestUtils {
    private IndexSettingTestUtils() {}

    public static final Object FAKE_VALUE = new Object() {
        @Override
        public String toString() {
            return "FAKE_VALUE";
        }
    };

    public enum Lookup {
        FOO,
        BAR,
        BAZ
    }

    public enum TestIndexSetting implements IndexSetting {
        OBJECT(Object.class),
        BOOLEAN(Boolean.class),
        DOUBLE(Double.class),
        INTEGER(Integer.class),
        STRING(String.class),
        ;

        private final Class<?> type;

        TestIndexSetting(Class<?> type) {
            this.type = type;
        }

        @Override
        public String getSettingName() {
            return name();
        }

        @Override
        public Class<?> getType() {
            return type;
        }
    }
}

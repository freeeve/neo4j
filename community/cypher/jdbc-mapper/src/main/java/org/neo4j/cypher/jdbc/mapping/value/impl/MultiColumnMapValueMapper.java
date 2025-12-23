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
package org.neo4j.cypher.jdbc.mapping.value.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.neo4j.cypher.jdbc.mapping.value.JdbcValueMapper;
import org.neo4j.values.AnyValue;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.SortedKeysMapValue;

/** Maps multiple jdbc result set columns to a neo4j map value. */
public final class MultiColumnMapValueMapper implements JdbcValueMapper {
    private final String[] sortedKeys;
    private final JdbcValueMapper[] valueMappers;

    public MultiColumnMapValueMapper(Map<String, JdbcValueMapper> propertiesMappers) {
        final var sortedEntries = propertiesMappers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        this.sortedKeys = new String[sortedEntries.size()];
        this.valueMappers = new JdbcValueMapper[sortedEntries.size()];
        for (int i = 0; i < sortedEntries.size(); ++i) {
            this.sortedKeys[i] = sortedEntries.get(i).getKey();
            this.valueMappers[i] = sortedEntries.get(i).getValue();
        }
    }

    @Override
    public MapValue toNeo4jValue(ResultSet result) throws SQLException {
        final var neo4jValues = new AnyValue[valueMappers.length];
        for (int i = 0; i < valueMappers.length; ++i) {
            neo4jValues[i] = valueMappers[i].toNeo4jValue(result);
        }
        // Using SortedKeysMapValue is a space and time optimisation that has proven significant before.
        return new SortedKeysMapValue(sortedKeys, neo4jValues);
    }
}

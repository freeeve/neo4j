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
import org.neo4j.cypher.jdbc.mapping.value.JdbcValueMapper;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.Values;

/** Maps a jdbc result set long value to a neo4j value. */
public final class LongMapping implements JdbcValueMapper {
    private final int column;

    public LongMapping(int column) {
        this.column = column;
    }

    @Override
    public AnyValue toNeo4jValue(ResultSet result) throws SQLException {
        final var value = result.getLong(column);
        return result.wasNull() ? Values.NO_VALUE : Values.longValue(value);
    }
}

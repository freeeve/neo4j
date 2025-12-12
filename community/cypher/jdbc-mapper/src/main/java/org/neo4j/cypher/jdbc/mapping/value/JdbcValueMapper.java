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
package org.neo4j.cypher.jdbc.mapping.value;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.neo4j.values.AnyValue;

public interface JdbcValueMapper {

    /**
     * Converts one or more columns from the jdbc result set to a single neo4j value.
     * Implementations are NOT allowed to mutate the ResultSet, only read values from the current position.
     * Should never return java null values.
     */
    AnyValue toNeo4jValue(ResultSet result) throws SQLException;
}

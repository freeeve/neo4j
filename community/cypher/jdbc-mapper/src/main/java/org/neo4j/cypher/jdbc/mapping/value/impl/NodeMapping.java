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
import org.neo4j.cypher.jdbc.mapping.id.GlobalIds;
import org.neo4j.cypher.jdbc.mapping.value.JdbcValueMapper;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.TextArray;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.VirtualValues;

/** Maps a virtual node from a jdbc result set to a neo4j value. */
public final class NodeMapping implements JdbcValueMapper {
    private final GlobalIds globalIds;
    private final String schemaId;
    private final int externalIdColumn;
    private final TextArray labels;
    private final MultiColumnMapValueMapper propertiesMapper;

    NodeMapping(
            GlobalIds globalIds,
            String schemaId,
            int externalIdColumn,
            TextArray labels,
            MultiColumnMapValueMapper propertiesMapper) {
        this.globalIds = globalIds;
        this.schemaId = schemaId;
        this.externalIdColumn = externalIdColumn;
        this.labels = labels;
        this.propertiesMapper = propertiesMapper;
    }

    @Override
    public AnyValue toNeo4jValue(ResultSet result) throws SQLException {
        final var externalId = result.getObject(externalIdColumn);
        if (externalId == null) {
            return Values.NO_VALUE;
        }

        final var id = globalIds.nodeId(schemaId, externalId);
        final var elementId = globalIds.idMapper().nodeElementId(id);
        final var properties = propertiesMapper.toNeo4jValue(result);
        return VirtualValues.nodeValue(id, elementId, labels, properties);
    }
}

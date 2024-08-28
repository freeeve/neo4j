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
package org.neo4j.internal.batchimport;

import java.util.HashMap;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.neo4j.common.EntityType;
import org.neo4j.internal.schema.SchemaCache;

public class ImportPropertyConstraintEnforcer {
    private final IntObjectMap<int[]> propertyExistenceConstraints;

    public ImportPropertyConstraintEnforcer(SchemaCache schemaCache, EntityType entityType) {
        this.propertyExistenceConstraints = buildPropertyExistenceConstraintsMap(schemaCache, entityType);
    }

    private static IntObjectMap<int[]> buildPropertyExistenceConstraintsMap(
            SchemaCache schemaCache, EntityType entityType) {
        var builder = new HashMap<Integer, MutableIntSet>();
        for (var constraint : schemaCache.constraints()) {
            if (constraint.enforcesPropertyExistence() && constraint.schema().entityType() == entityType) {
                var schema = constraint.schema();
                for (var entityToken : schema.getEntityTokenIds()) {
                    builder.computeIfAbsent(entityToken, t -> IntSets.mutable.empty())
                            .addAll(schema.getPropertyIds());
                }
            }
        }
        if (builder.isEmpty()) {
            return null;
        }
        var propertyExistenceConstraints = IntObjectMaps.mutable.<int[]>empty();
        builder.forEach((key, value) -> propertyExistenceConstraints.put(key, value.toSortedArray()));
        return propertyExistenceConstraints;
    }

    public boolean isEmpty() {
        return propertyExistenceConstraints == null;
    }

    public int[] mandatoryPropertyKeys(int entityToken) {
        return isEmpty() ? null : propertyExistenceConstraints.get(entityToken);
    }
}

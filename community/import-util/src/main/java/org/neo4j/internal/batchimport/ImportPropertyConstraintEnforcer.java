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

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.neo4j.common.EntityType;
import org.neo4j.internal.schema.SchemaCache;
import org.neo4j.internal.schema.constraints.PropertyTypeSet;

public class ImportPropertyConstraintEnforcer {
    private final IntObjectMap<MutableIntSet> entityTokenToPropertyKeys;
    private final IntObjectMap<MutableIntSet> propertyKeyToEntityTokens;
    private final IntObjectMap<List<PropertyAndType>> typeConstraints;

    public ImportPropertyConstraintEnforcer(SchemaCache schemaCache, EntityType entityType) {
        this.entityTokenToPropertyKeys = buildPropertyExistenceConstraintsMap(schemaCache, entityType);
        this.typeConstraints = buildPropertyTypeConstraintsMap(schemaCache, entityType);
        this.propertyKeyToEntityTokens = entityTokenToPropertyKeys != null ? reverse(entityTokenToPropertyKeys) : null;
    }

    private static IntObjectMap<MutableIntSet> buildPropertyExistenceConstraintsMap(
            SchemaCache schemaCache, EntityType entityType) {
        MutableIntObjectMap<MutableIntSet> map = IntObjectMaps.mutable.empty();
        for (var constraint : schemaCache.constraints()) {
            if (constraint.enforcesPropertyExistence() && constraint.schema().entityType() == entityType) {
                var schema = constraint.schema();
                for (var entityToken : schema.getEntityTokenIds()) {
                    map.getIfAbsentPut(entityToken, IntSets.mutable.empty()).addAll(schema.getPropertyIds());
                }
            }
        }
        return map.isEmpty() ? null : map;
    }

    private static IntObjectMap<List<PropertyAndType>> buildPropertyTypeConstraintsMap(
            SchemaCache schemaCache, EntityType entityType) {
        MutableIntObjectMap<List<PropertyAndType>> map = IntObjectMaps.mutable.empty();
        for (var constraint : schemaCache.constraints()) {
            if (constraint.enforcesPropertyType() && constraint.schema().entityType() == entityType) {
                final var typeConstraint = constraint.asPropertyTypeConstraint();
                var schema = constraint.schema();
                for (var entityToken : schema.getEntityTokenIds()) {
                    map.getIfAbsentPut(entityToken, ArrayList::new)
                            .add(new PropertyAndType(schema.getPropertyId(), typeConstraint.propertyType()));
                }
            }
        }
        return map;
    }

    private IntObjectMap<MutableIntSet> reverse(IntObjectMap<MutableIntSet> entityTokenToPropertyKeys) {
        MutableIntObjectMap<MutableIntSet> reversed = IntObjectMaps.mutable.empty();
        entityTokenToPropertyKeys.forEachKeyValue((entityToken, propertyKeys) -> propertyKeys.forEach(
                key -> reversed.getIfAbsentPut(key, IntSets.mutable::empty).add(entityToken)));
        return reversed;
    }

    public boolean hasPropertyExistenceConstraints() {
        return entityTokenToPropertyKeys != null;
    }

    public IntSet mandatoryPropertyKeys(int entityToken) {
        return !hasPropertyExistenceConstraints()
                ? IntSets.immutable.empty()
                : entityTokenToPropertyKeys.get(entityToken);
    }

    public IntSet mandatoryPropertyKeys(int[] entityTokens) {
        return buildTokenIds(entityTokens, entityTokenToPropertyKeys);
    }

    public IntSet entityTokensRelatedToPropertyKeys(int[] propertyKeys) {
        return buildTokenIds(propertyKeys, propertyKeyToEntityTokens);
    }

    private IntSet buildTokenIds(int[] tokens, IntObjectMap<MutableIntSet> mapping) {
        if (!hasPropertyExistenceConstraints()) {
            return IntSets.immutable.empty();
        }
        var result = IntSets.mutable.empty();
        for (int entityToken : tokens) {
            var mapped = mapping.get(entityToken);
            if (mapped != null) {
                result.addAll(mapped);
            }
        }
        return result;
    }

    public boolean hasPropertyTypeConstraints() {
        return typeConstraints != null;
    }

    public Iterable<PropertyAndType> propertyTypeConstraints(int entityToken) {
        if (!hasPropertyTypeConstraints()) {
            return emptyList();
        }
        return typeConstraints.getIfAbsent(entityToken, Collections::emptyList);
    }

    record PropertyAndType(int propertyKeyId, PropertyTypeSet type) {}
}

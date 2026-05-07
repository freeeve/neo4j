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
package org.neo4j.cypher.operations;

import org.neo4j.exceptions.CypherTypeException;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.EntityFilterPredicate;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.ArrayValue;
import org.neo4j.values.storable.LongArray;
import org.neo4j.values.storable.NumberArray;
import org.neo4j.values.virtual.ListValue;

public final class PropertyIndexQueries {
    private PropertyIndexQueries() {
        throw new UnsupportedOperationException("Don't instantiate!");
    }

    public static EntityFilterPredicate matchEntitySet(AnyValue value) {
        return switch (arrayValue(value)) {
            case LongArray la -> PropertyIndexQuery.entityFilter(la.asObject());
            case NumberArray na -> {
                long[] a = new long[na.intSize()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = na.value(i).longValue();
                }
                yield PropertyIndexQuery.entityFilter(a);
            }
            default ->
                throw CypherTypeException.expectedStringOrListOfStringsNotNull(
                        String.valueOf(value), value.prettyPrint(), CypherTypeValueMapper.valueType(value));
        };
    }

    private static ArrayValue arrayValue(AnyValue value) {
        return switch (value) {
            case ArrayValue a -> a;
            case ListValue l -> l.toStorableArray();
            default ->
                throw CypherTypeException.expectedCollection(
                        String.valueOf(value), value.prettyPrint(), CypherTypeValueMapper.valueType(value));
        };
    }
}

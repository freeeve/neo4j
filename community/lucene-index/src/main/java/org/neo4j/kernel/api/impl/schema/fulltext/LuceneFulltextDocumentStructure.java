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
package org.neo4j.kernel.api.impl.schema.fulltext;

import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;

public class LuceneFulltextDocumentStructure {
    public static final String FIELD_ENTITY_ID = "__neo4j__lucene__fulltext__index__internal__id__";

    private LuceneFulltextDocumentStructure() {}

    static long getEntityId(LuceneDocument from) {
        return Long.parseLong(from.get(FIELD_ENTITY_ID));
    }

    static LuceneQueryContext newCountEntityEntriesQuery(
            LuceneIndexSearcher indexSearcher, long nodeId, String[] propertyKeys, Value... propertyValues) {
        LuceneQueryContext queryContext = indexSearcher.newQueryContext();
        queryContext.addMustTerm(FIELD_ENTITY_ID, String.valueOf(nodeId));
        for (int i = 0; i < propertyKeys.length; i++) {
            String propertyKey = propertyKeys[i];
            Value value = propertyValues[i];
            // Only match on entries that doesn't contain fields we don't expect
            if (value.valueGroup() != ValueGroup.TEXT && value.valueGroup() != ValueGroup.TEXT_ARRAY) {
                queryContext.addMustNotHaveField(propertyKey);
            }
            // Why don't we match on the TEXT values that actually should be in the index?
            // 1. The analyzer used for our index can have split the property value into several terms so we cannot
            //    check that the exact property value exist in the index.
            // 2. There are some characters that analyzers will skip completely and if we have a property value with
            //    only such characters there will be no reference to the field at all, so we cannot use a wildcard query
            // either.
        }
        return queryContext;
    }
}

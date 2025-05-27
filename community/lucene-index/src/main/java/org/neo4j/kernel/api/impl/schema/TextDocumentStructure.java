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
package org.neo4j.kernel.api.impl.schema;

import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
import org.neo4j.values.storable.Value;

public class TextDocumentStructure {

    private TextDocumentStructure() {}

    public static LuceneQueryContext newSeekQuery(LuceneIndexSearcher searcher, Value... values) {
        LuceneQueryContext queryContext = searcher.newQueryContext();
        seekStrings(values, queryContext);
        return queryContext;
    }

    public static void seekStrings(Value[] values, LuceneQueryContext queryContext) {
        for (int i = 0; i < values.length; i++) {
            queryContext.addConstantMustTerm(
                    LuceneDocumentsFactory.textValueKey(i), values[i].asObject().toString());
        }
    }

    public static boolean useFieldForUniquenessVerification(String fieldName) {
        return !LuceneDocumentsFactory.ENTITY_ID_KEY.equals(fieldName)
                && LuceneDocumentsFactory.isFirstTextProperty(fieldName);
    }
}

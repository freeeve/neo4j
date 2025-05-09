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

import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
import org.neo4j.kernel.api.impl.index.lucene.LuceneStringValueEncoding;
import org.neo4j.values.storable.Value;

public class TextDocumentStructure {
    public static final String NODE_ID_KEY = "id";

    private TextDocumentStructure() {}

    public static LuceneDocument documentRepresentingProperties(long nodeId, Value... values) {
        return LuceneDocumentsFactory.CURRENT.reusableTextDocument(nodeId, values);
    }

    public static LuceneQueryContext newSeekQuery(LuceneIndexSearcher searcher, Value... values) {
        LuceneQueryContext queryContext = searcher.newQueryContext();
        seekStrings(values, queryContext);
        return queryContext;
    }

    public static void seekStrings(Value[] values, LuceneQueryContext queryContext) {
        for (int i = 0; i < values.length; i++) {
            queryContext.addConstantMustTerm(
                    LuceneStringValueEncoding.key(i), values[i].asObject().toString());
        }
    }

    public static long getNodeId(LuceneDocument from) {
        return Long.parseLong(from.get(NODE_ID_KEY));
    }

    public static boolean useFieldForUniquenessVerification(String fieldName) {
        return !TextDocumentStructure.NODE_ID_KEY.equals(fieldName)
                && LuceneStringValueEncoding.isFirstProperty(fieldName);
    }
}

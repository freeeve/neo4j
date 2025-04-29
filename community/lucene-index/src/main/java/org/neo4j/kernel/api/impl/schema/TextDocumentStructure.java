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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneReusableDocuments;
import org.neo4j.kernel.api.impl.index.lucene.LuceneStringValueEncoding;
import org.neo4j.values.storable.Value;

public class TextDocumentStructure {
    public static final String NODE_ID_KEY = "id";

    private TextDocumentStructure() {}

    public static LuceneDocument documentRepresentingProperties(long nodeId, Value... values) {
        return LuceneReusableDocuments.CURRENT.reusableTextDocument(nodeId, values);
    }

    public static MatchAllDocsQuery newScanQuery() {
        return new MatchAllDocsQuery();
    }

    public static Query newSeekQuery(Value... values) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (int i = 0; i < values.length; i++) {
            ConstantScoreQuery query = new ConstantScoreQuery(new TermQuery(new Term(
                    LuceneStringValueEncoding.key(i), values[i].asObject().toString())));
            builder.add(query, BooleanClause.Occur.MUST);
        }
        return builder.build();
    }

    public static long getNodeId(LuceneDocument from) {
        return Long.parseLong(from.get(NODE_ID_KEY));
    }

    public static boolean useFieldForUniquenessVerification(String fieldName) {
        return !TextDocumentStructure.NODE_ID_KEY.equals(fieldName)
                && LuceneStringValueEncoding.isFirstProperty(fieldName);
    }
}

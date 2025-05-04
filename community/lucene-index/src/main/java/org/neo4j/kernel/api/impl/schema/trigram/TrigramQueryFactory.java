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
package org.neo4j.kernel.api.impl.schema.trigram;

import static org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory.TRIGRAM_VALUE_KEY;

import org.apache.lucene.analysis.CharacterUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.schema.IndexQuery.IndexQueryType;
import org.neo4j.kernel.api.impl.index.LuceneQueryBuilder;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.schema.trigram.TrigramTokenStream.CodePointBuffer;

public class TrigramQueryFactory {

    // Need to filter out false positives
    public static Query exact(String value) {
        return trigramSearch(value);
    }

    // Need to filter out false positives
    public static Query stringPrefix(String prefix) {
        return trigramSearch(prefix);
    }

    // Need to filter out false positives
    public static Query stringContains(String contains) {
        return trigramSearch(contains);
    }

    // Need to filter out false positives
    public static Query stringSuffix(String suffix) {
        return trigramSearch(suffix);
    }

    public static MatchAllDocsQuery allValues() {
        return new MatchAllDocsQuery();
    }

    static boolean needStoreFilter(PropertyIndexQuery predicate) {
        return !predicate.type().equals(IndexQueryType.ALL_ENTRIES);
    }

    private static Query trigramSearch(String searchString) {
        if (searchString.isEmpty()) {
            return allValues();
        }

        var codePointBuffer = TrigramTokenStream.getCodePoints(searchString);

        if (codePointBuffer.codePointCount() < 3) {
            String searchTerm = QueryParserBase.escape(searchString);
            Term term = new Term(TRIGRAM_VALUE_KEY, "*" + searchTerm + "*");
            return new WildcardQuery(term);
        }

        LuceneQueryBuilder builder = new LuceneQueryBuilder();
        // Don't generate more clauses than what is allowed by IndexSearcher.
        // Default value for IndexSearcher.getMaxClauseCount() is 1024 which is assumed to be enough to not generate too
        // many false positives. And those false positives will be filtered out later as usual.
        for (int i = 0; i < codePointBuffer.codePointCount() - 2 && i < LuceneIndexSearcher.getMaxClauseCount(); i++) {
            String term = getNgram(codePointBuffer, i, 3);

            builder.addConstantMustTerm(TRIGRAM_VALUE_KEY, term);
        }
        return builder.build();
    }

    private static String getNgram(CodePointBuffer codePointBuffer, int ngramIndex, int n) {
        char[] termCharBuffer = new char[2 * n];
        int length = CharacterUtils.toChars(codePointBuffer.codePoints(), ngramIndex, n, termCharBuffer, 0);
        return new String(termCharBuffer, 0, length);
    }
}

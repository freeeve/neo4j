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
package org.neo4j.kernel.api.impl.index;

import static org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory.TRIGRAM_VALUE_KEY;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharacterUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.neo4j.kernel.api.impl.schema.TextDocumentStructure;
import org.neo4j.kernel.api.impl.schema.trigram.TrigramTokenStream;
import org.neo4j.values.storable.Value;

public class LuceneQueryBuilder {
    private final BooleanQuery.Builder builder = new BooleanQuery.Builder();

    public void addMustTerm(String field, String text) {
        builder.add(new TermQuery(new Term(field, text)), BooleanClause.Occur.MUST);
    }

    public void addMustNotHaveField(String field) {
        builder.add(new ConstantScoreQuery(new WildcardQuery(new Term(field, "*"))), BooleanClause.Occur.MUST_NOT);
    }

    public void addShouldQueryText(String query, String[] fields, Analyzer analyzer) throws ParseException {
        builder.add(parseFulltextQuery(query, fields, analyzer), BooleanClause.Occur.SHOULD);
    }

    public void addTrigram(String value) {
        builder.add(trigramSearch(value), BooleanClause.Occur.MUST);
    }

    public void addConstantMustTerm(String field, String text) {
        var termQuery = new ConstantScoreQuery(new TermQuery(new Term(field, text)));
        builder.add(termQuery, BooleanClause.Occur.MUST);
    }

    public Query build() {
        return builder.build();
    }

    private Query parseFulltextQuery(String query, String[] propertyNames, Analyzer analyzer) throws ParseException {
        MultiFieldQueryParser multiFieldQueryParser = new MultiFieldQueryParser(propertyNames, analyzer);
        multiFieldQueryParser.setAllowLeadingWildcard(true);
        return multiFieldQueryParser.parse(query);
    }

    private static Query trigramSearch(String searchString) {
        if (searchString.isEmpty()) {
            return new MatchAllDocsQuery();
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

    private static String getNgram(TrigramTokenStream.CodePointBuffer codePointBuffer, int ngramIndex, int n) {
        char[] termCharBuffer = new char[2 * n];
        int length = CharacterUtils.toChars(codePointBuffer.codePoints(), ngramIndex, n, termCharBuffer, 0);
        return new String(termCharBuffer, 0, length);
    }

    public void addMustSeek(Value... propertyValues) {
        builder.add(TextDocumentStructure.newSeekQuery(propertyValues), BooleanClause.Occur.MUST);
    }
}

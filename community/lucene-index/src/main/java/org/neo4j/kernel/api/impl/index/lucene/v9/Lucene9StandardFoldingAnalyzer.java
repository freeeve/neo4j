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
package org.neo4j.kernel.api.impl.index.lucene.v9;

import static org.neo4j.shaded.lucene9.analysis.en.EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

import org.neo4j.shaded.lucene9.analysis.Analyzer;
import org.neo4j.shaded.lucene9.analysis.StopwordAnalyzerBase;
import org.neo4j.shaded.lucene9.analysis.TokenStream;
import org.neo4j.shaded.lucene9.analysis.core.LowerCaseFilter;
import org.neo4j.shaded.lucene9.analysis.core.StopFilter;
import org.neo4j.shaded.lucene9.analysis.miscellaneous.ASCIIFoldingFilter;
import org.neo4j.shaded.lucene9.analysis.standard.StandardTokenizer;

public class Lucene9StandardFoldingAnalyzer extends StopwordAnalyzerBase {
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

    public Lucene9StandardFoldingAnalyzer() {
        super(ENGLISH_STOP_WORDS_SET);
    }

    @Override
    protected Analyzer.TokenStreamComponents createComponents(String fieldName) {
        StandardTokenizer src = new StandardTokenizer();
        src.setMaxTokenLength(DEFAULT_MAX_TOKEN_LENGTH);
        TokenStream tok = new ASCIIFoldingFilter(src);
        tok = new LowerCaseFilter(tok);
        tok = new StopFilter(tok, stopwords);
        return new Analyzer.TokenStreamComponents(src, tok);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

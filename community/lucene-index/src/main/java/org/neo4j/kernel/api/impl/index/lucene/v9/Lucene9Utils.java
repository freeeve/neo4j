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

import org.neo4j.shaded.lucene9.analysis.Analyzer;
import org.neo4j.shaded.lucene9.analysis.core.StopAnalyzer;
import org.neo4j.shaded.lucene9.analysis.en.EnglishAnalyzer;
import org.neo4j.shaded.lucene9.analysis.standard.StandardAnalyzer;

public class Lucene9Utils {

    static Analyzer loadAnalyzer(org.apache.lucene.analysis.Analyzer analyzer) {
        try {
            String analyzerClass = analyzer.getClass().getCanonicalName();

            return switch (analyzerClass) {
                case "org.neo4j.kernel.api.impl.schema.fulltext.analyzer.StandardFoldingAnalyzer" ->
                    new Lucene9StandardFoldingAnalyzer();
                // stop analyzer does not have no arg constructor
                case "org.apache.lucene.analysis.core.StopAnalyzer" ->
                    new StopAnalyzer(EnglishAnalyzer.getDefaultStopSet());
                // different usages of standard analyzer
                case "org.apache.lucene.analysis.standard.StandardAnalyzer" ->
                    ((org.apache.lucene.analysis.standard.StandardAnalyzer) analyzer)
                                    .getStopwordSet()
                                    .isEmpty()
                            ? new StandardAnalyzer()
                            : new StandardAnalyzer(EnglishAnalyzer.getDefaultStopSet());
                // specific case sensitive analyzer
                case "org.neo4j.kernel.api.impl.schema.fulltext.CaseSensitiveAnalyzer" ->
                    (Analyzer) Class.forName("org.neo4j.kernel.api.impl.schema.fulltext.v9.CaseSensitiveAnalyzer")
                            .getConstructor()
                            .newInstance();
                default ->
                    (Analyzer) Class.forName(analyzerClass.replace("org.apache.lucene", "org.neo4j.shaded.lucene9"))
                            .getConstructor()
                            .newInstance();
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

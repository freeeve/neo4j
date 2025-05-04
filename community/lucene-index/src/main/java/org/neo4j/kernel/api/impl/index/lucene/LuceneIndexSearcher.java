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
package org.neo4j.kernel.api.impl.index.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.kernel.api.impl.index.collector.DocValuesCollector;
import org.neo4j.kernel.api.impl.index.collector.ValuesIterator;
import org.neo4j.kernel.api.impl.schema.fulltext.FulltextResultCollector;
import org.neo4j.kernel.api.impl.schema.fulltext.PreparedSearch;
import org.neo4j.kernel.api.index.IndexProgressor;

public interface LuceneIndexSearcher extends Closeable {
    static int getMaxClauseCount() {
        return IndexSearcher.getMaxClauseCount();
    }

    IndexReader getIndexReader();

    LuceneDocument doc(int docId) throws IOException;

    IndexProgressor searchDocValues(Query query, String field, DocValuesCollector.EntityConsumer entityConsumer)
            throws IOException;

    IndexProgressor searchDocValues(Query query, String field, IndexProgressor.EntityValueClient client)
            throws IOException;

    ValuesIterator searchVectors(Query query, IndexQueryConstraints constraints) throws IOException;

    TopDocs searchTopN(Query query, int n) throws IOException;

    int count(Query query) throws IOException;

    Query rewrite(Query query) throws IOException;

    LuceneStatsCollector newStatsCollector(List<PreparedSearch> searches);

    void statsCachingSearch(Query query, FulltextResultCollector collector, LuceneStatsCollector statsCollector)
            throws IOException;
}

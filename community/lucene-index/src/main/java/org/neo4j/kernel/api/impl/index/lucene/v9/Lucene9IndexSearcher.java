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

import static org.neo4j.kernel.api.impl.index.collector.ScoredEntityIterator.mergeIterators;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.kernel.api.impl.index.collector.DocValuesCollector;
import org.neo4j.kernel.api.impl.index.collector.ValuesIterator;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneStatsCollector;
import org.neo4j.kernel.api.impl.index.partition.Neo4jIndexSearcher;
import org.neo4j.kernel.api.impl.schema.fulltext.FulltextResultCollector;
import org.neo4j.kernel.api.impl.schema.fulltext.PreparedSearch;
import org.neo4j.kernel.api.impl.schema.vector.VectorResultCollector;
import org.neo4j.kernel.api.index.IndexProgressor;

public class Lucene9IndexSearcher implements LuceneIndexSearcher {
    final IndexSearcher indexSearcher;
    private final ReferenceManager<IndexSearcher> referenceManager;

    public Lucene9IndexSearcher(ReferenceManager<IndexSearcher> referenceManager) throws IOException {
        this.referenceManager = referenceManager;
        this.indexSearcher = referenceManager.acquire();

        this.indexSearcher.setQueryCache(null); // Disable query cache
    }

    public Lucene9IndexSearcher(IndexSearcher indexSearcher) {
        this.referenceManager = null;
        this.indexSearcher = indexSearcher;
    }

    @Override
    public IndexReader getIndexReader() {
        return indexSearcher.getIndexReader();
    }

    @Override
    public LuceneDocument doc(int docId) throws IOException {
        return new Lucene9Document(indexSearcher.storedFields().document(docId));
    }

    @Override
    public IndexProgressor searchDocValues(Query query, String field, DocValuesCollector.EntityConsumer entityConsumer)
            throws IOException {
        return indexSearcher.search(
                query, new DocValuesCollectorManager(c -> c.getIndexProgressor(field, entityConsumer)));
    }

    @Override
    public IndexProgressor searchDocValues(Query query, String field, IndexProgressor.EntityValueClient client)
            throws IOException {
        return indexSearcher.search(query, new DocValuesCollectorManager(c -> c.getIndexProgressor(field, client)));
    }

    @Override
    public ValuesIterator searchVectors(Query query, IndexQueryConstraints constraints) throws IOException {
        return indexSearcher.search(query, new VectorValuesCollectorManager(constraints));
    }

    @Override
    public TopDocs searchTopN(Query query, int n) throws IOException {
        return indexSearcher.search(query, n);
    }

    @Override
    public int count(Query query) throws IOException {
        return indexSearcher.count(query);
    }

    @Override
    public Query rewrite(Query query) throws IOException {
        return indexSearcher.rewrite(query);
    }

    @Override
    public LuceneStatsCollector newStatsCollector(List<PreparedSearch> searches) {
        return new Lucene9StatsCollector(searches);
    }

    @Override
    public void close() throws IOException {
        if (referenceManager != null) {
            referenceManager.release(indexSearcher);
        }
    }

    @Override
    public void statsCachingSearch(Query query, FulltextResultCollector collector, LuceneStatsCollector statsCollector)
            throws IOException {
        // Weights are bonded with the top IndexReaderContext of the index searcher that they are created for.
        // That's why we have to create a new StatsCachingIndexSearcher, and a new weight, for every index partition.
        // However, the important thing is that we re-use the statsCollector.
        StatsCachingIndexSearcher statsCachingIndexSearcher =
                new StatsCachingIndexSearcher(indexSearcher, (Lucene9StatsCollector) statsCollector);
        Weight weight = statsCachingIndexSearcher.createWeight(query, collector.scoreMode(), 1);

        ((Neo4jIndexSearcher) indexSearcher).search(weight, collector);
    }

    /**
     * An index searcher implementation delegates to the given {@link Lucene9StatsCollector} for computing its term and collection statistics.
     * This makes it possible for this index searcher to create weights and scorers that are calibrated to the aggregate statistics of multiple indexes.
     * Aggregating the statistics is useful when a full-text index spans multiple partitions, or when transaction state needs to be taken into account as well.
     * Without the aggregate statistics, the scores computed from each search in the individual partitions, will not be comparable.
     */
    private static class StatsCachingIndexSearcher extends IndexSearcher {
        private final Lucene9StatsCollector collector;

        StatsCachingIndexSearcher(IndexSearcher searcher, Lucene9StatsCollector collector) {
            super(searcher.getTopReaderContext(), searcher.getExecutor());
            this.collector = collector;
        }

        @Override
        public TermStatistics termStatistics(Term term, int docFreq, long totalTermFreq) {
            return collector.termStatistics(term);
        }

        @Override
        public CollectionStatistics collectionStatistics(String field) {
            return collector.collectionStatistics(field);
        }
    }

    private static class DocValuesCollectorManager implements CollectorManager<DocValuesCollector, IndexProgressor> {
        private final Function<DocValuesCollector, IndexProgressor> progressFactory;

        public DocValuesCollectorManager(Function<DocValuesCollector, IndexProgressor> progressFactory) {
            this.progressFactory = progressFactory;
        }

        @Override
        public DocValuesCollector newCollector() {
            return new DocValuesCollector();
        }

        @Override
        public IndexProgressor reduce(Collection<DocValuesCollector> collectors) {
            List<IndexProgressor> list =
                    collectors.stream().map(progressFactory).toList();
            return new IndexProgressor.ConcatenatingIndexProgressor(list);
        }
    }

    private static class VectorValuesCollectorManager
            implements CollectorManager<VectorResultCollector, ValuesIterator> {

        private final IndexQueryConstraints constraints;

        private VectorValuesCollectorManager(IndexQueryConstraints constraints) {
            this.constraints = constraints;
        }

        @Override
        public VectorResultCollector newCollector() {
            return new VectorResultCollector(constraints);
        }

        @Override
        public ValuesIterator reduce(Collection<VectorResultCollector> collectors) {
            return mergeIterators(
                    collectors.stream().map(VectorResultCollector::iterator).toList());
        }
    }
}

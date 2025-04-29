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

import static org.neo4j.kernel.api.impl.index.collector.ScoredEntityIterator.mergeIterators;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.kernel.api.impl.index.collector.DocValuesCollector;
import org.neo4j.kernel.api.impl.index.collector.ValuesIterator;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9Document;
import org.neo4j.kernel.api.impl.index.partition.Neo4jIndexSearcher;
import org.neo4j.kernel.api.impl.schema.fulltext.FulltextResultCollector;
import org.neo4j.kernel.api.impl.schema.vector.VectorResultCollector;
import org.neo4j.kernel.api.index.IndexProgressor;

public class LuceneIndexSearcher implements Closeable {
    private final IndexSearcher indexSearcher;
    private final ReferenceManager<IndexSearcher> referenceManager;

    public LuceneIndexSearcher(ReferenceManager<IndexSearcher> referenceManager) throws IOException {
        this.referenceManager = referenceManager;
        this.indexSearcher = referenceManager.acquire();

        this.indexSearcher.setQueryCache(null); // Disable query cache
    }

    public LuceneIndexSearcher(IndexSearcher indexSearcher) {
        this.referenceManager = null;
        this.indexSearcher = indexSearcher;
    }

    public static int getMaxClauseCount() {
        return IndexSearcher.getMaxClauseCount();
    }

    public IndexReader getIndexReader() {
        return indexSearcher.getIndexReader();
    }

    public LuceneDocument doc(int docId) throws IOException {
        return new Lucene9Document(indexSearcher.storedFields().document(docId));
    }

    public IndexProgressor searchDocValues(Query query, String field, DocValuesCollector.EntityConsumer entityConsumer)
            throws IOException {
        return indexSearcher.search(
                query, new DocValuesCollectorManager(c -> c.getIndexProgressor(field, entityConsumer)));
    }

    public IndexProgressor searchDocValues(Query query, String field, IndexProgressor.EntityValueClient client)
            throws IOException {
        return indexSearcher.search(query, new DocValuesCollectorManager(c -> c.getIndexProgressor(field, client)));
    }

    public ValuesIterator searchVectors(Query query, IndexQueryConstraints constraints) throws IOException {
        return indexSearcher.search(query, new VectorValuesCollectorManager(constraints));
    }

    public void search(Weight weight, FulltextResultCollector collector) throws IOException {
        ((Neo4jIndexSearcher) indexSearcher).search(weight, collector);
    }

    public int count(Query query) throws IOException {
        return indexSearcher.count(query);
    }

    public Query rewrite(Query query) throws IOException {
        return indexSearcher.rewrite(query);
    }

    public IndexReaderContext getTopReaderContext() {
        return indexSearcher.getTopReaderContext();
    }

    public Executor getExecutor() {
        return indexSearcher.getExecutor();
    }

    public TermStatistics termStatistics(Term term, int i, long l) throws IOException {
        return indexSearcher.termStatistics(term, i, l);
    }

    public CollectionStatistics collectionStatistics(String field) throws IOException {
        return indexSearcher.collectionStatistics(field);
    }

    @Override
    public void close() throws IOException {
        if (referenceManager != null) {
            referenceManager.release(indexSearcher);
        }
    }

    public TermStates buildTermStates(Term term, boolean needsStats) throws IOException {
        return TermStates.build(indexSearcher, term, needsStats);
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

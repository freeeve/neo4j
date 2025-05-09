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
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.TopDocs;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.kernel.api.impl.index.collector.ValuesIterator;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LucenePartitionedSearch;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
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

    Lucene9IndexSearcher(IndexSearcher indexSearcher) {
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
    public IndexProgressor searchDocValues(LuceneQueryContext queryContext, String field, EntityConsumer entityConsumer)
            throws IOException {
        return indexSearcher.search(
                toInternal(queryContext),
                new DocValuesCollectorManager(c -> c.getIndexProgressor(field, entityConsumer)));
    }

    @Override
    public IndexProgressor searchDocValues(
            LuceneQueryContext queryContext, String field, IndexProgressor.EntityValueClient client)
            throws IOException {
        return indexSearcher.search(
                toInternal(queryContext), new DocValuesCollectorManager(c -> c.getIndexProgressor(field, client)));
    }

    @Override
    public ValuesIterator searchVectors(LuceneQueryContext queryContext, IndexQueryConstraints constraints)
            throws IOException {
        return indexSearcher.search(toInternal(queryContext), new VectorValuesCollectorManager(constraints));
    }

    @Override
    public TopDocs searchTopN(LuceneQueryContext queryContext, int n) throws IOException {
        return indexSearcher.search(toInternal(queryContext), n);
    }

    @Override
    public int count(LuceneQueryContext queryContext) throws IOException {
        return indexSearcher.count(toInternal(queryContext));
    }

    @Override
    public LuceneQueryContext newQueryContext() {
        return new Lucene9QueryContext();
    }

    @Override
    public LuceneQueryContext rewrite(LuceneQueryContext queryContext) throws IOException {
        Query originalQuery = toInternal(queryContext);
        Query rewrite = indexSearcher.rewrite(originalQuery);
        if (originalQuery == rewrite) {
            return queryContext;
        }
        return Lucene9QueryContext.wrap(rewrite);
    }

    @Override
    public void close() throws IOException {
        if (referenceManager != null) {
            referenceManager.release(indexSearcher);
        }
    }

    @Override
    public LucenePartitionedSearch newPartitionedSearcher(int size) {
        return new Lucene9PartitionedSearch(size);
    }

    private static Query toInternal(LuceneQueryContext queryContext) {
        return ((Lucene9QueryContext) queryContext).build();
    }

    private static class DocValuesCollectorManager
            implements CollectorManager<Lucene9DocValuesCollector, IndexProgressor> {
        private final Function<Lucene9DocValuesCollector, IndexProgressor> progressFactory;

        public DocValuesCollectorManager(Function<Lucene9DocValuesCollector, IndexProgressor> progressFactory) {
            this.progressFactory = progressFactory;
        }

        @Override
        public Lucene9DocValuesCollector newCollector() {
            return new Lucene9DocValuesCollector();
        }

        @Override
        public IndexProgressor reduce(Collection<Lucene9DocValuesCollector> collectors) {
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

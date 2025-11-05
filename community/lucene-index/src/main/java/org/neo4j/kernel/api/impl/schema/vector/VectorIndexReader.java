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
package org.neo4j.kernel.api.impl.schema.vector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.neo4j.internal.helpers.collection.BoundedIterable;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.NearestNeighborsPredicate;
import org.neo4j.internal.kernel.api.QueryContext;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotApplicableKernelException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.IOUtils.AutoCloseables;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.impl.index.SearcherReference;
import org.neo4j.kernel.api.impl.index.collector.ScoredEntityIterator;
import org.neo4j.kernel.api.impl.index.collector.ValuesIterator;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
import org.neo4j.kernel.api.impl.schema.AbstractLuceneIndexReader;
import org.neo4j.kernel.api.impl.schema.LuceneQueryFactory;
import org.neo4j.kernel.api.impl.schema.LuceneScoredEntityIndexProgressor;
import org.neo4j.kernel.api.impl.schema.reader.IndexReaderCloseException;
import org.neo4j.kernel.api.index.IndexProgressor;
import org.neo4j.kernel.api.index.IndexProgressor.EntityValueClient;
import org.neo4j.kernel.api.index.IndexSampler;
import org.neo4j.kernel.impl.index.schema.IndexUsageTracking;
import org.neo4j.logging.LogProvider;
import org.neo4j.values.storable.Value;

class VectorIndexReader extends AbstractLuceneIndexReader {
    private final OptionalInt dimensions;
    private final List<SearcherReference> searchers;

    VectorIndexReader(
            IndexDescriptor descriptor,
            VectorIndexConfig vectorIndexConfig,
            VectorDocumentStructure documentStructure,
            List<SearcherReference> searchers,
            IndexUsageTracking usageTracker,
            LogProvider logProvider) {
        super(descriptor, usageTracker, new LuceneQueryFactory.VectorQueryFactory(documentStructure), logProvider);
        this.dimensions = vectorIndexConfig.dimensions();
        this.searchers = searchers;
    }

    @Override
    public long countIndexedEntities(
            long entityId, CursorContext cursorContext, int[] propertyKeyIds, Value... propertyValues) {
        // TODO VECTOR: Currently only checks entity is in the index; it does not check the value is the same.
        //              Investigate finding a method to extract out the value itself from the index
        //              LeafReader::getFloatVectorValues seems promising with something like DocValuesCollector.
        //              Otherwise, perhaps k-ANN of k=1, filter=getById, (score-1) < epsilon?

        if (searchers.isEmpty()) {
            return 0;
        }
        var count = 0L;
        final var queryContext = searchers
                .getFirst()
                .getIndexSearcher()
                .newQueryContext()
                .exactTerm(VectorDocumentStructure.ENTITY_ID_KEY, entityId);
        for (final var searcher : searchers) {
            try {
                count += searcher.getIndexSearcher().count(queryContext);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return count;
    }

    @Override
    public IndexSampler createSampler() {
        return IndexSampler.EMPTY;
    }

    @Override
    public void query(
            EntityValueClient client,
            QueryContext queryContext,
            CursorContext cursorContext,
            IndexQueryConstraints constraints,
            PropertyIndexQuery... predicates)
            throws IndexNotApplicableKernelException {
        super.query(client, queryContext, cursorContext, adjustedConstraints(constraints, predicates), predicates);
    }

    @Override
    public void validateQuery(IndexQueryConstraints constraints, PropertyIndexQuery... predicates)
            throws IndexNotApplicableKernelException {
        validatePrimaryPredicate(predicates[0]);
        validateFilteredQueryPredicates(predicates);
    }

    @Override
    public PropertyIndexQuery validateSingleQuery(IndexQueryConstraints constraints, PropertyIndexQuery... predicates)
            throws IndexNotApplicableKernelException {
        final var predicate = super.validateSingleQuery(constraints, predicates);
        if (predicate instanceof final NearestNeighborsPredicate nearestNeighbour) {
            final var queryVector = nearestNeighbour.query();
            if (dimensions.isPresent() && queryVector.length != dimensions.getAsInt()) {
                throw IndexNotApplicableKernelException.vectorIndexDimensionalityMismatch(
                        indexName(), queryVector.length, dimensions.getAsInt());
            }
        }
        return predicate;
    }

    private void validateFilteredQueryPredicates(PropertyIndexQuery... predicates)
            throws IndexNotApplicableKernelException {

        for (int i = 1; i < predicates.length; i++) {

            var predicate = predicates[i];
            if (predicate != null) {

                var type = predicate.type();
                switch (type) {
                    case EXACT, RANGE -> {
                        // Each filter predicate is independent of others;
                        // so we can support arbitrary combinations range and exact predicates
                    }
                    case null, default ->
                        throw invalidVectorQueryFilter(
                                msg -> IndexNotApplicableKernelException.indexNotApplicable(
                                        log, descriptor.getName(), msg),
                                predicate,
                                predicates);
                }
            }
        }
    }

    private IndexQueryConstraints adjustedConstraints(
            IndexQueryConstraints constraints, PropertyIndexQuery... predicates)
            throws IndexNotApplicableKernelException {
        return validateSingleQuery(constraints, predicates) instanceof final NearestNeighborsPredicate nearestNeighbour
                ? constraints.limit(Math.min(
                        nearestNeighbour.numberOfNeighbors(),
                        constraints.limit().orElse(Integer.MAX_VALUE)))
                : constraints;
    }

    @Override
    protected IndexProgressor indexProgressor(
            LuceneQueryFactory queryFactory,
            IndexQueryConstraints constraints,
            EntityValueClient client,
            PropertyIndexQuery... predicates) {
        ValuesIterator iterator;
        if (searchers.isEmpty()) {
            iterator = ValuesIterator.EMPTY;
        } else {
            iterator = searchLucene(
                    queryFactory.createQuery(
                            searchers.getFirst().getIndexSearcher(), constraints, descriptor, predicates),
                    constraints);
        }
        return new LuceneScoredEntityIndexProgressor(iterator, client, constraints);
    }

    @Override
    protected String entityIdFieldKey() {
        return VectorDocumentStructure.ENTITY_ID_KEY;
    }

    @Override
    protected boolean needStoreFilter(PropertyIndexQuery predicate) {
        // We can't do filtering of false positives after the fact because we would
        // need to know which neighbors we missed to do so. We don't know what we don't know.
        return false;
    }

    @Override
    public void close() {
        final var closeables = new AutoCloseables<>(IndexReaderCloseException::new, searchers);
        try (closeables) {
            super.close();
        }
    }

    private ValuesIterator searchLucene(LuceneQueryContext queryContext, IndexQueryConstraints constraints) {
        // TODO VECTOR: FulltextIndexReader handles transaction state in a similar way
        //              with QueryContext, CursorContext, MemoryTracker
        try {
            // TODO VECTOR: pre-rewrite query? Not sure what rewriting entails
            final var results = new ArrayList<ValuesIterator>(searchers.size());
            for (final var searcher : searchers) {
                ValuesIterator valuesIterator = searcher.getIndexSearcher().searchVectors(queryContext, constraints);
                results.add(valuesIterator);
            }
            return ScoredEntityIterator.mergeIterators(results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    BoundedIterable<Long> newAllEntriesValueReader(long fromIdInclusive, long toIdExclusive) throws IOException {
        if (searchers.isEmpty()) {
            return BoundedIterable.empty();
        }
        String field = VectorDocumentStructure.ENTITY_ID_KEY;
        LuceneQueryContext queryContext =
                searchers.getFirst().getIndexSearcher().newQueryContext().matchAll();
        final var iterables = new ArrayList<BoundedIterable<Long>>(searchers.size());
        for (final var searcher : searchers) {
            iterables.add(newAllEntriesValueReaderForPartition(
                    field, searcher.getIndexSearcher(), queryContext, fromIdInclusive, toIdExclusive));
        }
        return BoundedIterable.concat(iterables);
    }
}

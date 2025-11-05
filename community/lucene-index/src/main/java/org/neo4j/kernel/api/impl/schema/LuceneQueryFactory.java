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

import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;

public abstract class LuceneQueryFactory {

    protected LuceneQueryFactory() {}

    public abstract LuceneQueryContext createQuery(
            LuceneIndexSearcher searcher,
            IndexQueryConstraints constraints,
            IndexDescriptor descriptor,
            PropertyIndexQuery... predicates);

    public static class TextQueryFactory extends LuceneQueryFactory {
        public static final LuceneQueryFactory INSTANCE = new TextQueryFactory();

        private TextQueryFactory() {}

        @Override
        public LuceneQueryContext createQuery(
                LuceneIndexSearcher searcher,
                IndexQueryConstraints constraints,
                IndexDescriptor descriptor,
                PropertyIndexQuery... predicates) {
            var predicate = predicates[0];
            return switch (predicate.type()) {
                case ALL_ENTRIES -> searcher.newQueryContext().matchAll();
                case EXACT ->
                    TextDocumentStructure.newSeekQuery(
                            searcher, ((PropertyIndexQuery.ExactPredicate) predicate).value());
                case STRING_PREFIX -> {
                    final var spp = (PropertyIndexQuery.StringPrefixPredicate) predicate;
                    yield searcher.newQueryContext().stringPrefix(spp.prefix().stringValue());
                }
                case STRING_CONTAINS -> {
                    final var scp = (PropertyIndexQuery.StringContainsPredicate) predicate;
                    yield searcher.newQueryContext()
                            .stringContains(scp.contains().stringValue());
                }
                case STRING_SUFFIX -> {
                    final var ssp = (PropertyIndexQuery.StringSuffixPredicate) predicate;
                    yield searcher.newQueryContext().stringSuffix(ssp.suffix().stringValue());
                }
                default -> throw invalidQuery(descriptor, predicate);
            };
        }
    }

    public static class TrigramQueryFactory extends LuceneQueryFactory {
        public static final LuceneQueryFactory INSTANCE = new TrigramQueryFactory();

        private TrigramQueryFactory() {}

        @Override
        public LuceneQueryContext createQuery(
                LuceneIndexSearcher searcher,
                IndexQueryConstraints constraints,
                IndexDescriptor descriptor,
                PropertyIndexQuery... predicates) {
            var predicate = predicates[0];
            return switch (predicate.type()) {
                case ALL_ENTRIES -> searcher.newQueryContext().matchAll();
                case EXACT -> {
                    final var value = ((PropertyIndexQuery.ExactPredicate) predicate)
                            .value()
                            .asObject()
                            .toString();
                    yield searcher.newQueryContext().trigramSearch(value);
                }
                case STRING_PREFIX -> {
                    final var spp = (PropertyIndexQuery.StringPrefixPredicate) predicate;
                    yield searcher.newQueryContext().trigramSearch(spp.prefix().stringValue());
                }
                case STRING_CONTAINS -> {
                    final var scp = (PropertyIndexQuery.StringContainsPredicate) predicate;
                    yield searcher.newQueryContext()
                            .trigramSearch(scp.contains().stringValue());
                }
                case STRING_SUFFIX -> {
                    final var ssp = (PropertyIndexQuery.StringSuffixPredicate) predicate;
                    yield searcher.newQueryContext().trigramSearch(ssp.suffix().stringValue());
                }
                default -> throw invalidQuery(descriptor, predicate);
            };
        }
    }

    public static class VectorQueryFactory extends LuceneQueryFactory {
        private final VectorDocumentStructure documentStructure;

        public VectorQueryFactory(VectorDocumentStructure documentStructure) {
            this.documentStructure = documentStructure;
        }

        @Override
        public LuceneQueryContext createQuery(
                LuceneIndexSearcher searcher,
                IndexQueryConstraints constraints,
                IndexDescriptor descriptor,
                PropertyIndexQuery... predicates) {
            var predicate = predicates[0];
            return switch (predicate.type()) {
                case ALL_ENTRIES -> searcher.newQueryContext().matchAll();
                case NEAREST_NEIGHBORS -> {
                    final var nearestNeighborsPredicate = (PropertyIndexQuery.NearestNeighborsPredicate) predicate;
                    final var k = Math.min(
                            nearestNeighborsPredicate.numberOfNeighbors(),
                            constraints.limit().orElse(Integer.MAX_VALUE));
                    final var effectiveK = k + constraints.skip().orElse(0);

                    if (predicates.length > 1) {
                        yield searcher.newQueryContext()
                                .approximateNearestNeighbors(
                                        documentStructure,
                                        nearestNeighborsPredicate.query(),
                                        Math.toIntExact(effectiveK),
                                        predicates);
                    } else {
                        yield searcher.newQueryContext()
                                .approximateNearestNeighbors(
                                        documentStructure,
                                        nearestNeighborsPredicate.query(),
                                        Math.toIntExact(effectiveK));
                    }
                }
                default -> throw invalidQuery(descriptor, predicate);
            };
        }
    }

    protected IllegalArgumentException invalidQuery(IndexDescriptor descriptor, PropertyIndexQuery predicate) {
        return new IllegalArgumentException(
                "Index query not supported for %s index. Query: %s".formatted(descriptor.getIndexType(), predicate));
    }
}

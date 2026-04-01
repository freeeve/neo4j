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
package org.neo4j.kernel.impl.index.vector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.internal.kernel.api.IndexReadSession;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.EntityFilterPredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.NearestNeighborsPredicate;
import org.neo4j.internal.kernel.api.TokenRead;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.util.Preconditions;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Value;

@ImpermanentDbmsExtension
abstract class VectorSSFTestBase {
    protected static final EmbeddingHolder EMBEDDINGS;

    static {
        try {
            EMBEDDINGS = EmbeddingHolder.from("vector-test-embedding-kaggle-netflix-shows.txt");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Inject
    protected GraphDatabaseAPI db;

    private static final int EF_CONSTRUCTION = 1000;
    private static final int K_NEAREST_NEIGHBORS = 10;
    private static final String SIMILARITY_FUNCTION = "COSINE";

    static final String EMBEDDING_NAME = "abstract_embedding";
    static final Label LABEL_NODE_1 = Label.label("NodeLabel1");
    static final RelationshipType TYPE_REL_1 = RelationshipType.withName("RelLabel1");
    static final String VECTOR_INDEX_NAME = "abstract_embedding_ix";

    static Function<TokenRead, PropertyIndexQuery> allQuery(String propertyKey) {
        return tokenRead -> PropertyIndexQuery.all(tokenRead.propertyKey(propertyKey));
    }

    static Function<TokenRead, PropertyIndexQuery> existsQuery(String propertyKey) {
        return tokenRead -> PropertyIndexQuery.exists(tokenRead.propertyKey(propertyKey));
    }

    static Function<TokenRead, PropertyIndexQuery> notExistsQuery(String propertyKey) {
        return tokenRead -> PropertyIndexQuery.notExists(tokenRead.propertyKey(propertyKey));
    }

    static Function<TokenRead, PropertyIndexQuery> exactQuery(String propertyKey, Value propertyValue) {
        return tokenRead -> PropertyIndexQuery.exact(tokenRead.propertyKey(propertyKey), propertyValue);
    }

    static Function<TokenRead, PropertyIndexQuery> rangeQuery(
            String propertyKey, Value propertyFrom, boolean fromInclusive, Value propertyTo, boolean toInclusive) {
        return tokenRead -> PropertyIndexQuery.range(
                tokenRead.propertyKey(propertyKey), propertyFrom, fromInclusive, propertyTo, toInclusive);
    }

    static final Function<? super VectorSSFQueryResult, Long> EXTRACT_ENTITY_ID = result -> result.entityId;

    static final Function<? super VectorSSFQueryResult, Long> EXTRACT_ID =
            result -> result.<IntegralValue>getValue("id").longValue();

    static final Function<? super VectorSSFQueryResult, String> EXTRACT_NAME =
            result -> result.<TextValue>getValue("name").stringValue();

    static class EmbeddingHolder {
        private final float[][] embeddings;

        private EmbeddingHolder(float[][] embeddings) {
            this.embeddings = embeddings;
        }

        static EmbeddingHolder from(String resource) throws IOException {
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(
                    Preconditions.requireNonNull(
                            VectorSSFTestBase.class.getResourceAsStream(resource), "Resource not found"),
                    StandardCharsets.UTF_8))) {
                String s = in.readLine();
                List<float[]> embeddings = new ArrayList<>();
                while (s != null) {
                    String[] tokens = s.split(",");
                    float[] embedding = new float[tokens.length];
                    for (int i = 0; i < embedding.length; i++) {
                        embedding[i] = Float.parseFloat(tokens[i].trim());
                    }
                    embeddings.add(embedding);
                    s = in.readLine();
                }
                return new EmbeddingHolder(embeddings.toArray(new float[embeddings.size()][]));
            }
        }

        int dimensions() {
            return dimensionsFor(0);
        }

        int dimensionsFor(int i) {
            return embeddings[i].length;
        }

        float[] get(int i) {
            return embeddings[i];
        }

        int count() {
            return embeddings.length;
        }
    }

    protected void createNodeVectorIndex(String name, int vectorDimension, String... onProperties) {
        try (final Transaction tx = db.beginTx()) {
            var creator = tx.schema().indexFor(LABEL_NODE_1);
            for (String onProperty : onProperties) {
                creator = creator.on(onProperty);
            }
            creator = creator.withIndexType(IndexType.VECTOR.toPublicApi())
                    .withIndexConfiguration(Map.of(
                            IndexSetting.vector_Dimensions(),
                            vectorDimension,
                            IndexSetting.vector_Similarity_Function(),
                            SIMILARITY_FUNCTION,
                            IndexSetting.vector_Hnsw_Ef_Construction(),
                            EF_CONSTRUCTION))
                    .withName(name);
            creator.create();
            tx.commit();
        }
        try (final Transaction tx = db.beginTx()) {
            tx.schema().awaitIndexOnline(name, 2, TimeUnit.MINUTES);
        }
    }

    protected void createRelationshipVectorIndex(String name, int vectorDimension, String... onProperties) {
        try (final Transaction tx = db.beginTx()) {
            var creator = tx.schema().indexFor(TYPE_REL_1);
            for (String onProperty : onProperties) {
                creator = creator.on(onProperty);
            }
            creator = creator.withIndexType(IndexType.VECTOR.toPublicApi())
                    .withIndexConfiguration(Map.of(
                            IndexSetting.vector_Dimensions(),
                            vectorDimension,
                            IndexSetting.vector_Similarity_Function(),
                            SIMILARITY_FUNCTION,
                            IndexSetting.vector_Hnsw_Ef_Construction(),
                            EF_CONSTRUCTION))
                    .withName(name);
            creator.create();
            tx.commit();
        }
        try (final Transaction tx = db.beginTx()) {
            tx.schema().awaitIndexOnline(name, 2, TimeUnit.MINUTES);
        }
    }

    /**
     * Create a single node with the specified properties
     * @param properties for the created record
     */
    protected long createTestNode(Map<String, Object> properties) {
        long node;
        try (final Transaction tx = db.beginTx()) {
            node = createTestNode(tx, properties);
            tx.commit();
        }
        return node;
    }

    /**
     * Create a single node with the specified properties
     * @param properties for the created record
     */
    protected long createTestNode(Transaction tx, Map<String, Object> properties) {
        var node = tx.createNode(LABEL_NODE_1);
        for (var prop : properties.entrySet()) {
            node.setProperty(prop.getKey(), prop.getValue());
        }
        return node.getId();
    }

    /**
     * Create a single relationship with the specified properties
     * @param properties for the created record
     */
    protected long createTestRelationship(Map<String, Object> properties) {
        long rel;
        try (final Transaction tx = db.beginTx()) {
            rel = createTestRelationship(tx, properties);
            tx.commit();
        }
        return rel;
    }

    /**
     * Create a single relationship with the specified properties
     * @param properties for the created record
     */
    protected long createTestRelationship(Transaction tx, Map<String, Object> properties) {
        var relationship = tx.createNode(LABEL_NODE_1).createRelationshipTo(tx.createNode(LABEL_NODE_1), TYPE_REL_1);
        for (var prop : properties.entrySet()) {
            relationship.setProperty(prop.getKey(), prop.getValue());
        }
        return relationship.getId();
    }

    /**
     * Delete a single node with the specified properties
     */
    protected void deleteTestNode(String key, Object value) {
        try (final Transaction tx = db.beginTx()) {
            var node = tx.findNode(LABEL_NODE_1, key, value);
            node.delete();
            tx.commit();
        }
    }

    /**
     * Update a single node record with the specified properties
     * @param properties for the created record
     */
    protected void updateTestNode(String key, Object value, Map<String, Object> properties) {
        try (final Transaction tx = db.beginTx()) {
            var node = tx.findNode(LABEL_NODE_1, key, value);
            if (node != null) {
                for (var prop : properties.entrySet()) {
                    var propKey = prop.getKey();
                    var propValue = prop.getValue();
                    if (propValue != null) {
                        node.setProperty(propKey, propValue);
                    } else {
                        node.removeProperty(propKey);
                    }
                }
            }
            tx.commit();
        }
    }

    /**
     * Scan the available index descriptors for the first (only) one of name/type
     * @param ktx transaction in which we are executing
     * @param indexType of an index to search for
     * @param name of an index to search for
     * @return the first matching descriptor
     * @throws Exception if something goes wrong
     */
    private IndexDescriptor findIndexDescriptor(KernelTransaction ktx, IndexType indexType, String name)
            throws TestException {

        final IndexDescriptor index = ktx.schemaRead().indexGetForName(name);
        if (index.getId() == -1 || !index.getIndexType().equals(indexType)) {
            throw new TestException("This is not the expected index");
        }
        return index;
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryNodeIndex(
            String indexName,
            NearestNeighborsPredicate kNearestNeighboursPredicate,
            Function<TokenRead, PropertyIndexQuery>... queryFilters)
            throws Exception {
        return queryNodeIndex(
                indexName, kNearestNeighboursPredicate, PropertyIndexQuery.matchAllEntityFilter(), queryFilters);
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryNodeIndex(
            String indexName,
            NearestNeighborsPredicate kNearestNeighboursPredicate,
            EntityFilterPredicate entityFilterPredicate,
            Function<TokenRead, PropertyIndexQuery>... queryFilters)
            throws Exception {
        try (final Transaction tx = db.beginTx()) {
            final var ktx = ((InternalTransaction) tx).kernelTransaction();
            try (final var indexCursor =
                            ktx.cursors().allocateNodeValueIndexCursor(ktx.cursorContext(), ktx.memoryTracker());
                    final var propertyCursor =
                            ktx.cursors().allocatePropertyCursor(ktx.cursorContext(), ktx.memoryTracker());
                    final var nodeCursor = ktx.cursors().allocateNodeCursor(ktx.cursorContext(), ktx.memoryTracker())) {
                var read = ktx.dataRead();
                var tokenRead = ktx.tokenRead();
                var queryContext = ktx.queryContext();
                IndexReadSession session = read.indexReadSession(findIndexDescriptor(ktx, IndexType.VECTOR, indexName));
                PropertyIndexQuery[] queries = new PropertyIndexQuery[queryFilters.length + 2];
                queries[0] = kNearestNeighboursPredicate;
                queries[1] = entityFilterPredicate;
                for (int i = 0; i < queryFilters.length; i++) {
                    var queryFilter = queryFilters[i];
                    queries[i + 2] = queryFilter == null ? null : queryFilter.apply(tokenRead);
                }
                read.nodeIndexSeek(queryContext, session, indexCursor, IndexQueryConstraints.unconstrained(), queries);

                List<VectorSSFQueryResult> results = new ArrayList<>();
                while (indexCursor.next()) {
                    float score = indexCursor.score();
                    indexCursor.node(nodeCursor);
                    while (nodeCursor.next()) {
                        results.add(VectorSSFQueryResult.fromCursors(
                                score, nodeCursor, propertyCursor, tokenRead, Set.of(EMBEDDING_NAME)));
                    }
                }
                return results;
            }
        }
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryRelationshipIndex(
            String indexName,
            NearestNeighborsPredicate kNearestNeighboursPredicate,
            EntityFilterPredicate entityFilterPredicate,
            Function<TokenRead, PropertyIndexQuery>... queryFilters)
            throws Exception {
        try (final Transaction tx = db.beginTx()) {
            final var ktx = ((InternalTransaction) tx).kernelTransaction();
            try (final var indexCursor = ktx.cursors()
                            .allocateRelationshipValueIndexCursor(ktx.cursorContext(), ktx.memoryTracker());
                    final var propertyCursor =
                            ktx.cursors().allocatePropertyCursor(ktx.cursorContext(), ktx.memoryTracker());
                    final var relCursor =
                            ktx.cursors().allocateRelationshipScanCursor(ktx.cursorContext(), ktx.memoryTracker())) {
                var read = ktx.dataRead();
                var tokenRead = ktx.tokenRead();
                var queryContext = ktx.queryContext();
                IndexReadSession session = read.indexReadSession(findIndexDescriptor(ktx, IndexType.VECTOR, indexName));
                PropertyIndexQuery[] queries = new PropertyIndexQuery[queryFilters.length + 2];
                queries[0] = kNearestNeighboursPredicate;
                queries[1] = entityFilterPredicate;
                for (int i = 0; i < queryFilters.length; i++) {
                    var queryFilter = queryFilters[i];
                    queries[i + 2] = queryFilter == null ? null : queryFilter.apply(tokenRead);
                }
                read.relationshipIndexSeek(
                        queryContext, session, indexCursor, IndexQueryConstraints.unconstrained(), queries);

                List<VectorSSFQueryResult> results = new ArrayList<>();
                while (indexCursor.next()) {
                    float score = indexCursor.score();
                    read.singleRelationship(indexCursor.reference(), relCursor);
                    while (relCursor.next()) {
                        results.add(VectorSSFQueryResult.fromCursors(
                                score, relCursor, propertyCursor, tokenRead, Set.of(EMBEDDING_NAME)));
                    }
                }
                return results;
            }
        }
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryNodeIndex(Function<TokenRead, PropertyIndexQuery>... queryFilters)
            throws Exception {
        return queryNodeIndex(
                VECTOR_INDEX_NAME,
                PropertyIndexQuery.nearestNeighbors(K_NEAREST_NEIGHBORS, EMBEDDINGS.get(0)),
                PropertyIndexQuery.matchAllEntityFilter(),
                queryFilters);
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryNodeIndex(
            EntityFilterPredicate entityFilterPredicate, Function<TokenRead, PropertyIndexQuery>... queryFilters)
            throws Exception {
        return queryNodeIndex(
                VECTOR_INDEX_NAME,
                PropertyIndexQuery.nearestNeighbors(K_NEAREST_NEIGHBORS, EMBEDDINGS.get(0)),
                entityFilterPredicate,
                queryFilters);
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryNodeIndex(
            int kNearestNeighbours, Function<TokenRead, PropertyIndexQuery>... queryFilters) throws Exception {
        return queryNodeIndex(
                        VECTOR_INDEX_NAME,
                        PropertyIndexQuery.nearestNeighbors(kNearestNeighbours, EMBEDDINGS.get(0)),
                        PropertyIndexQuery.matchAllEntityFilter(),
                        queryFilters)
                .stream()
                .toList();
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryRelationshipIndex(
            EntityFilterPredicate entityFilterPredicate, Function<TokenRead, PropertyIndexQuery>... queryFilters)
            throws Exception {
        return queryRelationshipIndex(
                VECTOR_INDEX_NAME,
                PropertyIndexQuery.nearestNeighbors(K_NEAREST_NEIGHBORS, EMBEDDINGS.get(0)),
                entityFilterPredicate,
                queryFilters);
    }

    @SafeVarargs
    final List<VectorSSFQueryResult> queryRelationshipIndex(
            int kNearestNeighbours, Function<TokenRead, PropertyIndexQuery>... queryFilters) throws Exception {
        return queryRelationshipIndex(
                        VECTOR_INDEX_NAME,
                        PropertyIndexQuery.nearestNeighbors(kNearestNeighbours, EMBEDDINGS.get(0)),
                        PropertyIndexQuery.matchAllEntityFilter(),
                        queryFilters)
                .stream()
                .toList();
    }

    protected static class TestException extends Exception {
        protected TestException(String s) {
            super(s);
        }
    }
}

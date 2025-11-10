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
package org.neo4j.kernel.impl.newapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.ReferenceCursor;
import org.neo4j.internal.kernel.api.TokenPredicate;
import org.neo4j.internal.kernel.api.TokenReadSession;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.coreapi.TransactionImpl;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.values.ElementIdMapper;

@ImpermanentDbmsExtension
public class KernelReadAPIWithoutTransactionStateTest {

    @Inject
    private GraphDatabaseAPI db;

    @Test
    void allNodesScanSeesNoTxStateOnEmptyDB() {
        try (var tx = db.beginTx()) {
            tx.createNode();
            assertThat(readNodeIdsFromScan(tx)).isEmpty();
        }
    }

    @Test
    void allNodesScanSeesNoTxStateOnNonEmptyDB() {
        String elementId;
        try (var tx = db.beginTx()) {
            elementId = tx.createNode().getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode();
            assertThat(readNodeIdsFromScan(tx)).containsExactly(elementId);
        }
    }

    @Test
    void nodeLabelIndexScanSeesNoTxStateOnEmptyDB() throws KernelException {
        Label label = Label.label("name");
        try (var tx = db.beginTx()) {
            tx.createNode(label);

            assertThat(readNodeIdsFromTokenIndex(tx, label)).isEmpty();
        }
    }

    @Test
    void nodeLabelIndexScanSeesNoTxStateOnNonEmptyDB() throws KernelException {
        Label label = Label.label("name");
        String elementId;
        try (var tx = db.beginTx()) {
            elementId = tx.createNode(label).getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode(label);
            assertThat(readNodeIdsFromTokenIndex(tx, label)).containsExactly(elementId);
        }
    }

    @Test
    void nodeIndexSeekSeesNoTxStateOnEmptyDB() throws KernelException {
        Label label = Label.label("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema().indexFor(label).on(property).withName(indexName).create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        try (var tx = db.beginTx()) {
            tx.createNode(label).setProperty(property, value);

            assertThat(readNodeIdsFromPropertyIndexSeek(tx, indexName, property, value))
                    .isEmpty();
        }
    }

    @Test
    void nodeIndexSeekSeesNoTxStateOnNonEmptyDB() throws KernelException {
        Label label = Label.label("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema().indexFor(label).on(property).withName(indexName).create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        String elementId;
        try (var tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty(property, value);
            elementId = node.getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode(label).setProperty(property, value);

            assertThat(readNodeIdsFromPropertyIndexSeek(tx, indexName, property, value))
                    .containsExactly(elementId);
        }
    }

    @Test
    void nodeIndexScanSeesNoTxStateOnEmptyDB() throws KernelException {
        Label label = Label.label("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema().indexFor(label).on(property).withName(indexName).create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        try (var tx = db.beginTx()) {
            tx.createNode(label).setProperty(property, value);

            assertThat(readNodeIdsFromPropertyIndexScan(tx, indexName)).isEmpty();
        }
    }

    @Test
    void nodeIndexScanSeesNoTxStateOnNonEmptyDB() throws KernelException {
        Label label = Label.label("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema().indexFor(label).on(property).withName(indexName).create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        String elementId;
        try (var tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty(property, value);
            elementId = node.getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode(label).setProperty(property, value);

            assertThat(readNodeIdsFromPropertyIndexScan(tx, indexName)).containsExactly(elementId);
        }
    }

    private List<String> readNodeIdsFromScan(Transaction tx) {
        try (var nodeCursor = ktx(tx).cursors().allocateNodeCursor(CursorContext.NULL_CONTEXT)) {
            ktx(tx).dataRead().allNodesScan(nodeCursor, false);
            return collectNodeIds(nodeCursor);
        }
    }

    private List<String> readNodeIdsFromTokenIndex(Transaction tx, Label label) throws KernelException {
        var index = ktx(tx).schemaRead()
                .index(SchemaDescriptors.ANY_TOKEN_NODE_SCHEMA_DESCRIPTOR)
                .next();

        int labelId = ktx(tx).tokenRead().nodeLabel(label.name());
        var dataRead = ktx(tx).dataRead();
        TokenReadSession tokenReadSession = dataRead.tokenReadSession(index);
        try (var nodeCursor = ktx(tx).cursors().allocateNodeLabelIndexCursor(CursorContext.NULL_CONTEXT)) {
            dataRead.nodeLabelIndexScan(
                    tokenReadSession,
                    nodeCursor,
                    IndexQueryConstraints.unconstrained(),
                    new TokenPredicate(labelId),
                    CursorContext.NULL_CONTEXT,
                    false);
            return collectNodeIds(nodeCursor);
        }
    }

    private List<String> readNodeIdsFromPropertyIndexSeek(
            Transaction tx, String indexName, String property, String value) throws KernelException {
        var index = ktx(tx).schemaRead().indexGetForName(indexName);
        int propertyId = ktx(tx).tokenRead().propertyKey(property);
        var dataRead = ktx(tx).dataRead();
        var tokenReadSession = dataRead.indexReadSession(index);
        try (var nodeCursor = ktx(tx).cursors()
                .allocateNodeValueIndexCursor(CursorContext.NULL_CONTEXT, EmptyMemoryTracker.INSTANCE)) {
            dataRead.nodeIndexSeek(
                    ktx(tx).queryContext(),
                    tokenReadSession,
                    nodeCursor,
                    IndexQueryConstraints.unconstrained(),
                    false,
                    PropertyIndexQuery.exact(propertyId, value));
            return collectNodeIds(nodeCursor);
        }
    }

    private List<String> readNodeIdsFromPropertyIndexScan(Transaction tx, String indexName) throws KernelException {
        var index = ktx(tx).schemaRead().indexGetForName(indexName);
        var dataRead = ktx(tx).dataRead();
        var indexReadSession = dataRead.indexReadSession(index);
        try (var nodeCursor = ktx(tx).cursors()
                .allocateNodeValueIndexCursor(CursorContext.NULL_CONTEXT, EmptyMemoryTracker.INSTANCE)) {
            dataRead.nodeIndexScan(indexReadSession, nodeCursor, IndexQueryConstraints.unconstrained(), false);
            return collectNodeIds(nodeCursor);
        }
    }

    @Test
    void allRelationshipsScanSeesNoTxStateOnEmptyDB() {
        try (var tx = db.beginTx()) {
            tx.createNode().createRelationshipTo(tx.createNode(), RelationshipType.withName("name"));
            assertThat(readRelationshipIdsFromScan(tx)).isEmpty();
        }
    }

    @Test
    void allRelationshipsScanSeesNoTxStateOnNonEmptyDB() {
        String elementId;
        try (var tx = db.beginTx()) {
            elementId = tx.createNode()
                    .createRelationshipTo(tx.createNode(), RelationshipType.withName("name"))
                    .getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode().createRelationshipTo(tx.createNode(), RelationshipType.withName("name"));
            assertThat(readRelationshipIdsFromScan(tx)).containsExactly(elementId);
        }
    }

    @Test
    void relationshipTypeIndexScanSeesNoTxStateOnEmptyDB() throws KernelException {
        var relationshipType = RelationshipType.withName("name");
        try (var tx = db.beginTx()) {
            tx.createNode().createRelationshipTo(tx.createNode(), relationshipType);
            assertThat(readRelationshipIdsFromIndexScan(tx, relationshipType)).isEmpty();
        }
    }

    @Test
    void relationshipTypeIndexScanSeesNoTxStateOnNonEmptyDB() throws KernelException {
        var relationshipType = RelationshipType.withName("name");
        String elementId;
        try (var tx = db.beginTx()) {
            elementId = tx.createNode()
                    .createRelationshipTo(tx.createNode(), relationshipType)
                    .getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode().createRelationshipTo(tx.createNode(), relationshipType);
            assertThat(readRelationshipIdsFromIndexScan(tx, relationshipType)).containsExactly(elementId);
        }
    }

    @Test
    void relationshipIndexSeekSeesNoTxStateOnEmptyDB() throws KernelException {
        var relationshipType = RelationshipType.withName("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema()
                    .indexFor(relationshipType)
                    .on(property)
                    .withName(indexName)
                    .create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        try (var tx = db.beginTx()) {
            tx.createNode()
                    .createRelationshipTo(tx.createNode(), relationshipType)
                    .setProperty(property, value);
            assertThat(readRelationshipIdsFromPropertyIndexSeek(tx, indexName, property, value))
                    .isEmpty();
        }
    }

    @Test
    void relationshipIndexSeekSeesNoTxStateOnNonEmptyDB() throws KernelException {
        var relationshipType = RelationshipType.withName("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema()
                    .indexFor(relationshipType)
                    .on(property)
                    .withName(indexName)
                    .create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        String elementId;
        try (var tx = db.beginTx()) {
            var node = tx.createNode().createRelationshipTo(tx.createNode(), relationshipType);
            node.setProperty(property, value);
            elementId = node.getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode()
                    .createRelationshipTo(tx.createNode(), relationshipType)
                    .setProperty(property, value);
            assertThat(readRelationshipIdsFromPropertyIndexSeek(tx, indexName, property, value))
                    .containsExactly(elementId);
        }
    }

    private List<String> readRelationshipIdsFromPropertyIndexSeek(
            Transaction tx, String indexName, String property, String value) throws KernelException {
        var index = ktx(tx).schemaRead().indexGetForName(indexName);
        int propertyId = ktx(tx).tokenRead().propertyKey(property);
        var dataRead = ktx(tx).dataRead();
        var tokenReadSession = dataRead.indexReadSession(index);
        try (var cursor = ktx(tx).cursors()
                .allocateRelationshipValueIndexCursor(CursorContext.NULL_CONTEXT, EmptyMemoryTracker.INSTANCE)) {
            dataRead.relationshipIndexSeek(
                    ktx(tx).queryContext(),
                    tokenReadSession,
                    cursor,
                    IndexQueryConstraints.unconstrained(),
                    false,
                    PropertyIndexQuery.exact(propertyId, value));
            return collectRelationshipIds(cursor);
        }
    }

    @Test
    void relationshipIndexScanSeesNoTxStateOnEmptyDB() throws KernelException {
        var relationshipType = RelationshipType.withName("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema()
                    .indexFor(relationshipType)
                    .on(property)
                    .withName(indexName)
                    .create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        try (var tx = db.beginTx()) {
            tx.createNode()
                    .createRelationshipTo(tx.createNode(), relationshipType)
                    .setProperty(property, value);
            assertThat(readRelationshipIdsFromPropertyIndexScan(tx, indexName)).isEmpty();
        }
    }

    @Test
    void relationshipIndexScanSeesNoTxStateOnNonEmptyDB() throws KernelException {
        var relationshipType = RelationshipType.withName("name");
        String property = "property";
        String value = "value";
        String indexName = "indexName";
        try (var tx = db.beginTx()) {
            tx.schema()
                    .indexFor(relationshipType)
                    .on(property)
                    .withName(indexName)
                    .create();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(5, TimeUnit.MINUTES);
        }

        String elementId;
        try (var tx = db.beginTx()) {
            var node = tx.createNode().createRelationshipTo(tx.createNode(), relationshipType);
            node.setProperty(property, value);
            elementId = node.getElementId();
            tx.commit();
        }

        try (var tx = db.beginTx()) {
            tx.createNode()
                    .createRelationshipTo(tx.createNode(), relationshipType)
                    .setProperty(property, value);
            assertThat(readRelationshipIdsFromPropertyIndexScan(tx, indexName)).containsExactly(elementId);
        }
    }

    private List<String> readRelationshipIdsFromPropertyIndexScan(Transaction tx, String indexName)
            throws KernelException {
        var index = ktx(tx).schemaRead().indexGetForName(indexName);
        var dataRead = ktx(tx).dataRead();
        var tokenReadSession = dataRead.indexReadSession(index);
        try (var cursor = ktx(tx).cursors()
                .allocateRelationshipValueIndexCursor(CursorContext.NULL_CONTEXT, EmptyMemoryTracker.INSTANCE)) {
            dataRead.relationshipIndexScan(tokenReadSession, cursor, IndexQueryConstraints.unconstrained(), false);
            return collectRelationshipIds(cursor);
        }
    }

    private List<String> readRelationshipIdsFromIndexScan(Transaction tx, RelationshipType relationshipType)
            throws KernelException {
        var index = ktx(tx).schemaRead()
                .index(SchemaDescriptors.ANY_TOKEN_RELATIONSHIP_SCHEMA_DESCRIPTOR)
                .next();

        int typeId = ktx(tx).tokenRead().relationshipType(relationshipType.name());
        var dataRead = ktx(tx).dataRead();
        TokenReadSession tokenReadSession = dataRead.tokenReadSession(index);
        try (var relationshipScanCursor =
                ktx(tx).cursors().allocateRelationshipTypeIndexCursor(CursorContext.NULL_CONTEXT)) {
            dataRead.relationshipTypeIndexScan(
                    tokenReadSession,
                    relationshipScanCursor,
                    IndexQueryConstraints.unconstrained(),
                    new TokenPredicate(typeId),
                    CursorContext.NULL_CONTEXT,
                    false);
            return collectRelationshipIds(relationshipScanCursor);
        }
    }

    private List<String> readRelationshipIdsFromScan(Transaction tx) {
        try (var nodeCursor = ktx(tx).cursors().allocateRelationshipScanCursor(CursorContext.NULL_CONTEXT)) {
            ktx(tx).dataRead().allRelationshipsScan(nodeCursor, false);
            return collectRelationshipIds(nodeCursor);
        }
    }

    private List<String> collectNodeIds(ReferenceCursor cursor) {
        List<String> ids = new ArrayList<>();
        var idMapper = db.getDependencyResolver().resolveDependency(ElementIdMapper.class);
        while (cursor.next()) {
            ids.add(idMapper.nodeElementId(cursor.reference()));
        }
        return ids;
    }

    private List<String> collectRelationshipIds(ReferenceCursor cursor) {
        List<String> ids = new ArrayList<>();
        var idMapper = db.getDependencyResolver().resolveDependency(ElementIdMapper.class);
        while (cursor.next()) {
            ids.add(idMapper.relationshipElementId(cursor.reference()));
        }
        return ids;
    }

    private static KernelTransaction ktx(Transaction tx) {
        return ((TransactionImpl) tx).kernelTransaction();
    }
}

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

import static org.neo4j.collection.PrimitiveLongCollections.mergeToSet;

import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.neo4j.internal.kernel.api.KernelReadTracer;
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.NodeLabelIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.internal.schema.IndexOrder;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.api.txstate.TransactionState;
import org.neo4j.storageengine.api.Degrees;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.RelationshipSelection;

public class DefaultNodeLabelIndexCursor extends DefaultEntityTokenIndexCursor<DefaultNodeLabelIndexCursor>
        implements NodeLabelIndexCursor {
    private final InternalCursorFactory internalCursors;
    private DefaultNodeCursor internalNodeCursor;

    DefaultNodeLabelIndexCursor(
            CursorPool<DefaultNodeLabelIndexCursor> pool,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState) {
        super(pool, applyAccessModeToTxState);
        this.internalCursors = internalCursors;
    }

    @Override
    protected boolean innerNext() {
        return indexNext();
    }

    @Override
    protected LongIterator createAddedInTxState(TransactionState txState, int token, IndexOrder order) {
        return sortTxState(txState.nodesWithLabelChanged(token).getAdded().freeze(), order);
    }

    @Override
    protected LongSet createDeletedInTxState(TransactionState txState, int token) {
        return mergeToSet(
                        txState.addedAndRemovedNodes().getRemoved(),
                        txState.nodesWithLabelChanged(token).getRemoved())
                .asUnmodifiable();
    }

    @Override
    protected void traceScan(KernelReadTracer tracer, int token) {
        tracer.onLabelScan(token);
    }

    @Override
    protected void traceNext(KernelReadTracer tracer, long entity) {
        tracer.onNode(entity);
    }

    @Override
    protected boolean allowedToSeeAllEntitiesWithToken(int token) {
        return accessModeProvider.getAccessMode().allowsTraverseAllNodesWithLabel(token);
    }

    @Override
    protected boolean allowedToSeeEntity(long entityReference) {
        if (accessModeProvider.getAccessMode().allowsTraverseAllLabels()) {
            return true;
        }
        ensureInternalCursor();
        read.singleNode(entityReference, internalNodeCursor);
        return internalNodeCursor.next();
    }

    @Override
    public void node(NodeCursor cursor) {
        read.singleNode(entityReference(), cursor);
    }

    @Override
    public long nodeReference() {
        return entityReference();
    }

    // NodeCursor interface
    @Override
    public TokenSet labels() {
        checkReadFromStore();
        return internalNodeCursor.labels();
    }

    @Override
    public TokenSet labelsIgnoringTxStateSetRemove() {
        checkReadFromStore();
        return internalNodeCursor.labelsIgnoringTxStateSetRemove();
    }

    @Override
    public boolean hasLabel(int label) {
        checkReadFromStore();
        return internalNodeCursor.hasLabel(label);
    }

    @Override
    public boolean hasLabel() {
        checkReadFromStore();
        return internalNodeCursor.hasLabel();
    }

    @Override
    public void relationships(RelationshipTraversalCursor relationships, RelationshipSelection selection) {
        checkReadFromStore();
        internalNodeCursor.relationships(relationships, selection);
    }

    @Override
    public boolean supportsFastRelationshipsTo() {
        checkReadFromStore();
        return internalNodeCursor.supportsFastRelationshipsTo();
    }

    @Override
    public void relationshipsTo(
            RelationshipTraversalCursor relationships, RelationshipSelection selection, long neighbourNodeReference) {
        checkReadFromStore();
        internalNodeCursor.relationshipsTo(relationships, selection, neighbourNodeReference);
    }

    @Override
    public long relationshipsReference() {
        checkReadFromStore();
        return internalNodeCursor.relationshipsReference();
    }

    @Override
    public boolean supportsFastDegreeLookup() {
        checkReadFromStore();
        return internalNodeCursor.supportsFastDegreeLookup();
    }

    @Override
    public int[] relationshipTypes() {
        checkReadFromStore();
        return internalNodeCursor.relationshipTypes();
    }

    @Override
    public Degrees degrees(RelationshipSelection selection) {
        checkReadFromStore();
        return internalNodeCursor.degrees(selection);
    }

    @Override
    public long degree(RelationshipSelection selection) {
        checkReadFromStore();
        return internalNodeCursor.degree(selection);
    }

    @Override
    public long degreeWithMax(long maxDegree, RelationshipSelection selection) {
        checkReadFromStore();
        return internalNodeCursor.degreeWithMax(maxDegree, selection);
    }

    @Override
    public void properties(PropertyCursor cursor, PropertySelection selection) {
        checkReadFromStore();
        internalNodeCursor.properties(cursor, selection);
    }

    @Override
    public Reference propertiesReference() {
        checkReadFromStore();
        return internalNodeCursor.propertiesReference();
    }

    @Override
    public boolean readFromStore() {
        ensureInternalCursor();
        if (entity != StatementConstants.NO_SUCH_NODE && internalNodeCursor.nodeReference() == entity) {
            // A security check, or a previous call to this method for this node already seems to have loaded
            // this node
            return true;
        }

        internalNodeCursor.single(entity, read, txStateHolder, accessModeProvider);
        return internalNodeCursor.next();
    }

    private void checkReadFromStore() {
        if (internalNodeCursor.nodeReference() != entity) {
            throw new IllegalStateException("Node hasn't been read from store");
        }
    }

    @Override
    public float score() {
        return Float.NaN;
    }

    @Override
    public String toString() {
        if (isClosed()) {
            return "NodeLabelIndexCursor[closed state]";
        } else {
            return "NodeLabelIndexCursor[node=" + entityReference() + ", label= " + tokenId + "]";
        }
    }

    @Override
    public void release() {
        if (internalNodeCursor != null) {
            internalNodeCursor.close();
            internalNodeCursor.release();
            internalNodeCursor = null;
        }
    }

    private void ensureInternalCursor() {
        if (internalNodeCursor == null) {
            internalNodeCursor = internalCursors.allocateNodeCursor();
        }
    }
}

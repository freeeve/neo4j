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
package org.neo4j.internal.indexcommand;

import static org.neo4j.common.EntityType.RELATIONSHIP;
import static org.neo4j.storageengine.api.TokenIndexEntryUpdate.tokenChange;
import static org.neo4j.token.api.TokenConstants.ANY_RELATIONSHIP_TYPE;

import org.eclipse.collections.api.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.neo4j.exceptions.KernelException;
import org.neo4j.exceptions.UnspecifiedKernelException;
import org.neo4j.graphdb.Direction;
import org.neo4j.internal.kernel.api.exceptions.schema.ConstraintValidationException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.RelationshipSelection;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.storageengine.api.txstate.RelationshipModifications;
import org.neo4j.storageengine.api.txstate.TxStateVisitor;
import org.neo4j.storageengine.util.SingleDegree;

public final class NodeBasedTransactionToIndexUpdateVisitor extends TransactionToIndexUpdateVisitor {

    private final StorageRelationshipScanCursor relationshipCursor;
    private final IndexDescriptor relationshipTypeIndex;

    public NodeBasedTransactionToIndexUpdateVisitor(
            TxStateVisitor next,
            IndexUpdatesState indexUpdatesState,
            StorageReader storageReader,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker) {
        super(next, indexUpdatesState, storageReader, cursorContext, storeCursors, memoryTracker);
        this.relationshipCursor =
                storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker);
        this.relationshipTypeIndex = getTokenIndex(storageReader, RELATIONSHIP);
    }

    @Override
    public void visitRelationshipModifications(RelationshipModifications modifications)
            throws ConstraintValidationException {
        super.visitRelationshipModifications(modifications);

        if (relationshipTypeIndex == null) {
            return;
        }

        collectPerNodeTypeModifications(modifications).forEachKeyValue(this::processChange);
    }

    private void processChange(NodeTypeKey nodeType, int delta) {
        if (shouldAddToIndex(nodeType.nodeId, nodeType.type, delta)) {
            indexUpdatesState.addTokenUpdate(
                    tokenChange(nodeType.nodeId, relationshipTypeIndex, NO_TOKENS, new int[] {nodeType.type}));
        } else if (shouldRemoveFromIndex(nodeType.nodeId, nodeType.type, delta)) {
            indexUpdatesState.addTokenUpdate(
                    tokenChange(nodeType.nodeId, relationshipTypeIndex, new int[] {nodeType.type}, NO_TOKENS));
        }
    }

    private boolean shouldAddToIndex(long nodeId, int relType, int delta) {
        if (delta <= 0) {
            return false;
        }
        nodeCursor.single(nodeId);
        if (!nodeCursor.next()) {
            return true;
        }
        SingleDegree degree = new SingleDegree(1);
        nodeCursor.degrees(RelationshipSelection.selection(relType, Direction.OUTGOING), degree);
        return degree.getTotal() == 0;
    }

    private boolean shouldRemoveFromIndex(long nodeId, int relType, int delta) {
        if (delta >= 0) {
            return false;
        }
        nodeCursor.single(nodeId);
        if (!nodeCursor.next()) {
            return false;
        }
        int removedCount = Math.abs(delta);
        SingleDegree degree = new SingleDegree(removedCount + 1);
        nodeCursor.degrees(RelationshipSelection.selection(relType, Direction.OUTGOING), degree);

        return degree.getTotal() <= removedCount;
    }

    private MutableObjectIntMap<NodeTypeKey> collectPerNodeTypeModifications(RelationshipModifications modifications) {
        MutableObjectIntMap<NodeTypeKey> changes = ObjectIntMaps.mutable.empty();

        modifications
                .creations()
                .forEach((id, type, startNode, en, ap, cp, rp) ->
                        changes.updateValue(new NodeTypeKey(startNode, type), 0, i -> i + 1));
        modifications
                .deletions()
                .forEach((id, type, startNode, en, ap, cp, rp) ->
                        changes.updateValue(new NodeTypeKey(startNode, findTypeToRemove(id, type)), 0, i -> i - 1));
        return changes;
    }

    @Override
    public void close() throws KernelException {
        // exception from super.close is the main exception and should not be wrapped as IOUtils.closeAllUnchecked does
        AutoCloseable superClose = super::close;
        //noinspection EmptyTryBlock
        try (relationshipCursor;
                superClose) {
            // superClose is closed first, any exception from the relationshipCursor will be added as suppressed to the
            // exception from superClose
        } catch (KernelException ke) {
            throw ke;
        } catch (Exception e) {
            throw UnspecifiedKernelException.unknownError(e);
        }
    }

    private int findTypeToRemove(long id, int type) {
        if (type == ANY_RELATIONSHIP_TYPE) {
            relationshipCursor.single(id);
            if (!relationshipCursor.next()) {
                throw new IllegalStateException(
                        "Relationship being deleted should exist along with its nodes. Relationship[" + id + "]");
            }
            return relationshipCursor.type();
        }
        return type;
    }

    private record NodeTypeKey(long nodeId, int type) {}
}

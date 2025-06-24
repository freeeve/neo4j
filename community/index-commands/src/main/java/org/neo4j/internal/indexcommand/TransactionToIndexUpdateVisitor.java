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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;
import static org.neo4j.common.EntityType.NODE;
import static org.neo4j.common.EntityType.RELATIONSHIP;
import static org.neo4j.internal.schema.SchemaDescriptors.forAnyEntityTokens;
import static org.neo4j.storageengine.api.TokenIndexEntryUpdate.tokenChange;
import static org.neo4j.storageengine.api.ValueIndexEntryUpdate.add;
import static org.neo4j.storageengine.api.ValueIndexEntryUpdate.change;
import static org.neo4j.storageengine.api.ValueIndexEntryUpdate.remove;
import static org.neo4j.token.api.TokenConstants.ANY_RELATIONSHIP_TYPE;

import java.util.Arrays;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.neo4j.common.EntityType;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.Direction;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.exceptions.schema.ConstraintValidationException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.IOUtils;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.RelationshipSelection;
import org.neo4j.storageengine.api.RelationshipVisitorWithProperties;
import org.neo4j.storageengine.api.StorageNodeCursor;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.storageengine.api.txstate.EntityChange;
import org.neo4j.storageengine.api.txstate.RelationshipModifications;
import org.neo4j.storageengine.api.txstate.TxStateVisitor;
import org.neo4j.storageengine.util.SingleDegree;
import org.neo4j.values.storable.ValueTuple;

public class TransactionToIndexUpdateVisitor extends TxStateVisitor.Delegator {
    private static final int[] NO_TOKENS = EMPTY_INT_ARRAY;

    private final IndexUpdatesState indexUpdatesState;
    private final StorageNodeCursor nodeCursor;
    private final StorageRelationshipScanCursor relationshipCursor;
    private final IndexDescriptor labelIndex;
    private final IndexDescriptor relationshipTypeIndex;
    private final RelationshipVisitorWithProperties<RuntimeException> tokenIndexAdditions;
    private final RelationshipVisitorWithProperties<RuntimeException> tokenIndexRemovals;

    public TransactionToIndexUpdateVisitor(
            TxStateVisitor next,
            IndexUpdatesState indexUpdatesState,
            StorageReader storageReader,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker,
            StorageEngineIndexingBehaviour storageEngineIndexingBehaviour) {
        super(next);
        this.indexUpdatesState = indexUpdatesState;
        boolean useNodeIds = storageEngineIndexingBehaviour.useNodeIdsInRelationshipTokenIndex();
        this.tokenIndexAdditions = useNodeIds
                ? new NodeBasedRelationshipTokenIndexAdditions()
                : new RelationshipBasedRelationshipTokenIndexAdditions();
        this.tokenIndexRemovals = useNodeIds
                ? new NodeBasedRelationshipTokenIndexRemovals()
                : new RelationshipBasedRelationshipTokenIndexRemovals();

        this.nodeCursor = storageReader.allocateNodeCursor(cursorContext, storeCursors, memoryTracker);
        this.relationshipCursor =
                storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker);

        this.labelIndex = getTokenIndex(storageReader, NODE);
        this.relationshipTypeIndex = getTokenIndex(storageReader, RELATIONSHIP);
    }

    private static IndexDescriptor getTokenIndex(StorageReader storageReader, EntityType entityType) {
        return Iterators.firstOrNull(storageReader.indexGetForSchema(forAnyEntityTokens(entityType)));
    }

    @Override
    public void visitDeletedNode(long id) {
        super.visitDeletedNode(id);

        if (labelIndex == null) {
            return;
        }
        nodeCursor.single(id);

        if (!nodeCursor.next()) {
            return;
        }
        int[] labelsBefore = nodeCursor.labels();
        if (labelsBefore.length == 0) {
            return;
        }
        if (labelsBefore.length > 1) {
            Arrays.sort(labelsBefore);
        }
        indexUpdatesState.addTokenUpdate(tokenChange(id, labelIndex, labelsBefore, NO_TOKENS));
    }

    @Override
    public void visitNodeLabelChanges(long id, IntSet added, IntSet removed) throws ConstraintValidationException {
        super.visitNodeLabelChanges(id, added, removed);

        if (labelIndex == null) {
            return;
        }
        indexUpdatesState.addTokenUpdate(tokenChange(id, labelIndex, removed.toSortedArray(), added.toSortedArray()));
    }

    @Override
    public void visitRelationshipModifications(RelationshipModifications modifications)
            throws ConstraintValidationException {
        super.visitRelationshipModifications(modifications);

        if (relationshipTypeIndex == null) {
            return;
        }

        modifications.creations().forEach(tokenIndexAdditions);
        modifications.deletions().forEach(tokenIndexRemovals);
    }

    @Override
    public void visitValueIndexUpdate(
            IndexDescriptor descriptor, long entityId, ValueTuple values, EntityChange entityChange) {
        super.visitValueIndexUpdate(descriptor, entityId, values, entityChange);
        var key = new IndexUpdatesState.IndexEntityPair(descriptor.getId(), entityId);
        ValueIndexEntryUpdate existingUpdate = indexUpdatesState.getValueUpdate(key);

        var update = getValueUpdate(descriptor, entityId, values, entityChange, existingUpdate);
        indexUpdatesState.putValueUpdate(key, update);
    }

    private ValueIndexEntryUpdate getValueUpdate(
            IndexDescriptor descriptor,
            long entityId,
            ValueTuple values,
            EntityChange entityChange,
            ValueIndexEntryUpdate existingUpdate) {
        if (entityChange == EntityChange.ADDED) {
            return existingUpdate == null
                    ? add(entityId, descriptor, values.getValues())
                    : change(entityId, descriptor, existingUpdate.values(), values.getValues());
        }
        return existingUpdate == null
                ? remove(entityId, descriptor, values.getValues())
                : change(entityId, descriptor, values.getValues(), existingUpdate.values());
    }

    @Override
    public void close() throws KernelException {
        super.close();
        IOUtils.closeAllUnchecked(indexUpdatesState, nodeCursor, relationshipCursor);
    }

    private class RelationshipBasedRelationshipTokenIndexAdditions
            implements RelationshipVisitorWithProperties<RuntimeException> {

        @Override
        public void visit(
                long id,
                int type,
                long startNode,
                long endNode,
                Iterable<StorageProperty> addedProperties,
                Iterable<StorageProperty> changedProperties,
                IntIterable removedProperties)
                throws RuntimeException {
            indexUpdatesState.addTokenUpdate(tokenChange(id, relationshipTypeIndex, NO_TOKENS, new int[] {type}));
        }
    }

    private class RelationshipBasedRelationshipTokenIndexRemovals
            implements RelationshipVisitorWithProperties<RuntimeException> {
        @Override
        public void visit(
                long id,
                int type,
                long startNode,
                long endNode,
                Iterable<StorageProperty> noProperties,
                Iterable<StorageProperty> changedProperties,
                IntIterable removedProperties)
                throws RuntimeException {
            indexUpdatesState.addTokenUpdate(
                    tokenChange(id, relationshipTypeIndex, new int[] {findTypeToRemove(id, type)}, NO_TOKENS));
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

    private class NodeBasedRelationshipTokenIndexAdditions
            implements RelationshipVisitorWithProperties<RuntimeException> {
        @Override
        public void visit(
                long id,
                int type,
                long startNode,
                long endNode,
                Iterable<StorageProperty> addedProperties,
                Iterable<StorageProperty> changedProperties,
                IntIterable removedProperties)
                throws RuntimeException {
            nodeCursor.single(startNode);
            if (!nodeCursor.next()) {
                indexUpdatesState.addTokenUpdate(
                        tokenChange(startNode, relationshipTypeIndex, NO_TOKENS, new int[] {type}));
                return;
            }

            // TODO mvcc correctly handle cases when there are multiple removals and additions in the same batch
            // if this is a first relationship of this type we generate token addition
            SingleDegree degree = new SingleDegree(1);
            nodeCursor.degrees(RelationshipSelection.selection(type, Direction.OUTGOING), degree);
            if (degree.getTotal() == 0) {
                indexUpdatesState.addTokenUpdate(
                        tokenChange(startNode, relationshipTypeIndex, NO_TOKENS, new int[] {type}));
            }
        }
    }

    private class NodeBasedRelationshipTokenIndexRemovals
            implements RelationshipVisitorWithProperties<RuntimeException> {
        @Override
        public void visit(
                long id,
                int type,
                long startNode,
                long endNode,
                Iterable<StorageProperty> noProperties,
                Iterable<StorageProperty> changedProperties,
                IntIterable removedProperties)
                throws RuntimeException {
            int typeToRemove = findTypeToRemove(id, type);
            nodeCursor.single(startNode);
            if (!nodeCursor.next()) {
                return;
            }
            // TODO mvcc correctly handle cases when there are multiple removals and additions in the same batch
            // if this is a last relationship of this type we generate token removal
            SingleDegree degree = new SingleDegree(2);
            nodeCursor.degrees(RelationshipSelection.selection(typeToRemove, Direction.OUTGOING), degree);

            if (degree.getTotal() == 1) {
                indexUpdatesState.addTokenUpdate(
                        tokenChange(startNode, relationshipTypeIndex, new int[] {typeToRemove}, NO_TOKENS));
            }
        }
    }
}

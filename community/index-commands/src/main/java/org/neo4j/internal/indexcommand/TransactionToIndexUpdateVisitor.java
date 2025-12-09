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
import static org.neo4j.internal.schema.SchemaDescriptors.forAnyEntityTokens;
import static org.neo4j.storageengine.api.EagerValueIndexEntryUpdate.add;
import static org.neo4j.storageengine.api.EagerValueIndexEntryUpdate.change;
import static org.neo4j.storageengine.api.EagerValueIndexEntryUpdate.remove;
import static org.neo4j.storageengine.api.TokenIndexEntryUpdate.tokenChange;

import java.util.Arrays;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.neo4j.common.EntityType;
import org.neo4j.exceptions.KernelException;
import org.neo4j.exceptions.UnspecifiedKernelException;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.exceptions.schema.ConstraintValidationException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.EagerValueIndexEntryUpdate;
import org.neo4j.storageengine.api.StorageNodeCursor;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.storageengine.api.txstate.EntityChange;
import org.neo4j.storageengine.api.txstate.TxStateVisitor;
import org.neo4j.values.storable.ValueTuple;

public abstract sealed class TransactionToIndexUpdateVisitor extends TxStateVisitor.Delegator
        permits NodeBasedTransactionToIndexUpdateVisitor, RelationshipBasedTransactionToIndexUpdateVisitor {
    protected static final int[] NO_TOKENS = EMPTY_INT_ARRAY;

    protected final IndexUpdatesState indexUpdatesState;
    protected final StorageNodeCursor nodeCursor;
    private final IndexDescriptor labelIndex;

    public TransactionToIndexUpdateVisitor(
            TxStateVisitor next,
            IndexUpdatesState indexUpdatesState,
            StorageReader storageReader,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker) {
        super(next);
        this.indexUpdatesState = indexUpdatesState;
        this.nodeCursor = storageReader.allocateNodeCursor(cursorContext, storeCursors, memoryTracker);
        this.labelIndex = getTokenIndex(storageReader, NODE);
    }

    protected static IndexDescriptor getTokenIndex(StorageReader storageReader, EntityType entityType) {
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
    public void visitValueIndexUpdate(
            IndexDescriptor descriptor, long entityId, ValueTuple values, EntityChange entityChange) {
        super.visitValueIndexUpdate(descriptor, entityId, values, entityChange);
        var key = new IndexUpdatesState.IndexEntityPair(descriptor.getId(), entityId);
        EagerValueIndexEntryUpdate existingUpdate = indexUpdatesState.getValueUpdate(key);

        var update = getValueUpdate(descriptor, entityId, values, entityChange, existingUpdate);
        indexUpdatesState.putValueUpdate(key, update);
    }

    private EagerValueIndexEntryUpdate getValueUpdate(
            IndexDescriptor descriptor,
            long entityId,
            ValueTuple values,
            EntityChange entityChange,
            EagerValueIndexEntryUpdate existingUpdate) {
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
        AutoCloseable superClose = super::close;
        //noinspection EmptyTryBlock
        try (nodeCursor;
                indexUpdatesState;
                superClose) {
            // superClose is closed first, any exception from other entries will be added as suppressed to the
            // exception from superClose
        } catch (KernelException ke) {
            throw ke;
        } catch (Exception e) {
            throw UnspecifiedKernelException.unknownError(e);
        }
    }
}

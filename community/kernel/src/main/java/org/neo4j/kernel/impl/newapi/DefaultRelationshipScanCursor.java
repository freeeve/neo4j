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

import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.impl.iterator.ImmutableEmptyLongIterator;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.RelationshipScanCursor;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.storageengine.api.AllRelationshipsScan;
import org.neo4j.storageengine.api.LongReference;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;

public class DefaultRelationshipScanCursor extends DefaultRelationshipCursor<DefaultRelationshipScanCursor>
        implements RelationshipScanCursor {
    private final StorageRelationshipScanCursor storeCursor;

    private long single;
    private boolean isSingle;

    protected DefaultRelationshipScanCursor(
            CursorPool<DefaultRelationshipScanCursor> pool,
            StorageRelationshipScanCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState) {
        super(storeCursor, pool, applyAccessModeToTxState, internalCursors);
        this.storeCursor = storeCursor;
    }

    void scan(
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider,
            boolean includeChangesFromThisTransaction) {
        init(
                read,
                includeChangesFromThisTransaction ? txStateHolder : TxStateHolder.EMPTY_TX_STATE,
                accessModeProvider);
        storeCursor.scan(includeChangesFromThisTransaction);
        this.single = LongReference.NULL;
        this.isSingle = false;
    }

    boolean scanBatch(
            Read read,
            AllRelationshipsScan scan,
            long sizeHint,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        init(read, txStateHolder, accessModeProvider);
        this.single = LongReference.NULL;
        this.isSingle = false;
        prepareChanges(ImmutableEmptyLongIterator.INSTANCE, false);
        return storeCursor.scanBatch(scan, sizeHint);
    }

    void single(long reference, Read read, TxStateHolder txStateHolder, AccessModeProvider accessModeProvider) {
        init(read, txStateHolder, accessModeProvider);
        storeCursor.single(reference);
        this.single = reference;
        this.isSingle = true;
    }

    void single(
            long reference,
            long sourceNodeReference,
            int type,
            long targetNodeReference,
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        init(read, txStateHolder, accessModeProvider);
        storeCursor.single(reference, sourceNodeReference, type, targetNodeReference);
        this.single = reference;
        this.isSingle = true;
    }

    @Override
    protected boolean filterOutTxStateRelationship() {
        return false;
    }

    @Override
    protected boolean allowedToTraverseEndNodes() {
        if (allowAllNodes) {
            return true;
        }

        var nodeCursor = getSecurityNodeCursor();

        boolean useTxStateRef = applyAccessModeToTxState && currentAddedInTx != LongReference.NULL;
        long sourceNode = useTxStateRef ? txStateSourceNodeReference : storeCursor.sourceNodeReference();
        read.singleNode(sourceNode, nodeCursor);
        if (nodeCursor.next()) {
            long targetNode = useTxStateRef ? txStateTargetNodeReference : storeCursor.targetNodeReference();
            read.singleNode(targetNode, nodeCursor);
            return nodeCursor.next();
        }

        return false;
    }

    @Override
    protected LongIterator collectAddedTxStateSnapshot(TxStateHolder stateHolder) {
        if (isSingle) {
            return stateHolder.txState().relationshipIsAddedInThisBatch(single)
                    ? LongHashSet.newSetWith(single).longIterator()
                    : ImmutableEmptyLongIterator.INSTANCE;
        }
        return stateHolder
                .txState()
                .addedAndRemovedRelationships()
                .getAdded()
                .freeze()
                .longIterator();
    }

    @Override
    public String toString() {
        if (isClosed()) {
            return "RelationshipScanCursor[closed state]";
        }
        return "RelationshipScanCursor[id=" + storeCursor.entityReference() + ", open state with: single="
                + single + ", "
                + storeCursor + "]";
    }
}

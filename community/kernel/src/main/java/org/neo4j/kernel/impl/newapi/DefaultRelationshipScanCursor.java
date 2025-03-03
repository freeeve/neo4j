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
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.impl.iterator.ImmutableEmptyLongIterator;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.RelationshipScanCursor;
import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.internal.kernel.api.security.ReadSecurityPropertyProvider;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.storageengine.api.AllRelationshipsScan;
import org.neo4j.storageengine.api.LongReference;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;

public class DefaultRelationshipScanCursor extends DefaultRelationshipCursor<DefaultRelationshipScanCursor>
        implements RelationshipScanCursor {
    private final StorageRelationshipScanCursor storeCursor;
    private final InternalCursorFactory internalCursors;
    private final boolean applyAccessModeToTxState;
    private long single;
    private boolean isSingle;
    private LongIterator addedRelationships;
    private DefaultNodeCursor securityNodeCursor;
    private StoragePropertyCursor securityPropertyCursor;
    private AccessMode accessMode;
    private boolean allowAllRelationships;
    private boolean allowAllNodes;

    protected DefaultRelationshipScanCursor(
            CursorPool<DefaultRelationshipScanCursor> pool,
            StorageRelationshipScanCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState) {
        super(storeCursor, pool);
        this.storeCursor = storeCursor;
        this.internalCursors = internalCursors;
        this.applyAccessModeToTxState = applyAccessModeToTxState;
    }

    void scan(Read read, TxStateHolder txStateHolder, AccessModeProvider accessModeProvider) {
        storeCursor.scan();
        this.single = LongReference.NULL;
        this.isSingle = false;
        init(read, txStateHolder, accessModeProvider);
        initAccessMode(accessModeProvider);
        this.addedRelationships = ImmutableEmptyLongIterator.INSTANCE;
    }

    boolean scanBatch(
            Read read,
            AllRelationshipsScan scan,
            long sizeHint,
            LongIterator addedRelationships,
            boolean hasChanges,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        this.read = read;
        this.txStateHolder = txStateHolder;
        this.accessModeProvider = accessModeProvider;
        initAccessMode(accessModeProvider);
        this.single = LongReference.NULL;
        this.isSingle = false;
        this.currentAddedInTx = LongReference.NULL;
        this.addedRelationships = addedRelationships;
        this.hasChanges = hasChanges;
        this.checkHasChanges = false;
        boolean scanBatch = storeCursor.scanBatch(scan, sizeHint);
        return addedRelationships.hasNext() || scanBatch;
    }

    void single(long reference, Read read, TxStateHolder txStateHolder, AccessModeProvider accessModeProvider) {
        storeCursor.single(reference);
        this.single = reference;
        this.isSingle = true;
        init(read, txStateHolder, accessModeProvider);
        initAccessMode(accessModeProvider);
        this.accessMode = accessModeProvider.getAccessMode();
        this.addedRelationships = ImmutableEmptyLongIterator.INSTANCE;
    }

    void single(
            long reference,
            long sourceNodeReference,
            int type,
            long targetNodeReference,
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        storeCursor.single(reference, sourceNodeReference, type, targetNodeReference);
        this.single = reference;
        this.isSingle = true;
        init(read, txStateHolder, accessModeProvider);
        initAccessMode(accessModeProvider);
        this.accessMode = accessModeProvider.getAccessMode();
        this.addedRelationships = ImmutableEmptyLongIterator.INSTANCE;
    }

    /**
     * It is expected that accessMode is stable while cursor is in use, so we cache accessMode and couple shortcuts
     */
    private void initAccessMode(AccessModeProvider accessModeProvider) {
        this.accessMode = accessModeProvider.getAccessMode();
        this.allowAllRelationships = accessMode.allowsTraverseAllRelTypes();
        this.allowAllNodes = accessMode.allowsTraverseAllLabels();
    }

    @Override
    public boolean next() {
        // Check tx state
        boolean hasChanges = hasChanges();

        if (hasChanges) {
            while (addedRelationships.hasNext()) {
                long next = addedRelationships.next();
                txStateHolder.txState().relationshipVisit(next, relationshipTxStateDataVisitor);

                if (!applyAccessModeToTxState || allowed()) {
                    trace();
                    return true;
                }
            }
            currentAddedInTx = LongReference.NULL;
        }

        while (storeCursor.next()) {
            if (!deletedInThisBatch(hasChanges) && allowed()) {
                trace();
                return true;
            }
        }
        return false;
    }

    private boolean deletedInThisBatch(boolean hasChanges) {
        return hasChanges && txStateHolder.txState().relationshipIsDeletedInThisBatch(storeCursor.entityReference());
    }

    private void trace() {
        if (tracer != null) {
            tracer.onRelationship(relationshipReference());
        }
    }

    protected boolean allowed() {
        assert accessMode == accessModeProvider.getAccessMode() : "access mode changed while cursor is in use";
        return allowedTraverseRelationship() && allowedToTraverseEndNodes();
    }

    private boolean allowedTraverseRelationship() {
        if (allowAllRelationships) {
            return true;
        }
        return accessMode.allowsTraverseRelationship(
                type(), properties -> getSecurityPropertyProvider(storeCursor, properties));
    }

    private ReadSecurityPropertyProvider getSecurityPropertyProvider(
            StorageRelationshipScanCursor storageCursor, IntSet securityProperties) {
        var propertyCursor = getSecurityPropertyCursor();
        var propertySelection = PropertySelection.selection(securityProperties.toArray());
        storageCursor.properties(propertyCursor, propertySelection);
        Iterable<StorageProperty> txStateChangedProperties = applyAccessModeToTxState
                ? txStateHolder
                        .txState()
                        .getNodeState(this.relationshipReference())
                        .addedAndChangedProperties()
                : null;

        return new ReadSecurityPropertyProvider.LazyReadSecurityPropertyProvider(
                propertyCursor, txStateChangedProperties, propertySelection);
    }

    private boolean allowedToTraverseEndNodes() {
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

    private StoragePropertyCursor getSecurityPropertyCursor() {
        if (securityPropertyCursor == null) {
            securityPropertyCursor = internalCursors.allocateStoragePropertyCursor();
        }
        return securityPropertyCursor;
    }

    private DefaultNodeCursor getSecurityNodeCursor() {
        if (securityNodeCursor == null) {
            securityNodeCursor = internalCursors.allocateNodeCursor();
        }
        return securityNodeCursor;
    }

    @Override
    public void closeInternal() {
        if (!isClosed()) {
            read = null;
            txStateHolder = null;
            accessModeProvider = null;
            accessMode = null;
            storeCursor.reset();
            if (securityNodeCursor != null) {
                securityNodeCursor.close();
            }
            if (securityPropertyCursor != null) {
                securityPropertyCursor.close();
            }
        }
        super.closeInternal();
    }

    @Override
    public boolean isClosed() {
        return read == null;
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

    @Override
    protected void collectAddedTxStateSnapshot() {
        if (isSingle) {
            addedRelationships = txStateHolder.txState().relationshipIsAddedInThisBatch(single)
                    ? LongHashSet.newSetWith(single).longIterator()
                    : ImmutableEmptyLongIterator.INSTANCE;
        } else {
            addedRelationships = txStateHolder
                    .txState()
                    .addedAndRemovedRelationships()
                    .getAdded()
                    .longIterator();
        }
    }

    @Override
    public void release() {
        storeCursor.close();
        if (securityNodeCursor != null) {
            securityNodeCursor.close();
            securityNodeCursor.release();
            securityNodeCursor = null;
        }
        if (securityPropertyCursor != null) {
            securityPropertyCursor.close();
            securityPropertyCursor = null;
        }
    }
}

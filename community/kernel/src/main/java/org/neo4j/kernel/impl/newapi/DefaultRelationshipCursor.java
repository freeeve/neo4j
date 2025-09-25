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
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.RelationshipCursor;
import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.storageengine.api.LongReference;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.StorageRelationshipCursor;

abstract class DefaultRelationshipCursor<SELF extends DefaultRelationshipCursor<?>> extends TraceableCursorImpl<SELF>
        implements RelationshipCursor {

    private final StorageRelationshipCursor storeCursor;
    protected final boolean applyAccessModeToTxState;
    private final InternalCursorFactory internalCursors;

    protected Read read;
    private TxStateHolder txStateHolder;

    private boolean hasChanges;
    private boolean checkHasChanges;
    private LongIterator addedRelationships;

    long currentAddedInTx = LongReference.NULL;
    private int txStateTypeId;
    protected long txStateSourceNodeReference;
    protected long txStateTargetNodeReference;

    private AccessModeProvider accessModeProvider;
    private AccessMode accessMode;
    private boolean allowAllRelationships;
    protected boolean allowAllNodes;
    private AccessControlDataProvider accessControlDataProvider;
    private DefaultNodeCursor securityNodeCursor;

    DefaultRelationshipCursor(
            StorageRelationshipCursor storeCursor,
            CursorPool<SELF> pool,
            boolean applyAccessModeToTxState,
            InternalCursorFactory internalCursors) {
        super(pool);
        this.storeCursor = storeCursor;
        this.applyAccessModeToTxState = applyAccessModeToTxState;
        this.internalCursors = internalCursors;
    }

    protected void init(Read read, TxStateHolder txStateHolder, AccessModeProvider accessModeProvider) {
        this.currentAddedInTx = LongReference.NULL;
        this.read = read;
        this.txStateHolder = txStateHolder;
        this.checkHasChanges = true;
        initAccessMode(accessModeProvider);
    }

    /**
     * It is expected that accessMode is stable while cursor is in use, so we cache accessMode and couple shortcuts
     */
    private void initAccessMode(AccessModeProvider accessModeProvider) {
        this.accessModeProvider = accessModeProvider;
        this.accessMode = accessModeProvider.getAccessMode();
        this.allowAllRelationships = accessMode.allowsTraverseAllRelTypes();
        this.allowAllNodes = accessMode.allowsTraverseAllLabels();
    }

    @Override
    public boolean next() {
        boolean hasChanges = hasChanges();

        if (hasChanges) {
            while (addedRelationships.hasNext()) {
                long next = addedRelationships.next();
                collectTxStateData(next);

                if (!applyAccessModeToTxState || allowed()) {
                    if (filterOutTxStateRelationship()) {
                        continue;
                    }

                    trace();
                    return true;
                }
            }
            currentAddedInTx = LongReference.NULL;
        }

        while (storeCursor.next()) {
            if (!deletedInThisBatch(hasChanges) && allowed()) {
                maybeTraceStoreHit();
                return true;
            }
        }
        return false;
    }

    protected void maybeTraceStoreHit() {
        trace();
    }

    private void collectTxStateData(long next) {
        txStateHolder
                .txState()
                .relationshipVisit(next, (relationshipId, typeId, sourceNodeReference, targetNodeReference) -> {
                    currentAddedInTx = relationshipId;
                    txStateTypeId = typeId;
                    txStateSourceNodeReference = sourceNodeReference;
                    txStateTargetNodeReference = targetNodeReference;
                });
    }

    protected abstract boolean filterOutTxStateRelationship();

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
        return accessMode.allowsTraverseRelationship(type(), getSelectedPropertiesProvider());
    }

    protected abstract boolean allowedToTraverseEndNodes();

    private AccessControlDataProvider getSelectedPropertiesProvider() {
        if (accessControlDataProvider == null) {
            accessControlDataProvider = new AccessControlDataProvider(
                    () -> storeCursor::properties,
                    internalCursors,
                    applyAccessModeToTxState,
                    this::txStateProperties,
                    () -> read);
        }
        return accessControlDataProvider;
    }

    private Iterable<StorageProperty> txStateProperties() {
        return txStateHolder
                .txState()
                .getRelationshipState(this.relationshipReference())
                .addedAndChangedProperties();
    }

    protected DefaultNodeCursor getSecurityNodeCursor() {
        if (securityNodeCursor == null) {
            securityNodeCursor = internalCursors.allocateNodeCursor();
        }
        return securityNodeCursor;
    }

    @Override
    public long relationshipReference() {
        return currentAddedInTx != LongReference.NULL ? currentAddedInTx : storeCursor.entityReference();
    }

    @Override
    public int type() {
        return currentAddedInTx != LongReference.NULL ? txStateTypeId : storeCursor.type();
    }

    @Override
    public void source(NodeCursor cursor) {
        read.singleNode(sourceNodeReference(), cursor);
    }

    @Override
    public void target(NodeCursor cursor) {
        read.singleNode(targetNodeReference(), cursor);
    }

    @Override
    public void properties(PropertyCursor cursor, PropertySelection selection) {
        ((DefaultPropertyCursor) cursor)
                .initRelationship(
                        this.type(),
                        selection,
                        read,
                        txStateHolder,
                        accessModeProvider,
                        storeCursor,
                        this.currentRelationshipIsAddedInTx(),
                        this.relationshipReference());
    }

    @Override
    public long sourceNodeReference() {
        return currentAddedInTx != LongReference.NULL ? txStateSourceNodeReference : storeCursor.sourceNodeReference();
    }

    @Override
    public long targetNodeReference() {
        return currentAddedInTx != LongReference.NULL ? txStateTargetNodeReference : storeCursor.targetNodeReference();
    }

    @Override
    public Reference propertiesReference() {
        return currentAddedInTx != LongReference.NULL
                ? LongReference.NULL_REFERENCE
                : storeCursor.propertiesReference();
    }

    protected boolean currentRelationshipIsAddedInTx() {
        return currentAddedInTx != LongReference.NULL;
    }

    /**
     * RelationshipCursor should only see changes that are there from the beginning
     * otherwise it will not be stable.
     */
    protected boolean hasChanges() {
        if (checkHasChanges) {
            hasChanges = txStateHolder.hasTxStateWithChanges();
            if (hasChanges) {
                addedRelationships = collectAddedTxStateSnapshot(txStateHolder);
            }
            checkHasChanges = false;
        }

        return hasChanges;
    }

    protected abstract LongIterator collectAddedTxStateSnapshot(TxStateHolder stateHolder);

    protected void prepareChanges(LongIterator addedRelationships, boolean hasChanges) {
        this.addedRelationships = addedRelationships;
        this.hasChanges = hasChanges;
        this.checkHasChanges = false;
    }

    @Override
    public void release() {
        storeCursor.close();
        if (securityNodeCursor != null) {
            securityNodeCursor.close();
            securityNodeCursor.release();
            securityNodeCursor = null;
        }
        if (accessControlDataProvider != null) {
            accessControlDataProvider.close();
            accessControlDataProvider = null;
        }
    }

    @Override
    public boolean isClosed() {
        return read == null;
    }

    @Override
    public void closeInternal() {
        if (!isClosed()) {
            read = null;
            txStateHolder = null;
            accessModeProvider = null;
            storeCursor.reset();
            if (securityNodeCursor != null) {
                securityNodeCursor.close();
            }
            if (accessControlDataProvider != null) {
                accessControlDataProvider.close();
            }
        }
        super.closeInternal();
    }
}

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

import org.eclipse.collections.api.set.primitive.LongSet;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.kernel.api.KernelReadTracer;
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.NodeValueIndexCursor;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.api.txstate.TransactionState;
import org.neo4j.storageengine.api.LongReference;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.StorageProperty;

public class DefaultNodeValueIndexCursor extends DefaultEntityValueIndexCursor<DefaultNodeValueIndexCursor>
        implements NodeValueIndexCursor {
    private final InternalCursorFactory internalCursors;
    private DefaultNodeCursor securityNodeCursor;
    private TraceablePropertyCursor propertyCursor;
    private int[] propertyIds;
    private AccessControlDataProvider accessControlDataProvider;

    DefaultNodeValueIndexCursor(
            CursorPool<DefaultNodeValueIndexCursor> pool,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState) {
        super(pool, applyAccessModeToTxState);
        this.internalCursors = internalCursors;
    }

    /**
     * Check that the user is allowed to access all nodes and properties given by the index descriptor.
     * <p>
     * If the current user is allowed to traverse all labels used in this index and read the properties
     * of all nodes in the index, we can skip checking on every node we get back.
     */
    @Override
    protected boolean canAccessAllDescribedEntities(IndexDescriptor descriptor) {
        propertyIds = descriptor.schema().getPropertyIds();
        int[] labelIds = descriptor.schema().getEntityTokenIds();
        return accessMode.allowsTraverseAndReadAllMatchingNodeProperties(labelIds, propertyIds);
    }

    @Override
    void traceOnEntity(KernelReadTracer tracer, long entity) {
        tracer.onNode(entity);
    }

    @Override
    String implementationName() {
        return "NodeValueIndexCursor";
    }

    @Override
    protected final boolean canAccessEntityAndProperties(long reference) {
        ensureSecurityNodeCursor();
        read.singleNode(reference, securityNodeCursor);
        if (!securityNodeCursor.next()) {
            // This node is not visible to this security context
            return false;
        }

        assert accessMode == accessModeProvider.getAccessMode() : "access mode changed while cursor is in use";
        return accessMode.allowsReadNodeProperties(
                () -> AccessControlDataProvider.nodeLabels(securityNodeCursor, applyAccessModeToTxState),
                propertyIds,
                this::getAccessControlDataProvider);
    }

    /**
     * AccessControlDataProvider when used as SelectedPropertiesProvider will return properties for the node pointed by {@link #securityNodeCursor}
     * This indirection is here for the sake of ultimate laziness
     */
    private AccessControlDataProvider getAccessControlDataProvider() {
        if (accessControlDataProvider == null) {
            accessControlDataProvider = new AccessControlDataProvider(
                    () -> (propertyCursor, selection) -> {
                        if (securityNodeCursor.storeCursor.entityReference() != LongReference.NULL) {
                            propertyCursor.initNodeProperties(securityNodeCursor.storeCursor, selection);
                        }
                    },
                    internalCursors,
                    applyAccessModeToTxState,
                    this::txStateProperties,
                    () -> read);
        }
        return accessControlDataProvider;
    }

    private Iterable<StorageProperty> txStateProperties() {
        if (txStateHolder.hasTxStateWithChanges()) {
            return txStateHolder
                    .txState()
                    .getNodeState(securityNodeCursor.nodeReference())
                    .addedProperties();
        }
        return Iterables.empty();
    }

    @Override
    public void node(NodeCursor cursor) {
        read.singleNode(entityReference(), cursor);
    }

    @Override
    public long nodeReference() {
        return entityReference();
    }

    @Override
    protected LongSet removed(TransactionState txState, LongSet removedFromIndex) {
        return mergeToSet(txState.addedAndRemovedNodes().getRemoved(), removedFromIndex)
                .asUnmodifiable();
    }

    @Override
    public void release() {
        if (securityNodeCursor != null) {
            securityNodeCursor.close();
            securityNodeCursor.release();
            securityNodeCursor = null;
        }
        if (propertyCursor != null) {
            propertyCursor.close();
            propertyCursor.release();
            propertyCursor = null;
        }
        if (accessControlDataProvider != null) {
            accessControlDataProvider.close();
            accessControlDataProvider.release();
            accessControlDataProvider = null;
        }
    }

    @Override
    protected boolean doStoreValuePassesQueryFilter(
            long reference, PropertySelection propertySelection, PropertyIndexQuery[] query) {
        ensureSecurityNodeCursor();
        read.singleNode(reference, securityNodeCursor);
        if (securityNodeCursor.next()) {
            ensurePropertyCursor();
            securityNodeCursor.properties(propertyCursor, propertySelection);
            return CursorPredicates.propertiesMatch(propertyCursor, query);
        }
        return false;
    }

    private void ensureSecurityNodeCursor() {
        if (securityNodeCursor == null) {
            securityNodeCursor = internalCursors.allocateNodeCursor();
        }
    }

    private void ensurePropertyCursor() {
        if (propertyCursor == null) {
            propertyCursor = internalCursors.allocatePropertyCursor();
        }
    }
}

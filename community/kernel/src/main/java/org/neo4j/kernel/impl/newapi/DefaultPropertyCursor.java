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

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.storageengine.api.LongReference;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.storageengine.api.StorageRelationshipCursor;
import org.neo4j.storageengine.api.txstate.EntityState;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;

public class DefaultPropertyCursor extends TraceableCursorImpl<DefaultPropertyCursor>
        implements TraceablePropertyCursor {
    final StoragePropertyCursor storeCursor;
    private final InternalCursorFactory internalCursors;
    private final boolean applyAccessModeToTxState;

    private Read read;
    private EntityState propertiesState;
    private Iterator<StorageProperty> txStateChangedProperties;
    private StorageProperty txStateValue;
    private boolean addedInTx;
    private PropertySelection selection;

    private IntPredicate securityPredicate;
    private AccessControlPropertiesProvider accessControlPropertiesProvider;
    private BiConsumer<StoragePropertyCursor, PropertySelection> securityPropertyInitializer;
    private DefaultNodeCursor securityNodeCursor;
    private DefaultRelationshipScanCursor securityRelCursor;

    DefaultPropertyCursor(
            CursorPool<DefaultPropertyCursor> pool,
            StoragePropertyCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState) {
        super(pool);
        this.storeCursor = storeCursor;
        this.internalCursors = internalCursors;
        this.applyAccessModeToTxState = applyAccessModeToTxState;
    }

    void initNode(
            long nodeReference,
            Reference reference,
            PropertySelection selection,
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        assert nodeReference != LongReference.NULL;

        init(selection, read);
        initializeNodeTransactionState(nodeReference, txStateHolder);
        storeCursor.initNodeProperties(reference, filterSelectionForTxState(selection));

        securityPropertyInitializer =
                (propertyCursor, propertySelection) -> propertyCursor.initNodeProperties(reference, propertySelection);
        securityPredicate = accessModeProvider
                .getAccessMode()
                .allowedToReadNodeProperties(
                        () -> getLabels(nodeReference), this::getSelectedPropertiesProvider, selection);
    }

    void initNode(
            DefaultNodeCursor nodeCursor,
            PropertySelection selection,
            Read read,
            boolean initStoreCursor,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        long nodeReference = nodeCursor.nodeReference();
        assert nodeReference != LongReference.NULL;

        init(selection, read);
        this.addedInTx = nodeCursor.currentNodeIsAddedInTx();
        initializeNodeTransactionState(nodeReference, txStateHolder);
        if (!addedInTx || applyAccessModeToTxState) {
            if (initStoreCursor) {
                storeCursor.initNodeProperties(nodeCursor.storeCursor, filterSelectionForTxState(selection));
            }
        } else {
            storeCursor.reset();
        }

        securityPropertyInitializer = (propertyCursor, propertySelection) ->
                propertyCursor.initNodeProperties(nodeCursor.storeCursor, propertySelection);
        securityPredicate = accessModeProvider
                .getAccessMode()
                .allowedToReadNodeProperties(
                        () -> getLabels(nodeReference), this::getSelectedPropertiesProvider, selection);
    }

    /**
     * Given the {@link PropertySelection} from the initial request, this narrows it down even further,
     * removing keys that has been changed or removed for this entity so that they don't have to be
     * selected from storage cursor. The returned {@link PropertySelection} should be passed to storage cursor.
     */
    private PropertySelection filterSelectionForTxState(PropertySelection selection) {
        // We're giving the entity state to the created selection here, which could be non-ideal if
        // this created selection is kept around in a larger context. But here it isn't.
        return propertiesState == null || propertiesState == EntityState.EMPTY
                ? selection
                : selection.excluding(k -> propertiesState.isPropertyChangedOrRemoved(k));
    }

    private void initializeNodeTransactionState(long nodeReference, TxStateHolder txStateHolder) {
        if (txStateHolder.hasTxStateWithChanges()) {
            this.propertiesState = txStateHolder.txState().getNodeState(nodeReference);
            this.txStateChangedProperties =
                    this.propertiesState.addedAndChangedProperties().iterator();
        } else {
            this.propertiesState = null;
            this.txStateChangedProperties = null;
        }
    }

    void initRelationship(
            long relationshipReference,
            Reference reference,
            PropertySelection selection,
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider) {
        assert relationshipReference != LongReference.NULL;

        init(selection, read);
        initializeRelationshipTransactionState(relationshipReference, txStateHolder);
        storeCursor.initRelationshipProperties(reference, filterSelectionForTxState(selection));

        securityPropertyInitializer = (propertyCursor, propertySelection) ->
                propertyCursor.initRelationshipProperties(reference, propertySelection);
        securityPredicate = accessModeProvider
                .getAccessMode()
                .allowedToReadRelationshipProperties(
                        () -> getRelType(relationshipReference), this::getSelectedPropertiesProvider, selection);
    }

    void initRelationship(
            PropertySelection selection,
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider,
            StorageRelationshipCursor storageRelationshipCursor,
            boolean addedInTransaction,
            long relationshipReference) {
        assert relationshipReference != LongReference.NULL;

        init(selection, read);
        initializeRelationshipTransactionState(relationshipReference, txStateHolder);
        this.addedInTx = addedInTransaction;
        if (!addedInTx || applyAccessModeToTxState) {
            storeCursor.initRelationshipProperties(storageRelationshipCursor, filterSelectionForTxState(selection));
        } else {
            storeCursor.reset();
        }

        securityPropertyInitializer = (propertyCursor, propertySelection) ->
                propertyCursor.initRelationshipProperties(storageRelationshipCursor, propertySelection);
        securityPredicate = accessModeProvider
                .getAccessMode()
                .allowedToReadRelationshipProperties(
                        () -> getRelType(relationshipReference), this::getSelectedPropertiesProvider, selection);
    }

    private void initializeRelationshipTransactionState(long relationshipReference, TxStateHolder txStateHolder) {
        if (txStateHolder.hasTxStateWithChanges()) {
            this.propertiesState = txStateHolder.txState().getRelationshipState(relationshipReference);
            this.txStateChangedProperties =
                    this.propertiesState.addedAndChangedProperties().iterator();
        } else {
            this.propertiesState = null;
            this.txStateChangedProperties = null;
        }
    }

    private void init(PropertySelection selection, Read read) {
        this.selection = selection;
        this.read = read;
    }

    private AccessControlPropertiesProvider getSelectedPropertiesProvider() {
        if (accessControlPropertiesProvider == null) {
            accessControlPropertiesProvider = new AccessControlPropertiesProvider(
                    () -> securityPropertyInitializer,
                    internalCursors,
                    applyAccessModeToTxState,
                    this::txStateProperties);
        }
        return accessControlPropertiesProvider;
    }

    private Iterable<StorageProperty> txStateProperties() {
        return this.propertiesState != null ? this.propertiesState.addedAndChangedProperties() : Iterables.empty();
    }

    @Override
    public boolean next() {
        if (txStateChangedProperties != null) {
            while (txStateChangedProperties.hasNext()) {
                txStateValue = txStateChangedProperties.next();
                int propertyKey = txStateValue.propertyKeyId();
                if (selection.test(propertyKey) && txStateEntryAllowed(propertyKey)) {
                    trace(propertyKey);
                    return true;
                }
            }
            txStateChangedProperties = null;
            txStateValue = null;
        }

        while (storeCursor.next()) {
            int propertyKey = storeCursor.propertyKey();
            if (allowed(propertyKey)) {
                trace(propertyKey);
                return true;
            }
        }
        return false;
    }

    protected boolean txStateEntryAllowed(int propertyKey) {
        return !applyAccessModeToTxState || allowed(propertyKey);
    }

    protected boolean allowed(int propertyKey) {
        return securityPredicate.test(propertyKey);
    }

    private void trace(int propertyKey) {
        if (tracer != null) {
            tracer.onProperty(propertyKey);
        }
    }

    @Override
    public void closeInternal() {
        if (!isClosed()) {
            propertiesState = null;
            txStateChangedProperties = null;
            txStateValue = null;
            read = null;
            storeCursor.reset();
            if (securityNodeCursor != null) {
                securityNodeCursor.close();
                securityNodeCursor = null;
            }
            if (securityRelCursor != null) {
                securityRelCursor.close();
                securityRelCursor = null;
            }
            if (accessControlPropertiesProvider != null) {
                accessControlPropertiesProvider.close();
                accessControlPropertiesProvider = null;
            }
            securityPropertyInitializer = null;
        }
        super.closeInternal();
    }

    @Override
    public int propertyKey() {
        if (txStateValue != null) {
            return txStateValue.propertyKeyId();
        }
        return storeCursor.propertyKey();
    }

    @Override
    public ValueGroup propertyType() {
        if (txStateValue != null) {
            return txStateValue.value().valueGroup();
        }
        return storeCursor.propertyType();
    }

    @Override
    public Value propertyValue() {
        if (txStateValue != null) {
            return txStateValue.value();
        }

        return storeCursor.propertyValue();
    }

    @Override
    public boolean isClosed() {
        return read == null;
    }

    @Override
    public String toString() {
        if (isClosed()) {
            return "PropertyCursor[closed state]";
        }
        return "PropertyCursor[id=" + propertyKey() + ", " + storeCursor + " ]";
    }

    /**
     * Gets the label while ignoring removes in the tx state. Implemented as a Supplier so that we don't need additional
     * allocations.
     *
     * Only used for security checks
     * @param reference
     */
    private TokenSet getLabels(long reference) {
        if (securityNodeCursor == null) {
            securityNodeCursor = internalCursors.allocateFullAccessNodeCursor();
        }
        read.singleNode(reference, securityNodeCursor);
        if (!securityNodeCursor.next()) {
            throw new IllegalStateException("Node " + reference + " not found for security check");
        }
        if (applyAccessModeToTxState) {
            return securityNodeCursor.labels();
        }
        return securityNodeCursor.labelsIgnoringTxStateSetRemove();
    }

    /**
     * Only used for security checks
     * @param reference
     */
    private int getRelType(long reference) {
        if (securityRelCursor == null) {
            securityRelCursor = internalCursors.allocateFullAccessRelationshipScanCursor();
        }
        read.singleRelationship(reference, securityRelCursor);
        if (!securityRelCursor.next()) {
            throw new IllegalStateException("Relationship " + reference + " not found for security check");
        }
        return securityRelCursor.type();
    }

    @Override
    public void release() {
        if (storeCursor != null) {
            storeCursor.close();
        }
        if (securityNodeCursor != null) {
            securityNodeCursor.close();
            securityNodeCursor.release();
            securityNodeCursor = null;
        }
        if (securityRelCursor != null) {
            securityRelCursor.close();
            securityRelCursor.release();
            securityRelCursor = null;
        }
        if (accessControlPropertiesProvider != null) {
            accessControlPropertiesProvider.close();
            accessControlPropertiesProvider = null;
        }
        securityPropertyInitializer = null;
    }
}

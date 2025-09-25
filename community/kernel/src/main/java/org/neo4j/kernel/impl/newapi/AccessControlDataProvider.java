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

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.internal.kernel.api.security.SelectedPropertiesProvider;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.values.storable.Value;

/**
 * Utility component to provide information for access control needs.
 *
 * It is implementation of SelectedPropertiesProvider that returns properties of the entity pointed by StorageEntityCursor
 * at the moment when {@link  #get(PropertySelection)} method called, plus whaterver {@link #txStateProperties} returns.
 *
 * Also, provides methods to get labels and relationship types for the entities.
 */
class AccessControlDataProvider implements SelectedPropertiesProvider, AutoCloseable {

    private final Supplier<BiConsumer<StoragePropertyCursor, PropertySelection>> propertyInitializer;
    private final InternalCursorFactory internalCursors;
    private final boolean applyAccessModeToTxState;
    private final Supplier<Iterable<StorageProperty>> txStateProperties;
    private final Supplier<Read> readSupplier;

    private StoragePropertyCursor propertyCursor;
    private DefaultNodeCursor nodeCursor;

    public AccessControlDataProvider(
            Supplier<BiConsumer<StoragePropertyCursor, PropertySelection>> propertyInitializer,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState,
            Supplier<Iterable<StorageProperty>> txStateProperties,
            Supplier<Read> readSupplier) {
        this.propertyInitializer = propertyInitializer;
        this.internalCursors = internalCursors;
        this.applyAccessModeToTxState = applyAccessModeToTxState;
        this.txStateProperties = txStateProperties;
        this.readSupplier = readSupplier;
    }

    @Override
    public IntObjectMap<Value> get(PropertySelection properties) {
        MutableIntObjectMap<Value> result = IntObjectMaps.mutable.empty();
        fillFromStorage(propertyInitializer.get(), properties, result);
        fillFromTxState(properties, result);
        return result;
    }

    private void fillFromTxState(PropertySelection propertySelection, MutableIntObjectMap<Value> result) {
        if (applyAccessModeToTxState) {
            var txStateChangedProperties = txStateProperties.get();
            for (var changedProperty : txStateChangedProperties) {
                if (propertySelection.test(changedProperty.propertyKeyId())) {
                    result.put(changedProperty.propertyKeyId(), changedProperty.value());
                }
            }
        }
    }

    private void fillFromStorage(
            BiConsumer<StoragePropertyCursor, PropertySelection> propertyInitializer,
            PropertySelection propertySelection,
            MutableIntObjectMap<Value> result) {
        var propertyCursor = getPropertyCursor();
        propertyInitializer.accept(propertyCursor, propertySelection);
        while (propertyCursor.next()) {
            result.put(propertyCursor.propertyKey(), propertyCursor.propertyValue());
        }
    }

    private StoragePropertyCursor getPropertyCursor() {
        if (propertyCursor == null) {
            propertyCursor = internalCursors.allocateStoragePropertyCursor();
        }
        return propertyCursor;
    }

    /**
     * Gets the label while ignoring removes in the tx state. Implemented as a Supplier so that we don't need additional
     * allocations.
     */
    public TokenSet getLabels(long reference) {
        if (nodeCursor == null) {
            nodeCursor = internalCursors.allocateFullAccessNodeCursor();
        }
        readSupplier.get().singleNode(reference, nodeCursor);
        if (!nodeCursor.next()) {
            throw new IllegalStateException("Node " + reference + " not found for security check");
        }
        return nodeLabels(nodeCursor, applyAccessModeToTxState);
    }

    public static TokenSet nodeLabels(DefaultNodeCursor cursor, boolean applyAccessModeToTxState) {
        if (applyAccessModeToTxState) {
            return cursor.labels();
        }
        return cursor.labelsIgnoringTxStateSetRemove();
    }

    @Override
    public void close() {
        if (propertyCursor != null) {
            propertyCursor.close();
            propertyCursor = null;
        }
        if (nodeCursor != null) {
            nodeCursor.close();
            nodeCursor = null;
        }
    }

    public void release() {
        if (propertyCursor != null) {
            propertyCursor.close();
            propertyCursor = null;
        }
        if (nodeCursor != null) {
            nodeCursor.close();
            nodeCursor.release();
            nodeCursor = null;
        }
    }
}

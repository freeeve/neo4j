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

import java.util.function.Supplier;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.neo4j.internal.kernel.api.security.SelectedPropertiesProvider;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.StorageEntityCursor;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.values.storable.Value;

/**
 * Implementation of SelectedPropertiesProvider that returns properties of the entity pointed by StorageEntityCursor
 * at the moment when {@link  #get(PropertySelection)} method called, plus whaterver {@link #txStateProperties} returns.
 *
 * Desgined for lazy loading those properties.
 */
class AccessControlPropertiesProvider implements SelectedPropertiesProvider, AutoCloseable {

    private final StorageEntityCursor storeCursor;
    private final InternalCursorFactory internalCursors;
    private final boolean applyAccessModeToTxState;
    private final Supplier<Iterable<StorageProperty>> txStateProperties;
    private StoragePropertyCursor propertyCursor;

    public AccessControlPropertiesProvider(
            StorageEntityCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState,
            Supplier<Iterable<StorageProperty>> txStateProperties) {
        this.storeCursor = storeCursor;
        this.internalCursors = internalCursors;
        this.applyAccessModeToTxState = applyAccessModeToTxState;
        this.txStateProperties = txStateProperties;
    }

    @Override
    public IntObjectMap<Value> get(PropertySelection properties) {
        MutableIntObjectMap<Value> result = IntObjectMaps.mutable.empty();
        fillFromStorage(storeCursor, properties, result);
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
            StorageEntityCursor storageCursor, PropertySelection propertySelection, MutableIntObjectMap<Value> result) {
        var propertyCursor = getPropertyCursor();
        storageCursor.properties(propertyCursor, propertySelection);
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

    @Override
    public void close() {
        if (propertyCursor != null) {
            propertyCursor.close();
            propertyCursor = null;
        }
    }
}

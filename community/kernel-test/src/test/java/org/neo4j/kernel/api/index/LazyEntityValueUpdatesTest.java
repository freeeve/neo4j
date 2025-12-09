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
package org.neo4j.kernel.api.index;

import static org.neo4j.storageengine.api.LazyValueIndexEntryUpdate.ValueSupplier.constant;

import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.storageengine.api.LazyEntityUpdates;
import org.neo4j.storageengine.api.LazyValueIndexEntryUpdate;
import org.neo4j.values.storable.Value;

class LazyEntityValueUpdatesTest
        extends AbstractEntityValueUpdatesTest<
                LazyEntityUpdates.PropertyValueSupplier, LazyEntityUpdates, LazyEntityUpdates.Builder> {
    @Override
    LazyEntityUpdates.Builder forEntity(long entityId, boolean isNode) {
        return LazyEntityUpdates.forEntity(entityId, isNode);
    }

    @Override
    LazyValueIndexEntryUpdate add(long entityId, IndexDescriptor indexKey, Value... values) {
        LazyValueIndexEntryUpdate.ValueSupplier[] suppliers =
                new LazyValueIndexEntryUpdate.ValueSupplier[values.length];
        for (int i = 0; i < values.length; i++) {
            suppliers[i] = constant(values[i]);
        }
        return LazyValueIndexEntryUpdate.add(entityId, indexKey, suppliers);
    }

    @Override
    LazyValueIndexEntryUpdate remove(long entityId, IndexDescriptor indexKey, Value... values) {
        LazyValueIndexEntryUpdate.ValueSupplier[] suppliers =
                new LazyValueIndexEntryUpdate.ValueSupplier[values.length];
        for (int i = 0; i < values.length; i++) {
            suppliers[i] = constant(values[i]);
        }
        return LazyValueIndexEntryUpdate.remove(entityId, indexKey, suppliers);
    }

    @Override
    LazyValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value before, Value after) {
        return LazyValueIndexEntryUpdate.change(entityId, indexKey, constant(before), constant(after));
    }

    @Override
    LazyValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value[] before, Value... after) {
        LazyValueIndexEntryUpdate.ValueSupplier[] beforeSuppliers =
                new LazyValueIndexEntryUpdate.ValueSupplier[before.length];
        for (int i = 0; i < before.length; i++) {
            beforeSuppliers[i] = constant(before[i]);
        }
        LazyValueIndexEntryUpdate.ValueSupplier[] afterSuppliers =
                new LazyValueIndexEntryUpdate.ValueSupplier[after.length];
        for (int i = 0; i < after.length; i++) {
            afterSuppliers[i] = constant(after[i]);
        }
        return LazyValueIndexEntryUpdate.change(entityId, indexKey, beforeSuppliers, afterSuppliers);
    }
}

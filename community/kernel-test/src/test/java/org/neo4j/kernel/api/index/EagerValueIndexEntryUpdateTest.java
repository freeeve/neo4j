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

import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.storageengine.api.EagerValueIndexEntryUpdate;
import org.neo4j.values.storable.Value;

class EagerValueIndexEntryUpdateTest extends AbstractValueIndexEntryUpdateTest {
    @Override
    EagerValueIndexEntryUpdate add(long entityId, IndexDescriptor indexKey, Value... values) {
        return EagerValueIndexEntryUpdate.add(entityId, indexKey, values);
    }

    @Override
    EagerValueIndexEntryUpdate remove(long entityId, IndexDescriptor indexKey, Value... values) {
        return EagerValueIndexEntryUpdate.remove(entityId, indexKey, values);
    }

    @Override
    EagerValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value before, Value after) {
        return EagerValueIndexEntryUpdate.change(entityId, indexKey, before, after);
    }

    @Override
    EagerValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value[] before, Value... after) {
        return EagerValueIndexEntryUpdate.change(entityId, indexKey, before, after);
    }
}

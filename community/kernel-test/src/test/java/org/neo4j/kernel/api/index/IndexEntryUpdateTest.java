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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

abstract class AbstractValueIndexEntryUpdateTest {
    protected final Value[] multiValue = new Value[] {Values.of("value"), Values.of("value2")};
    protected final Value singleValue = Values.of("value");

    protected static IndexDescriptor forLabel(int labelId, int... propertyIds) {
        return IndexPrototype.forSchema(SchemaDescriptors.forLabel(labelId, propertyIds))
                .withName("0")
                .materialise(0);
    }

    abstract ValueIndexEntryUpdate add(long entityId, IndexDescriptor indexKey, Value... values);

    abstract ValueIndexEntryUpdate remove(long entityId, IndexDescriptor indexKey, Value... values);

    abstract ValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value before, Value after);

    abstract ValueIndexEntryUpdate change(long entityId, IndexDescriptor indexKey, Value[] before, Value... after);

    @Test
    void indexEntryUpdatesShouldBeEqual() {
        ValueIndexEntryUpdate a = add(0, forLabel(3, 4), singleValue);
        ValueIndexEntryUpdate b = add(0, forLabel(3, 4), singleValue);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void addShouldRetainValues() {
        ValueIndexEntryUpdate single = add(0, forLabel(3, 4), singleValue);
        ValueIndexEntryUpdate multi = add(0, forLabel(3, 4, 5), multiValue);
        assertThat(single).isNotEqualTo(multi);
        assertThat(single.values()).isEqualTo(new Object[] {singleValue});
        assertThat(multi.values()).isEqualTo(multiValue);
    }

    @Test
    void removeShouldRetainValues() {
        ValueIndexEntryUpdate single = remove(0, forLabel(3, 4), singleValue);
        ValueIndexEntryUpdate multi = remove(0, forLabel(3, 4, 5), multiValue);
        assertThat(single).isNotEqualTo(multi);
        assertThat(single.values()).isEqualTo(new Object[] {singleValue});
        assertThat(multi.values()).isEqualTo(multiValue);
    }

    @Test
    void addShouldThrowIfAskedForChanged() {
        ValueIndexEntryUpdate single = add(0, forLabel(3, 4), singleValue);
        assertThrows(UnsupportedOperationException.class, single::beforeValues);
    }

    @Test
    void removeShouldThrowIfAskedForChanged() {
        ValueIndexEntryUpdate single = remove(0, forLabel(3, 4), singleValue);
        assertThrows(UnsupportedOperationException.class, single::beforeValues);
    }

    @Test
    void updatesShouldEqualRegardlessOfCreationMethod() {
        ValueIndexEntryUpdate singleAdd = add(0, forLabel(3, 4), singleValue);
        Value[] singleAsArray = {singleValue};
        ValueIndexEntryUpdate multiAdd = add(0, forLabel(3, 4), singleAsArray);
        ValueIndexEntryUpdate singleRemove = remove(0, forLabel(3, 4), singleValue);
        ValueIndexEntryUpdate multiRemove = remove(0, forLabel(3, 4), singleAsArray);
        ValueIndexEntryUpdate singleChange = change(0, forLabel(3, 4), singleValue, singleValue);
        ValueIndexEntryUpdate multiChange = change(0, forLabel(3, 4), singleAsArray, singleAsArray);
        assertThat(singleAdd).isEqualTo(multiAdd);
        assertThat(singleRemove).isEqualTo(multiRemove);
        assertThat(singleChange).isEqualTo(multiChange);
    }

    @Test
    void changedShouldRetainValues() {
        Value singleAfter = Values.of("Hello");
        ValueIndexEntryUpdate singleChange = change(0, forLabel(3, 4), singleValue, singleAfter);
        Value[] multiAfter = {Values.of("Hello"), Values.of("Hi")};
        ValueIndexEntryUpdate multiChange = change(0, forLabel(3, 4, 5), multiValue, multiAfter);
        assertThat(new Object[] {singleValue}).isEqualTo(singleChange.beforeValues());
        assertThat(new Object[] {singleAfter}).isEqualTo(singleChange.values());
        assertThat(multiValue).isEqualTo(multiChange.beforeValues());
        assertThat(multiAfter).isEqualTo(multiChange.values());
    }
}

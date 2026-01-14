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

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.values.storable.Values.stringValue;

import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.values.storable.Value;

public class NodeWriteSamePropValueNoopTest extends NodeWriteTestBase<WriteTestSupport> {
    @Override
    public WriteTestSupport newTestSupport() {
        return new WriteTestSupport() {
            @Override
            protected TestDatabaseManagementServiceBuilder configure(TestDatabaseManagementServiceBuilder builder) {
                builder = builder.setConfig(GraphDatabaseInternalSettings.no_property_update_on_identical_value, true);
                return super.configure(builder);
            }
        };
    }

    @Override
    void shouldWriteWhenSettingPropertyToSameValue() {}

    @Test
    void shouldNotWriteWhenSettingPropertyToSameValue() throws Exception {
        // Given
        Value theValue = stringValue("The Value");
        long nodeId = createNodeWithProperty(propertyKey, theValue.asObject());

        // When
        try (KernelTransaction tx = beginTransaction()) {
            int property = tx.token().propertyKeyGetOrCreateForName(propertyKey);
            tx.dataWrite().nodeSetProperty(nodeId, property, theValue);
            // Then
            assertThat(tx.commit()).isEqualTo(KernelTransaction.READ_ONLY_ID);
        }
    }

    @Override
    void nodeApplyChangesShouldWriteIfPropertyIsSameValue() {}

    @Test
    void nodeApplyChangesShouldNotWriteIfPropertyIsSameValue() throws Exception {
        // Given
        Value theValue = stringValue("The Value");
        long nodeId = createNodeWithProperty(propertyKey, theValue.asObject());

        // When
        try (KernelTransaction tx = beginTransaction()) {
            int key = tx.token().propertyKeyGetOrCreateForName(propertyKey);
            tx.dataWrite()
                    .nodeApplyChanges(
                            nodeId,
                            IntSets.immutable.empty(),
                            IntSets.immutable.empty(),
                            IntObjectMaps.immutable.of(key, theValue));
            // Then
            assertThat(tx.commit()).isEqualTo(KernelTransaction.READ_ONLY_ID);
        }
    }
}

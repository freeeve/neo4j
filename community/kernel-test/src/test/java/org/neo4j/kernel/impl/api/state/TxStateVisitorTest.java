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
package org.neo4j.kernel.impl.api.state;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.kernel.api.txstate.TransactionState;
import org.neo4j.storageengine.api.PropertyKeyValue;
import org.neo4j.storageengine.api.StorageProperty;
import org.neo4j.storageengine.api.txstate.RelationshipModifications;
import org.neo4j.storageengine.api.txstate.TxStateVisitor;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

class TxStateVisitorTest {
    @Test
    void shouldSeeAddedRelationshipProperties() throws Exception {
        // Given
        long relId = 1L;
        int propKey = 2;
        GatheringVisitor visitor = new GatheringVisitor();
        Value value = Values.of("hello");
        state.relationshipDoAddProperty(relId, 1, 2, 3, propKey, value);

        // When
        state.accept(visitor);

        // Then
        StorageProperty prop = new PropertyKeyValue(propKey, Values.of("hello"));
        assertThat(visitor.relPropertyChanges)
                .containsExactly(propChange(relId, singletonList(prop), IntSets.immutable.empty()));
    }

    private static GatheringVisitor.PropertyChange propChange(
            long relId, Collection<StorageProperty> added, IntIterable removed) {
        return new GatheringVisitor.PropertyChange(relId, added, removed);
    }

    private TransactionState state;

    @BeforeEach
    void before() {
        state = new TxState();
    }

    static class GatheringVisitor extends TxStateVisitor.Adapter {
        static class PropertyChange {
            final long entityId;
            final List<StorageProperty> added;
            final IntList removed;

            PropertyChange(long entityId, Collection<StorageProperty> added, IntIterable removed) {
                this.entityId = entityId;
                this.added = Iterables.asList(added);
                this.removed = removed.toList();
            }

            PropertyChange(long entityId, Iterator<StorageProperty> added, IntIterable removed) {
                this.entityId = entityId;
                this.added = Iterators.asList(added);
                this.removed = removed.toList();
            }

            @Override
            public String toString() {
                return "PropertyChange{" + "entityId=" + entityId + ", added=" + added + ", removed=" + removed + '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                PropertyChange that = (PropertyChange) o;

                if (entityId != that.entityId) {
                    return false;
                }
                if (!added.equals(that.added)) {
                    return false;
                }
                return removed.equals(that.removed);
            }

            @Override
            public int hashCode() {
                int result = (int) (entityId ^ (entityId >>> 32));
                result = 31 * result + added.hashCode();
                result = 31 * result + removed.hashCode();
                return result;
            }
        }

        List<PropertyChange> nodePropertyChanges = new ArrayList<>();
        List<PropertyChange> relPropertyChanges = new ArrayList<>();

        @Override
        public void visitNodePropertyChanges(long id, Iterable<StorageProperty> added, IntIterable removed) {
            nodePropertyChanges.add(new PropertyChange(id, added.iterator(), removed));
        }

        @Override
        public void visitRelationshipModifications(RelationshipModifications modifications) {
            modifications
                    .updates()
                    .forEach((id, typeId, startNodeId, endNodeId, added, removed) ->
                            relPropertyChanges.add(new PropertyChange(id, added.iterator(), removed)));
        }
    }
}

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
package org.neo4j.internal.batchimport.input;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.neo4j.internal.helpers.collection.Iterators.asSet;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.InputEntityVisitor;
import org.neo4j.internal.batchimport.input.csv.Decorator;
import org.neo4j.internal.helpers.ArrayUtil;

class InputEntityDecoratorsTest {
    private final InputEntity entity = new InputEntity();
    private final Groups groups = new Groups();
    private final Group group = groups.getOrCreate(null);

    @Test
    void shouldProvideDefaultRelationshipType() throws Exception {
        // GIVEN
        String defaultType = "TYPE";
        InputEntityVisitor relationship =
                InputEntityDecorators.defaultRelationshipType(defaultType).apply(entity);

        // WHEN
        relationship(relationship, "source", 1, 0, emptyMap(), null, "start", "end", group, null, null);

        // THEN
        assertThat(entity.stringType).isEqualTo(defaultType);
    }

    @Test
    void shouldNotOverrideAlreadySetRelationshipType() throws Exception {
        // GIVEN
        String defaultType = "TYPE";
        InputEntityVisitor relationship =
                InputEntityDecorators.defaultRelationshipType(defaultType).apply(entity);

        // WHEN
        String customType = "CUSTOM_TYPE";
        relationship(relationship, "source", 1, 0, emptyMap(), null, "start", "end", group, customType, null);

        // THEN
        assertThat(entity.stringType).isEqualTo(customType);
    }

    @Test
    void shouldNotOverrideAlreadySetRelationshipTypeId() throws Exception {
        // GIVEN
        String defaultType = "TYPE";
        Decorator decorator = InputEntityDecorators.defaultRelationshipType(defaultType);
        InputEntityVisitor relationship = decorator.apply(entity);

        // WHEN
        int typeId = 5;
        relationship(relationship, "source", 1, 0, emptyMap(), null, "start", "end", group, null, typeId);

        // THEN
        assertThat(entity.hasIntType).isTrue();
        assertThat(entity.intType).isEqualTo(typeId);
    }

    @Test
    void shouldAddLabelsToNodeWithoutLabels() throws Exception {
        // GIVEN
        String[] toAdd = new String[] {"Add1", "Add2"};
        InputEntityVisitor node = InputEntityDecorators.additiveLabels(toAdd).apply(entity);

        // WHEN
        node(node, "source", 1, 0, "id", group, emptyMap(), null, InputEntity.NO_LABELS, null);

        // THEN
        assertThat(entity.labels()).containsExactly(toAdd);
    }

    @Test
    void shouldAddMissingLabels() throws Exception {
        // GIVEN
        String[] toAdd = new String[] {"Add1", "Add2"};
        InputEntityVisitor node = InputEntityDecorators.additiveLabels(toAdd).apply(entity);

        // WHEN
        String[] nodeLabels = new String[] {"SomeOther"};
        node(node, "source", 1, 0, "id", group, emptyMap(), null, nodeLabels, null);

        // THEN
        assertThat(asSet(entity.labels())).hasSameElementsAs(asSet(ArrayUtil.union(toAdd, nodeLabels)));
    }

    @Test
    void shouldNotTouchLabelsIfNodeHasLabelFieldSet() throws Exception {
        // GIVEN
        String[] toAdd = new String[] {"Add1", "Add2"};
        InputEntityVisitor node = InputEntityDecorators.additiveLabels(toAdd).apply(entity);

        // WHEN
        long labelField = 123L;
        node(node, "source", 1, 0, "id", group, emptyMap(), null, null, labelField);

        // THEN
        assertThat(entity.labels().length).isZero();
        assertThat(entity.labelField).isEqualTo(labelField);
    }

    @Test
    void shouldCramMultipleDecoratorsIntoOne() {
        // GIVEN
        Decorator decorator1 = spy(new IdentityDecorator());
        Decorator decorator2 = spy(new IdentityDecorator());
        Decorator multi = InputEntityDecorators.decorators(decorator1, decorator2);

        // WHEN
        InputEntityVisitor node = mock(InputEntityVisitor.class);
        multi.apply(node);

        // THEN
        InOrder order = inOrder(decorator1, decorator2);
        order.verify(decorator1).apply(node);
        order.verify(decorator2).apply(node);
        order.verifyNoMoreInteractions();
    }

    private static void node(
            InputEntityVisitor entity,
            String sourceDescription,
            long lineNumber,
            long position,
            Object id,
            Group group,
            Map<String, Object> properties,
            Long propertyId,
            String[] labels,
            Long labelField)
            throws IOException {
        applyProperties(entity, properties, propertyId);
        entity.id(id, group);
        if (labelField != null) {
            entity.labelField(labelField);
        } else {
            entity.labels(labels);
        }
        entity.endOfEntity();
    }

    private static void relationship(
            InputEntityVisitor entity,
            String sourceDescription,
            long lineNumber,
            long position,
            Map<String, Object> properties,
            Long propertyId,
            Object startNode,
            Object endNode,
            Group group,
            String type,
            Integer typeId)
            throws IOException {
        applyProperties(entity, properties, propertyId);
        entity.startId(startNode, group);
        entity.endId(endNode, group);
        if (typeId != null) {
            entity.type(typeId);
        } else if (type != null) {
            entity.type(type);
        }
        entity.endOfEntity();
    }

    private static void applyProperties(InputEntityVisitor entity, Map<String, Object> properties, Long propertyId) {
        if (propertyId != null) {
            entity.propertyId(propertyId);
        }
        properties.forEach((key, value) -> entity.property(key, value, false));
    }

    private static class IdentityDecorator implements Decorator {
        @Override
        public InputEntityVisitor apply(InputEntityVisitor entity) {
            return entity;
        }
    }
}

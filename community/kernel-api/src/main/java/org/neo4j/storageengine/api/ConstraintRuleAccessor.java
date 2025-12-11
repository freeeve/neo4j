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
package org.neo4j.storageengine.api;

import org.neo4j.common.TokenNameLookup;
import org.neo4j.internal.kernel.api.exceptions.schema.CreateConstraintFailureException;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.internal.schema.constraints.KeyConstraintDescriptor;
import org.neo4j.internal.schema.constraints.NodeLabelExistenceConstraintDescriptor;
import org.neo4j.internal.schema.constraints.RelationshipEndpointLabelConstraintDescriptor;
import org.neo4j.internal.schema.constraints.TypeConstraintDescriptor;
import org.neo4j.internal.schema.constraints.UniquenessConstraintDescriptor;

public interface ConstraintRuleAccessor {
    ConstraintDescriptor readConstraint(ConstraintDescriptor rule);

    ConstraintDescriptor createUniquenessConstraintRule(UniquenessConstraintDescriptor descriptor, long indexId);

    ConstraintDescriptor createKeyConstraintRule(
            KeyConstraintDescriptor descriptor, long indexId, TokenNameLookup tokenNameLookup)
            throws CreateConstraintFailureException;

    ConstraintDescriptor createExistenceConstraint(ConstraintDescriptor descriptor, TokenNameLookup tokenNameLookup)
            throws CreateConstraintFailureException;

    ConstraintDescriptor createPropertyTypeConstraint(
            TypeConstraintDescriptor descriptor, TokenNameLookup tokenNameLookup)
            throws CreateConstraintFailureException;

    ConstraintDescriptor createRelationshipEndpointLabelConstraint(
            RelationshipEndpointLabelConstraintDescriptor descriptor, TokenNameLookup tokenNameLookup)
            throws CreateConstraintFailureException;

    ConstraintDescriptor createNodeLabelExistenceConstraint(
            NodeLabelExistenceConstraintDescriptor descriptor, TokenNameLookup tokenNameLookup)
            throws CreateConstraintFailureException;
}

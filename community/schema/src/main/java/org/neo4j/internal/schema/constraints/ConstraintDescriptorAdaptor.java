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
package org.neo4j.internal.schema.constraints;

import org.neo4j.internal.schema.ConstraintDescriptor;

public abstract class ConstraintDescriptorAdaptor implements ConstraintDescriptor {

    @Override
    public boolean enforcesUniqueness() {
        return false;
    }

    @Override
    public boolean enforcesPropertyExistence() {
        return false;
    }

    @Override
    public boolean enforcesPropertyType() {
        return false;
    }

    @Override
    public boolean isPropertyTypeConstraint() {
        return false;
    }

    @Override
    public boolean isRelationshipEndpointLabelConstraint() {
        return false;
    }

    @Override
    public boolean isNodeLabelExistenceConstraint() {
        return false;
    }

    @Override
    public boolean isNodePropertyTypeConstraint() {
        return false;
    }

    @Override
    public boolean isRelationshipPropertyTypeConstraint() {
        return false;
    }

    @Override
    public TypeConstraintDescriptor asPropertyTypeConstraint() {
        throw conversionException(TypeConstraintDescriptor.class);
    }

    @Override
    public boolean isPropertyExistenceConstraint() {
        return false;
    }

    @Override
    public boolean isRelationshipPropertyExistenceConstraint() {
        return false;
    }

    @Override
    public boolean isNodePropertyExistenceConstraint() {
        return false;
    }

    @Override
    public ExistenceConstraintDescriptor asPropertyExistenceConstraint() {
        throw conversionException(ExistenceConstraintDescriptor.class);
    }

    @Override
    public boolean isUniquenessConstraint() {
        return false;
    }

    @Override
    public boolean isNodeUniquenessConstraint() {
        return false;
    }

    @Override
    public boolean isRelationshipUniquenessConstraint() {
        return false;
    }

    @Override
    public UniquenessConstraintDescriptor asUniquenessConstraint() {
        throw conversionException(UniquenessConstraintDescriptor.class);
    }

    @Override
    public boolean isNodeKeyConstraint() {
        return false;
    }

    @Override
    public boolean isRelationshipKeyConstraint() {
        return false;
    }

    @Override
    public boolean isIndexBackedConstraint() {
        return false;
    }

    @Override
    public IndexBackedConstraintDescriptor asIndexBackedConstraint() {
        throw conversionException(IndexBackedConstraintDescriptor.class);
    }

    @Override
    public boolean isKeyConstraint() {
        return false;
    }

    @Override
    public KeyConstraintDescriptor asKeyConstraint() {
        throw conversionException(KeyConstraintDescriptor.class);
    }

    @Override
    public RelationshipEndpointLabelConstraintDescriptor asRelationshipEndpointLabelConstraint() {
        throw conversionException(RelationshipEndpointLabelConstraintDescriptor.class);
    }

    @Override
    public NodeLabelExistenceConstraintDescriptor asNodeLabelExistenceConstraint() {
        throw conversionException(NodeLabelExistenceConstraintDescriptor.class);
    }

    IllegalStateException conversionException(Class<? extends ConstraintDescriptor> targetType) {
        return new IllegalStateException("Cannot cast this schema to a " + targetType
                + " because it does not match that structure: " + this + ".");
    }
}

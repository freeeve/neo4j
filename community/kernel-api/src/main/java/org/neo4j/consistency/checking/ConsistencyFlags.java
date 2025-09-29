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
package org.neo4j.consistency.checking;

import java.util.Objects;

public record ConsistencyFlags(
        boolean checkStructure,
        boolean checkIndexes,
        ConstraintFlags checkConstraints,
        boolean checkGraph,
        boolean checkCounts,
        boolean checkPropertyOwners) {
    public static final ConsistencyFlags NONE =
            new ConsistencyFlags(false, false, ConstraintFlags.NO_CONSTRAINTS, false, false, false);
    public static final ConsistencyFlags ALL =
            new ConsistencyFlags(true, true, ConstraintFlags.ALL_CONSTRAINTS, true, true, true);
    public static final ConsistencyFlags DEFAULT = ALL.withoutCheckPropertyOwners();

    public ConsistencyFlags {
        IllegalArgumentException argumentException = null;
        if (!checkGraph) {
            if (checkCounts) {
                argumentException =
                        requireNonNullAndAppendException(argumentException, checkGraphInvariant("checkCounts"));
            }
            if (checkPropertyOwners) {
                argumentException =
                        requireNonNullAndAppendException(argumentException, checkGraphInvariant("checkPropertyOwners"));
            }
        }

        if (argumentException != null) {
            throw argumentException;
        }
    }

    public ConsistencyFlags(
            boolean checkIndexes, boolean checkGraph, boolean checkCounts, boolean checkPropertyOwners) {
        this(
                DEFAULT.checkStructure(),
                checkIndexes,
                DEFAULT.checkConstraints(),
                checkGraph,
                checkCounts,
                checkPropertyOwners);
    }

    public ConsistencyFlags withCheckStructure() {
        return new ConsistencyFlags(true, checkIndexes, checkConstraints, checkGraph, checkCounts, checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckStructure() {
        return new ConsistencyFlags(
                false, checkIndexes, checkConstraints, checkGraph, checkCounts, checkPropertyOwners);
    }

    public ConsistencyFlags withCheckIndexes() {
        return new ConsistencyFlags(
                checkStructure, true, checkConstraints, checkGraph, checkCounts, checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckIndexes() {
        return new ConsistencyFlags(
                checkStructure, false, checkConstraints, checkGraph, checkCounts, checkPropertyOwners);
    }

    public ConsistencyFlags withCheckConstraints() {
        return new ConsistencyFlags(
                checkStructure,
                checkIndexes,
                ConstraintFlags.ALL_CONSTRAINTS,
                checkGraph,
                checkCounts,
                checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckConstraints() {
        return new ConsistencyFlags(
                checkStructure,
                checkIndexes,
                ConstraintFlags.NO_CONSTRAINTS,
                checkGraph,
                checkCounts,
                checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckPropertyConstraints() {
        return new ConsistencyFlags(
                checkStructure,
                checkIndexes,
                ConstraintFlags.NO_PROPERTY_CONSTRAINTS,
                checkGraph,
                checkCounts,
                checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckRelEndpointConstraints() {
        return new ConsistencyFlags(
                checkStructure,
                checkIndexes,
                ConstraintFlags.NO_REL_ENDPOINT_CONSTRAINTS,
                checkGraph,
                checkCounts,
                checkPropertyOwners);
    }

    public ConsistencyFlags withCheckGraph() {
        return new ConsistencyFlags(
                checkStructure, checkIndexes, checkConstraints, true, checkCounts, checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckGraph() {
        return new ConsistencyFlags(checkStructure, checkIndexes, checkConstraints, false, false, false);
    }

    public ConsistencyFlags withCheckCounts() {
        return new ConsistencyFlags(checkStructure, checkIndexes, checkConstraints, true, true, checkPropertyOwners);
    }

    public ConsistencyFlags withoutCheckCounts() {
        return new ConsistencyFlags(
                checkStructure, checkIndexes, checkConstraints, checkGraph, false, checkPropertyOwners);
    }

    public ConsistencyFlags withCheckPropertyOwners() {
        return new ConsistencyFlags(checkStructure, checkIndexes, checkConstraints, true, checkCounts, true);
    }

    public ConsistencyFlags withoutCheckPropertyOwners() {
        return new ConsistencyFlags(checkStructure, checkIndexes, checkConstraints, checkGraph, checkCounts, false);
    }

    private static IllegalArgumentException requireNonNullAndAppendException(
            IllegalArgumentException argumentException, Exception exception) {
        final var ex = Objects.requireNonNullElseGet(argumentException, IllegalArgumentException::new);
        ex.addSuppressed(exception);
        return ex;
    }

    private static IllegalArgumentException checkGraphInvariant(String check) {
        return new IllegalArgumentException(
                "'%s' cannot be set to '%s' with 'checkGraph' set to '%s'.".formatted(check, true, false));
    }

    public record ConstraintFlags(
            boolean propertyConstraints, boolean relEndpointConstraints, boolean labelExistenceConstraints) {
        public static final ConstraintFlags ALL_CONSTRAINTS = new ConstraintFlags(true, true, true);
        public static final ConstraintFlags NO_CONSTRAINTS = new ConstraintFlags(false, false, false);
        public static final ConstraintFlags NO_PROPERTY_CONSTRAINTS = new ConstraintFlags(false, true, true);
        public static final ConstraintFlags NO_REL_ENDPOINT_CONSTRAINTS = new ConstraintFlags(true, false, true);
    }
}

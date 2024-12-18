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
package org.neo4j.kernel.api.exceptions.schema;

import static java.lang.String.format;

import java.util.Locale;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.kernel.api.exceptions.Status;

public class IncompatibleGraphTypeDependenceException extends SchemaKernelException {

    private IncompatibleGraphTypeDependenceException(
            ErrorGqlStatusObject gqlStatusObject,
            ConstraintDescriptor constraint,
            ConstraintDescriptor preExistingConstraint,
            TokenNameLookup tokenNameLookup) {
        super(
                gqlStatusObject,
                Status.Schema.ConstraintCreationFailed,
                constructUserMessage(constraint, preExistingConstraint, tokenNameLookup));
    }

    public static IncompatibleGraphTypeDependenceException incompatibleGraphTypeDependence(
            ConstraintDescriptor constraint,
            ConstraintDescriptor preExistingConstraint,
            TokenNameLookup tokenNameLookup) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB2)
                .withParam(
                        GqlParams.StringParam.graphTypeDependence1,
                        constraint.graphTypeDependence().name().toLowerCase(Locale.ROOT))
                .withParam(GqlParams.StringParam.constrDescrOrName1, constraint.userDescription(tokenNameLookup))
                .withParam(
                        GqlParams.StringParam.graphTypeDependence2,
                        preExistingConstraint.graphTypeDependence().name().toLowerCase(Locale.ROOT))
                .withParam(
                        GqlParams.StringParam.constrDescrOrName2,
                        preExistingConstraint.userDescription(tokenNameLookup))
                .build();
        return new IncompatibleGraphTypeDependenceException(gql, constraint, preExistingConstraint, tokenNameLookup);
    }

    private static String constructUserMessage(
            ConstraintDescriptor constraint,
            ConstraintDescriptor preExistingConstraint,
            TokenNameLookup tokenNameLookup) {
        return format(
                "Graph type %s constraint: %s is incompatible with graph type %s %s due to differing graph type dependence.",
                constraint.graphTypeDependence().name().toLowerCase(Locale.ROOT),
                constraint.userDescription(tokenNameLookup),
                preExistingConstraint.graphTypeDependence().name().toLowerCase(Locale.ROOT),
                preExistingConstraint.userDescription(tokenNameLookup));
    }
}

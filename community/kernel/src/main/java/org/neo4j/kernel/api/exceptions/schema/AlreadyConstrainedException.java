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

import org.neo4j.common.TokenNameLookup;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.kernel.api.exceptions.Status;

public class AlreadyConstrainedException extends SchemaKernelException {

    private static final String ALREADY_CONSTRAINED_MESSAGE_PREFIX = "Constraint already exists: ";

    private static final String INDEX_CONTEXT_FORMAT =
            "There is a uniqueness constraint on %s, so an index is " + "already created that matches this.";

    private AlreadyConstrainedException(String message, ErrorGqlStatusObject gqlStatusObject) {
        super(gqlStatusObject, Status.Schema.ConstraintAlreadyExists, message);
    }

    // KNL-014
    public static AlreadyConstrainedException cannotCreateIndex(
            ConstraintDescriptor constraint, TokenNameLookup tokenNameLookup) {
        var constraintName = constraint.getName();
        var description = constraint.schema().userDescription(tokenNameLookup);

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N74)
                .withParam(GqlParams.StringParam.constr, constraintName)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N70)
                        .withParam(GqlParams.StringParam.idxDescrOrName, description)
                        .build())
                .build();
        var message = messageWithLabelAndPropertyName(tokenNameLookup, INDEX_CONTEXT_FORMAT, constraint.schema());
        return new AlreadyConstrainedException(message, gql);
    }

    // KNL-015
    public static AlreadyConstrainedException cannotCreateConstraint(
            ConstraintDescriptor constraint, TokenNameLookup tokenNameLookup) {
        var constraintUD = constraint.userDescription(tokenNameLookup);

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N65)
                .withParam(GqlParams.StringParam.constrDescrOrName, constraintUD)
                .build();
        var message = ALREADY_CONSTRAINED_MESSAGE_PREFIX + constraintUD;
        return new AlreadyConstrainedException(message, gql);
    }
}

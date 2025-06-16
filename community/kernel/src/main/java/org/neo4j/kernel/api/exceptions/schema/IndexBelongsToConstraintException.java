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

import org.neo4j.common.TokenNameLookup;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.kernel.api.exceptions.Status;

public class IndexBelongsToConstraintException extends SchemaKernelException {
    private static final String MESSAGE_SCHEMA = "Index belongs to constraint: %s";
    private static final String MESSAGE_NAME = "Index belongs to constraint: `%s`";

    private IndexBelongsToConstraintException(String message, ErrorGqlStatusObject errorGqlStatusObject) {
        super(errorGqlStatusObject, Status.Schema.ForbiddenOnConstraintIndex, message);
    }

    // KNL-019
    public static IndexBelongsToConstraintException indexBelongsToConstraint(
            SchemaDescriptor descriptor, TokenNameLookup tokenNameLookup) {
        var userDescription = descriptor.userDescription(tokenNameLookup);
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBC)
                .withParam(GqlParams.StringParam.constrDescrOrName, userDescription)
                .build();
        var message = format(MESSAGE_SCHEMA, userDescription);
        return new IndexBelongsToConstraintException(message, gql);
    }

    // KNL-020
    public static IndexBelongsToConstraintException indexBelongsToConstraint(String constraintName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBC)
                .withParam(GqlParams.StringParam.constrDescrOrName, constraintName)
                .build();
        var message = format(MESSAGE_NAME, constraintName);
        return new IndexBelongsToConstraintException(message, gql);
    }
}

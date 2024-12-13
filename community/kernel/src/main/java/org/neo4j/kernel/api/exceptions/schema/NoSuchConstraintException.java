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
import org.neo4j.internal.schema.SchemaDescriptorSupplier;
import org.neo4j.kernel.api.exceptions.Status;

public class NoSuchConstraintException extends SchemaKernelException {
    private static final String MESSAGE = "No such constraint %s.";

    private NoSuchConstraintException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, Status.Schema.ConstraintNotFound, message);
    }

    // KNL-027
    public static NoSuchConstraintException noSuchConstraint(String name) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N64)
                .withParam(GqlParams.StringParam.constrDescrOrName, name)
                .build();
        return new NoSuchConstraintException(gql, format(MESSAGE, name));
    }

    // KNL-027
    public static NoSuchConstraintException noSuchConstraint(
            SchemaDescriptorSupplier constraint, TokenNameLookup lookup) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N64)
                .withParam(GqlParams.StringParam.constrDescrOrName, constraint.userDescription(lookup))
                .build();
        return new NoSuchConstraintException(gql, format(MESSAGE, constraint.userDescription(lookup)));
    }
}

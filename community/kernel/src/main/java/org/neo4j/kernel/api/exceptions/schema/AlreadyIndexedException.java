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
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.kernel.api.exceptions.Status;

public class AlreadyIndexedException extends SchemaKernelException {

    private static final String INDEX_CONTEXT_FORMAT = "There already exists an index %s.";
    private static final String CONSTRAINT_CONTEXT_FORMAT =
            "There already exists an index %s. " + "A constraint cannot be created until the index has been dropped.";

    private AlreadyIndexedException(String message, ErrorGqlStatusObject gqlStatusObject) {
        super(gqlStatusObject, Status.Schema.IndexAlreadyExists, message);
    }

    // KNL-022
    public static AlreadyIndexedException cannotCreateIndex(
            SchemaDescriptor descriptor, TokenNameLookup tokenNameLookup) {
        var userDescription = descriptor.userDescription(tokenNameLookup);

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N70)
                .withParam(GqlParams.StringParam.idxDescrOrName, userDescription)
                .build();
        var message = messageWithLabelAndPropertyName(tokenNameLookup, INDEX_CONTEXT_FORMAT, descriptor);
        return new AlreadyIndexedException(message, gql);
    }

    // KNL-023
    public static AlreadyIndexedException cannotCreateConstraint(
            SchemaDescriptor descriptor, TokenNameLookup tokenNameLookup) {
        var userDescription = descriptor.userDescription(tokenNameLookup);

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N73)
                .withParam(GqlParams.StringParam.idxDescrOrName, userDescription)
                .build();
        var message = messageWithLabelAndPropertyName(tokenNameLookup, CONSTRAINT_CONTEXT_FORMAT, descriptor);
        return new AlreadyIndexedException(message, gql);
    }
}

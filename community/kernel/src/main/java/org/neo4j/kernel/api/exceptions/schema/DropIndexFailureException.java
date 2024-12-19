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

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.kernel.api.exceptions.Status;

public class DropIndexFailureException extends SchemaKernelException {
    private DropIndexFailureException(String message) {
        super(Status.Schema.IndexDropFailed, message);
    }

    private DropIndexFailureException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, Status.Schema.IndexDropFailed, message);
    }

    public DropIndexFailureException(String message, Throwable cause) {
        super(Status.Schema.IndexDropFailed, message, cause);
    }

    public DropIndexFailureException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, Status.Schema.IndexDropFailed, message, cause);
    }

    // KNL-035
    public static DropIndexFailureException noIndexSpecified() {
        return new DropIndexFailureException("No index was specified.");
    }

    // KNL-036
    public static DropIndexFailureException indexDoesNotExist(String indexName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N10)
                .withParam(GqlParams.StringParam.idxDescrOrName, indexName)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N69)
                        .withParam(GqlParams.StringParam.idxDescrOrName, indexName)
                        .build())
                .build();
        return new DropIndexFailureException(
                gql, "Unable to drop index called `" + indexName + "`. There is no such index.");
    }
}

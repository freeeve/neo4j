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
package org.neo4j.exceptions;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class EntityNotFoundException extends Neo4jException {

    private EntityNotFoundException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static EntityNotFoundException databaseNotFound(String kind, String dbName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N00)
                        .withParam(GqlParams.StringParam.db, dbName)
                        .build())
                .build();

        return new EntityNotFoundException(gql, String.format("%s not found: %s", kind, dbName));
    }

    public static EntityNotFoundException databaseWithElementIdNotFound(String elementId) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA7)
                        .withParam(GqlParams.StringParam.db, elementId)
                        .build())
                .build();

        return new EntityNotFoundException(
                gql, String.format("Database corresponding to element id not found: %s", elementId));
    }

    public static EntityNotFoundException nodeUnexpectedlyDeleted(long nodeId) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N11)
                .build();
        return new EntityNotFoundException(gql, "Node " + nodeId + " was unexpectedly deleted");
    }

    public static EntityNotFoundException nodeDeletedInThisTransaction(long nodeId) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N13)
                .withParam(GqlParams.StringParam.entityType, "node")
                .build();
        return new EntityNotFoundException(
                gql, "Node with id %s has been deleted in this transaction".formatted(nodeId));
    }

    public static EntityNotFoundException relationshipDeletedInThisTransaction(long relId) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N13)
                .withParam(GqlParams.StringParam.entityType, "relationship")
                .build();
        return new EntityNotFoundException(
                gql, "Relationship with id %s has been deleted in this transaction".formatted(relId));
    }

    public static EntityNotFoundException unsupportedAccessOfStandardDb(String graph, String composite) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N05)
                        .withParam(GqlParams.StringParam.db1, graph)
                        .withParam(GqlParams.StringParam.db2, composite)
                        .withParam(GqlParams.StringParam.db3, graph)
                        .build())
                .build();

        return new EntityNotFoundException(
                gql,
                String.format(
                        "When connected to a composite database, access is allowed only to its constituents. "
                                + "Attempted to access '%s' while connected to '%s'",
                        graph, composite));
    }

    public static EntityNotFoundException createEntityNotFoundException(
            ErrorGqlStatusObject gqlStatusObject, String message) {
        return new EntityNotFoundException(gqlStatusObject, message);
    }

    @Override
    public Status status() {
        return Status.Statement.EntityNotFound;
    }
}

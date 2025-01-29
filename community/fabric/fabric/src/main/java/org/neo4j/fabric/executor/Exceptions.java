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
package org.neo4j.fabric.executor;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.HasQuery;
import org.neo4j.kernel.api.exceptions.Status;

public class Exceptions {
    public static RuntimeException transform(ErrorGqlStatusObject gqlStatusObject, Status defaultStatus, Throwable t) {
        return Exceptions.transform(gqlStatusObject, defaultStatus, t, null);
    }

    public static RuntimeException transform(
            ErrorGqlStatusObject fallbackGqlStatusObject, Status defaultStatus, Throwable t, Long queryId) {

        // preserve the original exception if possible
        // or try to preserve  at least the original status
        if (t instanceof Status.HasStatus withStatus) {
            if (t instanceof RuntimeException runtimeException) {
                if (queryId == null) {
                    return runtimeException;
                } else if (t instanceof HasQuery withQuery) {
                    withQuery.setQuery(queryId);
                    return runtimeException;
                }
            }
            if (t instanceof ErrorGqlStatusObject gqlStatusObjectOfUnwrapped) {
                return new FabricException(gqlStatusObjectOfUnwrapped, withStatus.status(), t.getMessage(), t, queryId);
            }
            return new FabricException(withStatus.status(), t.getMessage(), t, queryId);
        }

        return new FabricException(fallbackGqlStatusObject, defaultStatus, t.getMessage(), t, queryId);
    }

    public static RuntimeException transformTransactionStartFailure(Throwable t) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N06)
                .build();
        return transform(gql, Status.Transaction.TransactionStartFailed, t);
    }

    public static RuntimeException transformUnexpectedError(Status defaultStatus, Throwable t) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N42)
                .build();
        return transform(gql, defaultStatus, t);
    }

    public static RuntimeException transformUnexpectedError(Status defaultStatus, Throwable t, long queryId) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N42)
                .build();
        return transform(gql, defaultStatus, t, queryId);
    }
}

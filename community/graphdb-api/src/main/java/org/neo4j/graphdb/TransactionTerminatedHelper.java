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
package org.neo4j.graphdb;

import static org.neo4j.kernel.api.exceptions.Status.Classification.DatabaseError;
import static org.neo4j.kernel.api.exceptions.Status.Classification.TransientError;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * This helper class contains methods to create `TransactionTerminatedException`.
 * These would normally be on the exception class itself, but that is `@PublicApi`,
 * and we don't want these methods to be public API.
 */
public class TransactionTerminatedHelper {

    public static TransactionTerminatedException transactionTerminated(Status status) {
        var gql = getGqlStatusObject(status, status.code().description());
        return new TransactionTerminatedException(gql, status, "");
    }

    public static TransactionTerminatedException transactionTerminated(Status status, String additionalInfo) {
        var gql = getGqlStatusObject(status, status.code().description() + " " + additionalInfo);
        return new TransactionTerminatedException(gql, status, additionalInfo);
    }

    private static ErrorGqlStatusObject getGqlStatusObject(Status status, String reason) {
        GqlStatusInfoCodes gqlStatus;

        switch (status.code().classification()) {
            case ClientError -> gqlStatus = GqlStatusInfoCodes.STATUS_25N14;
            case DatabaseError -> gqlStatus = GqlStatusInfoCodes.STATUS_25N15;
            case TransientError -> gqlStatus = GqlStatusInfoCodes.STATUS_25N16;

            default -> {
                // The termination was closed because of a ClientNotification, which should never happen
                var msg = String.format(
                        "Expected transaction termination to be caused by a ClientError, DatabaseError or TransientError but was %s",
                        status.code().classification());
                var gql = GqlHelper.get50N00(TransactionTerminatedException.class.getSimpleName(), msg);
                throw new TransactionFailureException(gql, msg);
            }
        }

        return ErrorGqlStatusObjectImplementation.from(gqlStatus)
                .withParam(GqlParams.StringParam.msg, reason)
                .build();
    }
}

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

import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * This helper class contains methods to create `TransactionFailureException`. These would normally be on the exception
 * class itself, but that is `@PublicApi`, and we don't want these methods to be public API.
 */
public class TransactionFailureHelper {

    public static final String UNABLE_TO_COMPLETE_TRANSACTION = "Unable to complete transaction.";

    public static TransactionFailureException failToStartTransaction(Throwable e) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N06)
                .build();
        return new TransactionFailureException(
                gql, "Fail to start new transaction.", e, Status.Transaction.TransactionStartFailed);
    }

    /**
     * All usages of this should log the error to the debug log.
     * We cannot do this here without adding a dependency on the log package, which would be a circular dependency.
     */
    public static TransactionFailureException genericFailure(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N02)
                .build();
        return new TransactionFailureException(gql, UNABLE_TO_COMPLETE_TRANSACTION, cause);
    }
}

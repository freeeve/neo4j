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
package org.neo4j.kernel.impl.coreapi;

import static org.neo4j.graphdb.TransactionFailureHelper.UNABLE_TO_COMPLETE_TRANSACTION;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.graphdb.ConstraintViolationException;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.TransactionFailureHelper;
import org.neo4j.graphdb.TransientFailureException;
import org.neo4j.graphdb.TransientTransactionFailureException;
import org.neo4j.internal.kernel.api.exceptions.ConstraintViolationTransactionFailureException;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.exceptions.Status.Classification;
import org.neo4j.logging.Log;
import org.neo4j.monitoring.ExceptionHandlerService;

public class DefaultTransactionExceptionMapper implements TransactionExceptionMapper {
    public static final DefaultTransactionExceptionMapper INSTANCE = new DefaultTransactionExceptionMapper();

    private DefaultTransactionExceptionMapper() {}

    @Override
    public RuntimeException mapException(Exception e, Log log, ExceptionHandlerService exceptionHandlerService) {
        if (e instanceof TransientFailureException tfe) {
            // We let transient exceptions pass through unchanged since they aren't really transaction failures
            // in the same sense as unexpected failures are. Such exception signals that the transaction
            // can be retried and might be successful the next time.
            return tfe;
        } else if (e instanceof ConstraintViolationTransactionFailureException) {
            return new ConstraintViolationException(e.getMessage(), e);
        } else if (e instanceof Status.HasStatus) {
            Status status = ((Status.HasStatus) e).status();
            return mapStatusException(
                    UNABLE_TO_COMPLETE_TRANSACTION + ": " + status.code().description(),
                    status,
                    e,
                    exceptionHandlerService);
        } else {
            // GQL status code 25N02 points to the debug log for more information, so let's make sure people will
            // actually find more info there.
            log.error(e.getMessage(), e);
            exceptionHandlerService.raiseException(e.getMessage(), e);
            if (e instanceof ErrorGqlStatusObject statusObject) {
                return TransactionFailureHelper.genericFailure(statusObject, e);
            }
            return TransactionFailureHelper.genericFailure(e);
        }
    }

    public static RuntimeException mapStatusException(
            String message, Status status, Exception cause, ExceptionHandlerService exceptionHandlerService) {
        ErrorGqlStatusObject gql = cause instanceof ErrorGqlStatusObject statusObject
                ? statusObject.gqlStatusObject()
                : ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N02)
                        .build();

        if (status.code().classification() == Classification.TransientError) {
            return new TransientTransactionFailureException(gql, status, message, cause);
        }
        exceptionHandlerService.raiseException(cause.getMessage(), cause);
        return new TransactionFailureException(gql, message, cause, status);
    }
}

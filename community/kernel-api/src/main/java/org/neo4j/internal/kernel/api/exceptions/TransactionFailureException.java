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
package org.neo4j.internal.kernel.api.exceptions;

import static org.neo4j.kernel.api.exceptions.Status.Cluster.ReplicationFailure;
import static org.neo4j.kernel.api.exceptions.Status.General.UnknownError;
import static org.neo4j.kernel.api.exceptions.Status.Transaction.LeaseExpired;
import static org.neo4j.kernel.api.exceptions.Status.Transaction.TransactionCommitFailed;

import org.neo4j.exceptions.KernelException;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.logging.Log;

public class TransactionFailureException extends KernelException {

    protected TransactionFailureException(
            ErrorGqlStatusObject gqlStatusObject,
            Status statusCode,
            Throwable cause,
            String message,
            Object... parameters) {
        super(gqlStatusObject, statusCode, cause, message, parameters);
    }

    private TransactionFailureException(ErrorGqlStatusObject gqlStatusObject, Status statusCode, Throwable cause) {
        super(gqlStatusObject, statusCode, cause);
    }

    protected TransactionFailureException(
            ErrorGqlStatusObject gqlStatusObject, Status statusCode, String message, Object... parameters) {
        super(gqlStatusObject, statusCode, message, parameters);
    }

    private TransactionFailureException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, Status.Transaction.TransactionStartFailed, cause, message);
    }

    public static <EX extends Throwable & Status.HasStatus> TransactionFailureException wrapError(EX cause) {
        if (cause instanceof ErrorGqlStatusObject gqlException && gqlException.gqlStatusObject() != null) {
            return new TransactionFailureException(gqlException, cause.status(), cause, cause.getMessage());
        }
        // This case can be removed once all errors has been ported to GQLSTATUS
        return new TransactionFailureException(GqlHelper.getDefaultObject(), cause.status(), cause, cause.getMessage());
    }

    public static TransactionFailureException internalError(String msgTitle, String message, Throwable cause) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new TransactionFailureException(gql, message, cause);
    }

    public static TransactionFailureException internalError(
            Status statusCode, String msgTitle, String message, Object... parameters) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new TransactionFailureException(gql, statusCode, message, parameters);
    }

    public static TransactionFailureException internalError(
            Status statusCode, Throwable cause, String msgTitle, String message, Object... parameters) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new TransactionFailureException(gql, statusCode, cause, message, parameters);
    }

    public static TransactionFailureException leaseExpired(int currentLeaseId, int leaseId) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N08)
                .build();
        return new TransactionFailureException(
                gql,
                LeaseExpired,
                "The lease used for the transaction has expired: [current lease id:%d, transaction lease id:%d]",
                currentLeaseId,
                leaseId);
    }

    public static TransactionFailureException invalidatedLease() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N08)
                .build();
        return new TransactionFailureException(gql, LeaseExpired, "The lease has been invalidated");
    }

    public static TransactionFailureException unexpectedOutcome(String outcome) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N09)
                .build();
        return new TransactionFailureException(gql, UnknownError, "Unexpected outcome: " + outcome);
    }

    public static TransactionFailureException replicationError(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N33)
                .build();
        return new TransactionFailureException(gql, ReplicationFailure, cause);
    }

    public static TransactionFailureException innerTransactionsStillOpen() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_2DN07)
                .build();
        return new TransactionFailureException(
                gql,
                TransactionCommitFailed,
                "The transaction cannot be committed when it has open inner transactions.");
    }

    public static TransactionFailureException unknownError(Throwable e) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N42)
                .build();
        return new TransactionFailureException(gql, Status.General.UnknownError, e);
    }

    public static TransactionFailureException cannotBeCommitedInReadOnlyDb() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N08)
                .build();
        return new TransactionFailureException(
                gql,
                Status.General.ForbiddenOnReadOnlyDatabase,
                "Transactions cannot be committed in a read-only Neo4j database");
    }

    public static TransactionFailureException transactionRollbackFailed(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_40N01)
                .build();
        return new TransactionFailureException(gql, Status.Transaction.TransactionRollbackFailed, cause);
    }

    // KNL-038
    public static TransactionFailureException cannotRollbackCannotDropCreatedConstraintIndex(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_40N01)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N10)
                        // We should have an indexName here, but we don't have it
                        .build())
                .build();
        return new TransactionFailureException(
                gql, Status.Transaction.TransactionRollbackFailed, cause, "Could not drop created constraint indexes");
    }

    public static TransactionFailureException couldNotApplyTransaction(String batchString, Throwable cause, Log log) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_2DN05)
                .build();
        final var message =
                "Could not apply the transaction: %s to the store after written to log.".formatted(batchString);
        var e = new TransactionFailureException(gql, Status.Transaction.TransactionCommitFailed, cause, message);
        log.error(message, e);
        return e;
    }

    public static TransactionFailureException couldNotAppendTransaction(String batchString, Throwable cause, Log log) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_2DN06)
                .build();
        final var message = "Could not append transaction: %s to log.".formatted(batchString);
        final var e = new TransactionFailureException(gql, Status.Transaction.TransactionLogError, cause, message);
        log.error(message, e);
        return e;
    }

    public static TransactionFailureException couldNotPreallocateDiskSpace(
            String batchString, Status status, Throwable cause, Log log) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N59)
                .build();
        final var message = "Could not preallocate disk space for the transaction: %s".formatted(batchString);
        final var e = new TransactionFailureException(gql, status, cause, message);
        log.error(message, e);
        return e;
    }
}

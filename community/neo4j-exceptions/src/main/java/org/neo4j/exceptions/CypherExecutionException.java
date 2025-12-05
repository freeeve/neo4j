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
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class CypherExecutionException extends Neo4jException {

    @Deprecated
    public CypherExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CypherExecutionException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    protected CypherExecutionException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    private <EX extends Throwable & ErrorGqlStatusObject> CypherExecutionException(EX cause) {
        super(cause, cause.legacyMessage(), cause);
    }

    public static <EX extends Throwable & ErrorGqlStatusObject> CypherExecutionException wrapError(EX e) {
        if (e.gqlStatusObject() != null) {
            return new CypherExecutionException(e);
        }
        // This case can be removed once all instances of ProcedureException has been ported to GQLSTATUS
        return new CypherExecutionException(GqlHelper.getDefaultObject(), e.getMessage(), e);
    }

    public static CypherExecutionException wrapKernelException(String msg, KernelException e) {
        if (e.gqlStatusObject() != null) {
            return new CypherExecutionException(e, msg, e);
        }
        // This case can be removed once all instances of KernelException has been ported to GQLSTATUS
        return new CypherExecutionException(GqlHelper.getDefaultObject(), msg, e);
    }

    public static CypherExecutionException csvBufferSizeOverflow(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N49)
                        .build())
                .build();
        return new CypherExecutionException(gql, """
                Tried to read a field larger than the current buffer size.
                 Make sure that the field doesn't have an unterminated quote,
                 if it doesn't you can try increasing the buffer size via `dbms.import.csv.buffer_size`.""", cause);
    }

    public static CypherExecutionException internalError(String msgTitle, String msg) {
        var gql = GqlHelper.get50N00(msgTitle, msg);
        return new CypherExecutionException(gql, msg);
    }

    public static CypherExecutionException internalError(String msgTitle, String msg, Throwable cause) {
        var gql = GqlHelper.get50N00(msgTitle, msg);
        return new CypherExecutionException(gql, msg, cause);
    }

    public static CypherExecutionException queryExecutionFailed(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N42)
                .build();
        return new CypherExecutionException(gql, "Query execution failed", cause);
    }

    public static CypherExecutionException unknownError(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N42)
                .build();
        return new CypherExecutionException(gql, cause.getMessage(), cause);
    }

    public static CypherExecutionException unexpectedError(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                .withParam(GqlParams.StringParam.msgTitle, "Unexpected error")
                .withParam(GqlParams.StringParam.msg, cause.getMessage())
                .build();
        return new CypherExecutionException(gql, cause.getMessage(), cause);
    }

    public static CypherExecutionException unrecognisedExecutionMode(String procedure, String mode) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                .withParam(GqlParams.StringParam.proc, procedure)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N03)
                        .withParam(GqlParams.StringParam.proc, procedure)
                        .withParam(GqlParams.StringParam.procExeMode, mode)
                        .build())
                .build();
        return new CypherExecutionException(
                gql, "Unable to execute procedure, because it requires an unrecognized execution mode: " + mode, null);
    }

    public static CypherExecutionException unrecognisedCypherType(String input) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N52)
                        .withParam(GqlParams.StringParam.input, input)
                        .build())
                .build();
        return new CypherExecutionException(
                gql, "Unable to execute procedure, because the signature has an unrecognized type: " + input, null);
    }

    public static CypherExecutionException genericAdministrationException(String msg, Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N41)
                .withParam(GqlParams.StringParam.msg, msg)
                .build();
        return new CypherExecutionException(gql, msg, cause);
    }

    public static CypherExecutionException failedCopyPrivileges(String to, String from, Throwable cause) {
        var msg = String.format("Failed to create role '%s' as copy of '%s': Failed to copy privileges.", to, from);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException failedToAlterDb(String dbName, Throwable cause) {
        var msg = String.format("Failed to alter the specified database '%s'.", dbName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException createEntityCause(String entity, String name, Throwable cause) {
        var msg = String.format("Failed to create the specified %s '%s'", entity, name);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException deleteEntityCause(String entity, String name, Throwable cause) {
        var msg = String.format("Failed to delete the specified %s '%s'", entity, name);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException alterEntityCause(String entity, String name, Throwable cause) {
        var msg = String.format("Failed to alter the specified %s '%s'", entity, name);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException renameEntityCause(
            String entity, String oldName, String newName, Throwable cause) {
        var msg = String.format("Failed to rename the specified %s '%s' to '%s'.", entity, oldName, newName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException createRoleCopyCause(String role, Throwable cause) {
        var msg = String.format("Failed to create a role as copy of '%s'", role);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException dbStart(String dbName, Throwable cause) {
        var msg = String.format("Failed to start the specified database '%s'.", dbName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException dbStop(String dbName, Throwable cause) {
        var msg = String.format("Failed to stop the specified database '%s'.", dbName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException enableServer(String server, Throwable cause) {
        var msg = String.format("Failed to enable the specified server '%s'.", server);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException reallocateDbs(Throwable cause) {
        var msg = String.format("Failed to reallocate databases: %s", cause.getMessage());
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException deallocateServers(String names, Throwable cause) {
        var msg = String.format("Failed to deallocate the specified server(s) '%s'.", names);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException grantRole(String role, String userName, Throwable cause) {
        var msg = String.format("Failed to grant role '%s' to user '%s'.", role, userName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException grantRoleToAuthRule(String role, String userName, Throwable cause) {
        var msg = String.format("Failed to grant role '%s' to auth rule '%s'.", role, userName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException revokeRole(String role, String userName, Throwable cause) {
        var msg = String.format("Failed to revoke role '%s' from user '%s'.", role, userName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException revokeRoleFromAuthRule(String role, String userName, Throwable cause) {
        var msg = String.format("Failed to revoke role '%s' from auth rule '%s'.", role, userName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException grantOrDenyPrivilege(
            String type, String action, String role, Throwable cause) {
        var msg = String.format("Failed to %s %s privilege to role '%s'.", type, action, role);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException revokePrivilege(String action, String role, Throwable cause) {
        var msg = String.format("Failed to revoke %s privilege from role '%s'.", action, role);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException alterOwnPassword(String userName, Throwable cause) {
        var msg = String.format("User '%s' failed to alter their own password.", userName);
        return genericAdministrationException(msg, cause);
    }

    public static CypherExecutionException entityFromOtherDb(String elementId, String currentDb, String expectedDb) {
        var legacyMsg = "Can not use an entity from another database. Entity element id: "
                + elementId + ", entity database: "
                + currentDb + ", expected database: "
                + expectedDb + ".";

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N04)
                        .withParam(GqlParams.StringParam.db1, expectedDb)
                        .withParam(GqlParams.StringParam.db2, currentDb)
                        .withParam(GqlParams.StringParam.db3, expectedDb)
                        .build())
                .build();

        return new CypherExecutionException(gql, legacyMsg);
    }

    @Override
    public Status status() {
        return getCause() instanceof Status.HasStatus st ? st.status() : Status.Statement.ExecutionFailed;
    }
}

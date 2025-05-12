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
package org.neo4j.dbms.routing;

import static java.lang.String.format;
import static org.neo4j.kernel.api.exceptions.Status.Database.DatabaseNotFound;
import static org.neo4j.kernel.api.exceptions.Status.Database.IllegalAliasChain;
import static org.neo4j.kernel.api.exceptions.Status.General.DatabaseUnavailable;
import static org.neo4j.kernel.api.exceptions.Status.Procedure.ProcedureCallFailed;

import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlException;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlParams.StringParam;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.util.VisibleForTesting;

public class RoutingException extends GqlException implements Status.HasStatus {
    private final Status status;

    @VisibleForTesting
    public RoutingException(ErrorGqlStatusObject gqlStatusObject, Status status, String message) {
        super(gqlStatusObject, message);
        this.status = status;
    }

    public static RoutingException policyDefinitionNotFound(String policyName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N15)
                .withParam(GqlParams.StringParam.routingPolicy, policyName)
                .build();
        return new RoutingException(
                gql,
                Status.Routing.RoutingFailed,
                format("Policy definition for '%s' could not be found.", policyName));
    }

    public static RoutingException serverPanic(String panicReason) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N32)
                .build();
        return new RoutingException(gql, Status.Routing.DbmsInPanic, panicReason);
    }

    public static RoutingException invalidAddressKey() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N16)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N10)
                        .build())
                .build();
        return new RoutingException(
                gql,
                Status.Procedure.ProcedureCallFailed,
                "An address key is included in the query string provided to the "
                        + "GetRoutingTableProcedure, but its value could not be parsed.");
    }

    public static RoutingException boltNotEnabled(String dbName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N70)
                .withParam(GqlParams.StringParam.db, dbName)
                .build();
        return new RoutingException(
                gql,
                ProcedureCallFailed,
                "Cannot get routing table for " + dbName
                        + " because Bolt is not enabled. Please update your configuration for '"
                        + BoltConnector.enabled.name()
                        + "'");
    }

    public static RoutingException routingTableIsEmpty(String dbName) {
        var gql = GqlHelper.getGql08N09(dbName);
        return new RoutingException(
                gql, DatabaseUnavailable, String.format("Routing table for database %s is empty", dbName));
    }

    public static RoutingException routingTableForUnavailableDb(String dbName) {
        var gql = GqlHelper.getGql08N09(dbName);
        return new RoutingException(
                gql,
                DatabaseUnavailable,
                String.format(
                        "Unable to get a routing table for database '%s' because this database is unavailable",
                        dbName));
    }

    public static RoutingException routingTableForNonExistingDb(String dbName) {
        return new RoutingException(
                GqlHelper.getGql22000_22N51(dbName),
                DatabaseNotFound,
                "Unable to get a routing table for database '" + dbName + "' because this database does not exist");
    }

    public static RoutingException aliasChainsNotPermitted(String databaseName, String sourceAlias) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N14)
                .withParam(GqlParams.StringParam.alias1, databaseName)
                .withParam(GqlParams.StringParam.alias2, sourceAlias)
                .build();
        return new RoutingException(
                gql,
                IllegalAliasChain,
                "Unable to provide a routing table for the database '"
                        + databaseName + "' because the request came from another alias '"
                        + sourceAlias + "' and alias chains are not permitted.");
    }

    public static RoutingException invalidRoutingRequest(String fieldName) {
        var gqlCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N41)
                .withParam(StringParam.field, fieldName)
                .build();
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N16)
                .withCause(gqlCause)
                .build();
        return new RoutingException(gql, Status.Procedure.ProcedureCallFailed, gqlCause.getMessage());
    }

    @Override
    public Status status() {
        return status;
    }
}

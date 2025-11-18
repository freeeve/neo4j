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
package org.neo4j.dbms.api;

import static java.lang.String.format;
import static org.neo4j.gqlstatus.PrivilegeGqlCodeEntity.DATABASE;
import static org.neo4j.gqlstatus.PrivilegeGqlCodeEntity.entityNotFound;

import java.util.List;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

/**
 * This helper class contains methods to create `DatabaseNotFoundException`. These would normally be on the exception
 * class itself, but that is `@PublicApi`, and we don't want these methods to be public API.
 * <br>
 * There are two GQL status variants for database not found:
 * <ul>
 *  <li>
 *      {@link org.neo4j.gqlstatus.GqlStatusInfoCodes#STATUS_42001} - Syntax Error: Thrown when the database is considered to be an entity that can be modified, usually in admin commands such as ALTER DB, DROP DB, USE DB, etc.
 *      <br>
 *      They should probably throw {@link org.neo4j.exceptions.InvalidArgumentException} instead.
 *  </li>
 *  <li>
 *      {@link org.neo4j.gqlstatus.GqlStatusInfoCodes#STATUS_22000} - Data Exception: Thrown when the database is considered a static part of the system. For example when trying to connect to a database that doesn't exist.
 *  </li>
 * </ul>
 */
public class DatabaseNotFoundHelper {

    // region [Data Exception Helpers]

    public static DatabaseNotFoundException databaseNotFound(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(gql, "database not found: " + databaseName);
    }

    public static DatabaseNotFoundException cannotFindDatabase(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(gql, "Cannot find database: " + databaseName);
    }

    public static DatabaseNotFoundException databaseDoesNotExist(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(gql, "Database does not exist: " + databaseName);
    }

    public static DatabaseNotFoundException databaseWithNameNotFound(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(gql, format("Database with name `%s` not found.", databaseName));
    }

    public static DatabaseNotFoundException noDatabaseFoundWithNameAlias(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(
                gql, String.format("No database found with name/alias '%s'", databaseName));
    }

    public static DatabaseNotFoundException noDatabaseFoundWithNameAliasOnServers(
            String databaseName, List<String> servers) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(
                gql, String.format("No database found with name/alias '%s' on server(s) '%s'", databaseName, servers));
    }

    public static DatabaseNotFoundException defaultDatabaseNotFound(String defaultDatabaseName) {
        var gql = GqlHelper.getGql22000_22N51(defaultDatabaseName);
        return new DatabaseNotFoundException(gql, "Default database not found: " + defaultDatabaseName);
    }

    public static DatabaseNotFoundException databaseNotFoundForRecreate(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(
                gql,
                format(
                        "Failed to recreate the specified database '%s': No database exists with that name or alias.",
                        databaseName));
    }

    public static DatabaseNotFoundException upstreamDatabaseNotFound(String databaseName) {
        var gql = GqlHelper.getGql22000_22N51(databaseName);
        return new DatabaseNotFoundException(
                gql,
                format(
                        "Failed to use the specified database '%s' as an upstream: No database exists with that name or alias.",
                        databaseName));
    }

    // endregion [Data Exception Helpers]

    // region [Syntax Error Helpers]

    public static DatabaseNotFoundException compositeDatabaseNotFound(String databaseName, String paramName) {
        var gql = entityNotFound(DATABASE, databaseName, paramName);
        var message = gql.gqlStatusObject()
                .cause()
                .map(ErrorGqlStatusObject::statusDescription)
                .orElse(gql.statusDescription());
        return new DatabaseNotFoundException(gql, message);
    }

    public static DatabaseNotFoundException failedCreateCompositeAlias(
            String fullName, String namespaceName, String paramName) {
        var gql = entityNotFound(DATABASE, namespaceName, paramName);
        return new DatabaseNotFoundException(
                gql,
                format(
                        "Failed to create the specified database alias '%s': "
                                + "Composite database '%s' does not exist.",
                        fullName, namespaceName));
    }

    public static DatabaseNotFoundException failedCreateAlias(
            String aliasName, String targetName, String targetParameterName) {
        var gql = entityNotFound(DATABASE, targetName, targetParameterName);
        return new DatabaseNotFoundException(
                gql,
                format(
                        "Failed to create the specified database alias '%s': " + "Database '%s' does not exist.",
                        aliasName, targetName));
    }

    public static DatabaseNotFoundException failedDeleteComposite(String name, String paramName) {
        var gql = entityNotFound(DATABASE, name, paramName);
        return new DatabaseNotFoundException(
                gql, format("Failed to delete the specified composite database '%s': Database does not exist.", name));
    }

    public static DatabaseNotFoundException failedAction(String action, String name, String paramName) {
        var gql = entityNotFound(DATABASE, name, paramName);
        return new DatabaseNotFoundException(
                gql, format("Failed to %s the specified database '%s': Database does not exist.", action, name));
    }

    public static DatabaseNotFoundException failedActionAlias(
            String action, String alias, String name, String dnNameParamName) {
        var gql = entityNotFound(DATABASE, name, dnNameParamName);
        return new DatabaseNotFoundException(
                gql,
                format(
                        "Failed to %s the specified database alias '%s': Database '%s' does not exist.",
                        action, alias, name));
    }

    public static DatabaseNotFoundException noNameOrAlias(String name, String paramName) {
        var gql = entityNotFound(DATABASE, name, paramName);
        return new DatabaseNotFoundException(
                gql, format("Database '%s' does not exist': No database exists with that name or alias.", name));
    }

    public static DatabaseNotFoundException databaseNameNotFoundWithoutDot(String name) {
        var gql = entityNotFound(DATABASE, name, null);
        return new DatabaseNotFoundException(gql, format("Database %s not found", name));
    }

    public static DatabaseNotFoundException databaseNameNotFoundWithDot(String name) {
        var gql = entityNotFound(DATABASE, name, null);
        return new DatabaseNotFoundException(gql, format("Database %s not found.", name));
    }

    public static DatabaseNotFoundException graphNotFound(String name) {
        var gql = entityNotFound(DATABASE, name, null);
        return new DatabaseNotFoundException(gql, format("Graph not found: %s", name));
    }

    public static DatabaseNotFoundException byElementIdFunction(String elementId) {
        var gqlStatus = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA7)
                        .withParam(GqlParams.StringParam.db, "graph.byElementId(" + elementId + ")")
                        .build())
                .build();
        return new DatabaseNotFoundException(
                gqlStatus,
                "No database is corresponding to `graph.byElementId(" + elementId
                        + ")`. Verify that the elementId is correct.");
    }

    public static DatabaseNotFoundException invalidCommandDatabaseDoesNotExistsWithLegacyMessage(
            String msg, String command, String dbname, String parameterName) {
        var gql = GqlHelper.get42N00_databaseNotFound(command, dbname, parameterName);
        return new DatabaseNotFoundException(gql, msg);
    }

    // endregion [Syntax Error Helpers]
}

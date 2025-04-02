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

import static org.neo4j.gqlstatus.PrivilegeGqlCodeEntity.DATABASE;
import static org.neo4j.gqlstatus.PrivilegeGqlCodeEntity.entityAlreadyExists;

import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

/**
 * This helper class contains methods to create `DatabaseExistsException`. These would normally be on the exception
 * class itself, but that is `@PublicApi`, and we don't want these methods to be public API.
 * <br>
 * See {@link org.neo4j.exceptions.InvalidArgumentException#failedActionEntityNotFound} for similar exceptions.
 */
public class DatabaseExistsHelper {

    public static DatabaseExistsException failedCreateAliasBecauseDatabaseExists(String databaseName) {
        var gql = entityAlreadyExists(DATABASE, databaseName);
        return new DatabaseExistsException(
                gql,
                "Failed to create the specified database alias '%s': Database exists with that name."
                        .formatted(databaseName));
    }

    public static DatabaseExistsException failedCreateAliasBecauseAliasExists(String databaseName) {
        var gql = entityAlreadyExists(DATABASE, databaseName);
        return new DatabaseExistsException(
                gql,
                "Failed to create the specified database alias '%s': Database name or alias already exists."
                        .formatted(databaseName));
    }

    public static DatabaseExistsException failedCreateDatabaseBecauseDatabaseExists(String name, String otherName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N87)
                .withParam(GqlParams.StringParam.db1, name)
                .withParam(GqlParams.StringParam.db2, otherName)
                .build();

        return new DatabaseExistsException(
                gql,
                String.format(
                        "Cannot create database '%s' because another database '%s' exists with an ambiguous name.",
                        name, otherName));
    }

    public static DatabaseExistsException failedCreateDatabaseBecauseAliasExists(String databaseName) {
        var gql = entityAlreadyExists(DATABASE, databaseName);
        return new DatabaseExistsException(
                gql,
                "Failed to create the specified database '%s': Database name or alias already exists."
                        .formatted(databaseName));
    }

    public static DatabaseExistsException failedCreateDatabaseBecausePartAlreadyExists(
            String ownerDatabaseName, String databaseName) {
        var gql = entityAlreadyExists(DATABASE, databaseName);
        return new DatabaseExistsException(
                gql,
                "Failed to create the specified database '%s': Part of database '%s' name or alias already exists."
                        .formatted(ownerDatabaseName, databaseName));
    }
}

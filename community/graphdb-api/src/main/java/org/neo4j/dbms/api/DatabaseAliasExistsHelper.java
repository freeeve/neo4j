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

import java.util.List;
import java.util.stream.Collectors;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

/**
 * This helper class contains methods to create `DatabaseAliasExistsException`. These would normally be on the exception
 * class itself, but that is `@PublicApi`, and we don't want these methods to be public API.
 */
public class DatabaseAliasExistsHelper {

    public static DatabaseAliasExistsException cannotDropDatabaseWithAlias(String database, List<String> aliases) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N82)
                .withParam(GqlParams.StringParam.db, database)
                .withParam(
                        GqlParams.ListParam.aliasList, aliases.stream().sorted().toList())
                .build();
        var aliasesJoined = aliases.stream().sorted().collect(Collectors.joining("', '", "'", "'"));
        var legacyMessage =
                "Failed to delete the specified database '%s': Database has one or more aliases. Drop the aliases: [%s] before dropping the database."
                        .formatted(database, aliasesJoined);
        return new DatabaseAliasExistsException(gql, legacyMessage);
    }

    public static DatabaseAliasExistsException cannotDropCompositeWithAlias(String database, List<String> aliases) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N82)
                .withParam(GqlParams.StringParam.db, database)
                .withParam(
                        GqlParams.ListParam.aliasList, aliases.stream().sorted().toList())
                .build();
        var aliasesJoined = aliases.stream().sorted().collect(Collectors.joining("', '", "'", "'"));
        var legacyMessage =
                "Failed to delete the specified composite database '%s': Composite database has one or more constituent aliases. Drop the aliases: [%s] before dropping the database."
                        .formatted(database, aliasesJoined);
        return new DatabaseAliasExistsException(gql, legacyMessage);
    }
}

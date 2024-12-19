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

import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

/**
 * This helper class contains methods to create `DatabaseLimitReachedException`. These would normally be on the
 * exception class itself, but that is `@PublicApi`, and we don't want these methods to be public API.
 */
public class DatabaseLimitReachedHelper {
    public static DatabaseLimitReachedException cannotCreateAdditionalDb(String dbName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N55)
                .withParam(GqlParams.StringParam.db, dbName)
                .withParam(GqlParams.StringParam.cfgSetting, "dbms.max_databases")
                .build();

        return new DatabaseLimitReachedException(
                gql, String.format("Failed to create the specified database '%s':", dbName));
    }
}

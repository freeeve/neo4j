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

import org.neo4j.gqlstatus.GqlHelper;

/**
 * This helper class contains methods to create `DatabaseManagementException`. These would normally be on the
 * exception class itself, but that is `@PublicApi`, and we don't want these methods to be public API.
 */
public class DatabaseManagementHelper {
    public static DatabaseManagementException internalError(String msgTitle, String message) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new DatabaseManagementException(gql, message);
    }

    public static DatabaseManagementException internalError(String msgTitle, String message, Throwable cause) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new DatabaseManagementException(gql, message, cause);
    }
}

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
package org.neo4j.dbms.systemgraph;

import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.kernel.database.NamedDatabaseId.NAMED_SYSTEM_DATABASE_ID;

import java.util.function.Supplier;
import org.neo4j.dbms.database.DatabaseContextProvider;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.security.AuthProviderFailedException;

public class SystemGraphTestHelper {
    public static Supplier<GraphDatabaseService> makeSystemSupplier(
            DatabaseContextProvider<?> databaseContextProvider) {
        return () -> databaseContextProvider
                .getDatabaseContext(NAMED_SYSTEM_DATABASE_ID)
                .orElseThrow(() -> AuthProviderFailedException.internalError(
                        SystemGraphTestHelper.class.getSimpleName(),
                        "No database called `" + SYSTEM_DATABASE_NAME + "` was found."))
                .databaseFacade();
    }
}

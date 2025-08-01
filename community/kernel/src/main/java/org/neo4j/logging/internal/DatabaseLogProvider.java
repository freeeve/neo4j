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
package org.neo4j.logging.internal;

import org.neo4j.logging.AbstractLogProvider;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.logging.NullLogProvider;

public class DatabaseLogProvider extends AbstractLogProvider<DatabaseLog> {
    private final DatabaseLogIdentifier databaseLogIdentifier;
    private final InternalLogProvider logProvider;

    public static DatabaseLogProvider nullDatabaseLogProvider() {
        return new DatabaseLogProvider(DatabaseLogIdentifier.EMPTY, NullLogProvider.getInstance());
    }

    public DatabaseLogProvider(DatabaseLogIdentifier databaseLogIdentifier, InternalLogProvider logProvider) {
        this.databaseLogIdentifier = databaseLogIdentifier;
        this.logProvider = logProvider;
    }

    @Override
    protected DatabaseLog buildLog(Class<?> loggingClass) {
        return new DatabaseLog(databaseLogIdentifier, logProvider.getLog(loggingClass));
    }

    @Override
    protected DatabaseLog buildLog(String name) {
        return new DatabaseLog(databaseLogIdentifier, logProvider.getLog(name));
    }
}

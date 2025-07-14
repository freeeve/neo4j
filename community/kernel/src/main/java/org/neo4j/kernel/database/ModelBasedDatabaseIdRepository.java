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
package org.neo4j.kernel.database;

import java.util.Optional;

public class ModelBasedDatabaseIdRepository implements DatabaseIdRepository {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final Optional<NamedDatabaseId> NAMED_SYSTEM_DATABASE_ID =
            Optional.of(NamedDatabaseId.NAMED_SYSTEM_DATABASE_ID);

    private final DatabaseObjectRepositoryModelProvider modelProvider;

    public ModelBasedDatabaseIdRepository(DatabaseObjectRepositoryModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public Optional<NamedDatabaseId> getByName(NormalizedDatabaseName normalizedDatabaseName) {
        if (NamedDatabaseId.SYSTEM_DATABASE_NAME.equals(normalizedDatabaseName.name())) {
            return NAMED_SYSTEM_DATABASE_ID;
        }
        return modelProvider.withModel(model -> model.getDatabaseIdByAlias(normalizedDatabaseName.name()));
    }

    @Override
    public Optional<NamedDatabaseId> getById(DatabaseId databaseId) {
        if (DatabaseId.SYSTEM_DATABASE_ID.equals(databaseId)) {
            return NAMED_SYSTEM_DATABASE_ID;
        }
        return modelProvider.withModel(model -> model.getDatabaseIdByUUID(databaseId.uuid()));
    }

    @Override
    public Optional<NamedDatabaseId> getOwningDatabaseId(DatabaseId databaseId) {
        if (DatabaseId.SYSTEM_DATABASE_ID.equals(databaseId)) {
            return NAMED_SYSTEM_DATABASE_ID;
        }
        return modelProvider.withModel(model -> model.getDatabaseIdByUUID(databaseId.uuid(), true));
    }
}

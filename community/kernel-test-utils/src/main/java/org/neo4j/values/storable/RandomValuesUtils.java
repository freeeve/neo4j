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
package org.neo4j.values.storable;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.storageengine.api.StorageEngineFactory;

public class RandomValuesUtils {
    private RandomValuesUtils() {}

    /** Select default configuration for RandomValues
     * <p/>>
     * Utility to select default configuration for when tests are executed under multiple storage engines.
     */
    public static RandomValues.Configuration selectStorageEngineDependentConfiguration(GraphDatabaseService db) {
        return selectStorageEngineDependentConfigurationBuilder(db).build();
    }

    public static RandomValues.Configuration selectStorageEngineDependentConfiguration(String storageEngineName) {
        return selectStorageEngineDependentConfigurationBuilder(storageEngineName)
                .build();
    }

    public static RandomValues.ConfigurationBuilder selectStorageEngineDependentConfigurationBuilder(
            GraphDatabaseService db) {
        var storageEngine = ((GraphDatabaseAPI) db)
                .getDependencyResolver()
                .resolveOptionalDependency(StorageEngineFactory.class)
                .orElseThrow();
        return selectStorageEngineDependentConfigurationBuilder(storageEngine.name());
    }

    public static RandomValues.ConfigurationBuilder selectStorageEngineDependentConfigurationBuilder(
            String storageEngineName) {
        return switch (storageEngineName) {
            case "block" -> RandomValues.newConfigurationBuilder();
            default -> RandomValues.newConfigurationBuilder().includeVectorTypes(false);
        };
    }
}

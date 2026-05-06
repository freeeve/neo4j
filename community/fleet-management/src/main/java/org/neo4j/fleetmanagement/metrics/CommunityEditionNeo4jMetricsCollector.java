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
package org.neo4j.fleetmanagement.metrics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.fleetmanagement.communication.model.DataPoint;

public class CommunityEditionNeo4jMetricsCollector implements ICollector {

    private static final int COLLECT_EVERY = 10;
    private final String dbName;
    private final Path dataDirectory;
    private final Path transactionsDirectory;
    private int callCountUntilReport = 0;

    public CommunityEditionNeo4jMetricsCollector(Config config) {
        dbName = config.get(GraphDatabaseSettings.initial_default_database);
        dataDirectory = Path.of(config.get(GraphDatabaseSettings.data_directory)
                .resolve(GraphDatabaseSettings.DEFAULT_DATABASES_ROOT_DIR_NAME)
                .resolve(dbName)
                .toString());
        transactionsDirectory = Path.of(config.get(GraphDatabaseSettings.transaction_logs_root_path)
                .resolve(dbName)
                .toString());
    }

    @Override
    public void start() {}

    @Override
    public void collect(Map<String, List<DataPoint>> data) {
        if (callCountUntilReport == 0) {
            var size = FileUtils.sizeOfDirectory(dataDirectory.toFile())
                    + FileUtils.sizeOfDirectory(transactionsDirectory.toFile());
            data.computeIfAbsent("fleet_management_neo4j_store_size_database", k -> new ArrayList<>())
                    .add(new DataPoint(Map.of("database", dbName), (double) size));
        }
        callCountUntilReport = (callCountUntilReport + 1) % COLLECT_EVERY;
    }
}

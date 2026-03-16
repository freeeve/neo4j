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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.common.Edition;
import org.neo4j.configuration.Config;
import org.neo4j.fleetmanagement.communication.model.DataPoint;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.kernel.impl.factory.DbmsInfo;

public class MetricsCollection {
    private final List<ICollector> collectors;

    public MetricsCollection(Config config, Configuration configuration, DbmsInfo dbmsInfo) {
        var collectors = new ArrayList<>(List.of(new CpuCollector(), new MemoryCollector()));
        if (Edition.COMMUNITY.equals(dbmsInfo.edition)) {
            collectors.add(new CommunityEditionNeo4jMetricsCollector(config));
        } else {
            collectors.add(new Neo4jMetricsCollector(config, configuration));
        }
        this.collectors = collectors;
    }

    public void start() {
        for (ICollector collector : collectors) {
            collector.start();
        }
    }

    public Map<String, List<DataPoint>> collect() {
        Map<String, List<DataPoint>> metricsData = new HashMap<>();
        for (ICollector collector : collectors) {
            collector.collect(metricsData);
        }

        return metricsData;
    }
}

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

package org.neo4j.fleetmanagement.procedures;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.neo4j.fleetmanagement.metrics.CpuCollector;
import org.neo4j.fleetmanagement.metrics.MemoryCollector;
import org.neo4j.fleetmanagement.metrics.model.MetricsDefinition;

public class MetricNamesSupplier implements Supplier<List<String>> {
    private static org.neo4j.fleetmanagement.configuration.Configuration configuration;

    public static void setConfiguration(org.neo4j.fleetmanagement.configuration.Configuration configuration) {
        MetricNamesSupplier.configuration = configuration;
    }

    @Override
    public List<String> get() {
        if (configuration == null
                || configuration.getMetrics() == null
                || configuration.getMetrics().isEmpty()) {
            return List.of();
        }
        List<String> neo4jMetrics = configuration.getMetrics().stream()
                .map(MetricsDefinition::getMetricName)
                .collect(Collectors.toList());
        neo4jMetrics.addAll(Arrays.stream(MemoryCollector.MetricNames.values())
                .map(m -> m.metricName)
                .collect(Collectors.toList()));
        neo4jMetrics.addAll(Arrays.stream(CpuCollector.MetricNames.values())
                .map(m -> m.metricName)
                .collect(Collectors.toList()));
        return neo4jMetrics;
    }
}

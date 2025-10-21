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

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.Map;
import org.neo4j.fleetmanagement.communication.model.DataPoint;

public class CpuCollector implements ICollector {

    public enum MetricNames {
        CPU_USAGE("fleet_management_cpu_usage"),
        CPU_CORE_COUNT("fleet_management_cpu_core_count");

        public final String metricName;

        MetricNames(String metricName) {
            this.metricName = metricName;
        }
    }

    private final OperatingSystemMXBean operatingSystemMXBean;

    public CpuCollector() {
        this.operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    }

    @Override
    public void collect(Map<String, List<DataPoint>> data) {
        var quotient = operatingSystemMXBean.getSystemLoadAverage();

        data.put(MetricNames.CPU_USAGE.metricName, List.of(new DataPoint(quotient)));
        data.put(
                MetricNames.CPU_CORE_COUNT.metricName,
                List.of(new DataPoint(Runtime.getRuntime().availableProcessors())));
    }
}

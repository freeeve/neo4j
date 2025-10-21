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
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.neo4j.fleetmanagement.communication.model.DataPoint;

public class MemoryCollector implements ICollector {

    public enum MetricNames {
        HEAP_USED("fleet_management_heap_used"),
        NON_HEAP_USED("fleet_management_non_heap_used"),
        TOTAL_HEAP("fleet_management_total_heap"),
        TOTAL_MEMORY("fleet_management_total_memory"),
        MEMORY_USED("fleet_management_memory_used");

        public final String metricName;

        MetricNames(String metricName) {
            this.metricName = metricName;
        }
    }

    private final MemoryMXBean memoryMXBean;
    private final MBeanServer mBeanServer;

    public MemoryCollector() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public void collect(Map<String, List<DataPoint>> data) {
        var heap = this.memoryMXBean.getHeapMemoryUsage().getUsed();
        var nonHeap = this.memoryMXBean.getNonHeapMemoryUsage().getUsed();
        var totalHeap = this.memoryMXBean.getHeapMemoryUsage().getMax();

        data.put(MetricNames.HEAP_USED.metricName, List.of(new DataPoint(heap)));
        data.put(MetricNames.NON_HEAP_USED.metricName, List.of(new DataPoint(nonHeap)));
        data.put(MetricNames.TOTAL_HEAP.metricName, List.of(new DataPoint(totalHeap)));

        var jvmMemory = Runtime.getRuntime().totalMemory();

        long totalMemory;
        try {
            var osBean = new ObjectName("java.lang:type=OperatingSystem");
            var attribute = mBeanServer.getAttribute(osBean, "TotalPhysicalMemorySize");
            totalMemory = Long.parseLong(attribute.toString());
        } catch (Exception e) {
            System.out.println("Error getting total physical memory: " + e.getMessage());
            totalMemory = jvmMemory;
        }

        var usedMemory = jvmMemory - Runtime.getRuntime().freeMemory();

        data.put(MetricNames.TOTAL_MEMORY.metricName, List.of(new DataPoint(totalMemory)));
        data.put(MetricNames.MEMORY_USED.metricName, List.of(new DataPoint(usedMemory)));
    }
}

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
package org.neo4j.fleetmanagement.logs.model;

import java.util.HashMap;
import java.util.Map;

public class SecurityLogsTimeSlice {
    private final Map<SecurityLogsDefaultAggregation.Key, SecurityLogsDefaultAggregation.Meta> securityLogs =
            new HashMap<>();
    private final long creationTime;

    public SecurityLogsTimeSlice() {
        creationTime = System.currentTimeMillis();
    }

    public void add(SecurityLog log) {
        var aggregationKey = SecurityLogsDefaultAggregation.Key.from(log);
        securityLogs
                .computeIfAbsent(aggregationKey, key -> new SecurityLogsDefaultAggregation.Meta())
                .aggregate(log);
    }

    public Map<SecurityLogsDefaultAggregation.Key, SecurityLogsDefaultAggregation.Meta> getLogs() {
        return securityLogs;
    }

    public long getCreationTime() {
        return creationTime;
    }
}

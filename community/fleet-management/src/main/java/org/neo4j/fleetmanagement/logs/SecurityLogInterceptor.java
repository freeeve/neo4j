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
package org.neo4j.fleetmanagement.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.neo4j.fleetmanagement.communication.SecurityLogsService;
import org.neo4j.fleetmanagement.logs.model.SecurityLog;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.logging.Level;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogMonitor;
import org.neo4j.logging.Neo4jLogMessage;
import org.neo4j.logging.log4j.Neo4jMapMessage;

public class SecurityLogInterceptor implements LogMonitor {
    private final Log userLog;
    private final SecurityLogsService securityLogsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityLogInterceptor(SecurityLogsService securityLogsService) {
        this.userLog = Logger.getNeo4jLogger();
        this.securityLogsService = securityLogsService;
    }

    @Override
    public void onLogMessage(Level level, Neo4jLogMessage message, Throwable throwable) {
        if (level == Level.DEBUG) {
            // don't capture DEBUG logs as they are mostly internal
            return;
        }

        try {
            var messageMap = (Neo4jMapMessage) message;
            String logJson = messageMap.asString("JSON");
            var log = objectMapper.readValue(logJson, SecurityLog.class);

            // add custom fields
            log.timestamp = Instant.now().toEpochMilli();
            log.level = level.name();

            this.securityLogsService.add(log);
        } catch (Exception e) {
            userLog.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}

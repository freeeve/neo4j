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
package org.neo4j.fleetmanagement.communication;

import java.time.Instant;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.fleetmanagement.bootstrap.FleetManagerTask;
import org.neo4j.fleetmanagement.communication.model.SecurityLogsReportMessage;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.configuration.ClusterSync;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.logs.model.SecurityLog;
import org.neo4j.fleetmanagement.logs.model.SecurityLogsDefaultAggregation;
import org.neo4j.fleetmanagement.logs.model.SecurityLogsTimeSlice;
import org.neo4j.fleetmanagement.topology.TopologyMapper;
import org.neo4j.fleetmanagement.transactions.ITransactor;

public class SecurityLogsService extends BufferedReportingService<SecurityLogsTimeSlice> {
    private final ServerIdentity serverIdentity;

    public SecurityLogsService(
            ITransactor transactor,
            Upstream upstream,
            State state,
            ServerIdentity serverIdentity,
            Configuration configuration) {
        super(transactor, upstream, state, configuration, new SecurityLogsTimeSlice());
        this.serverIdentity = serverIdentity;
    }

    public void add(SecurityLog log) {
        if (isBufferLimitReached()) {
            // drop logs until the current buffer is cleared to avoid extra memory consumption
            return;
        }

        getCurrent().add(log);
    }

    @Override
    public void report() {
        if (!this.state.isConnected()) {
            this.updateBufferSize();
        }

        var oldLogs = getPrevious(new SecurityLogsTimeSlice());
        if (oldLogs.getLogs() == null || oldLogs.getLogs().isEmpty()) {
            return;
        }

        var msg = new SecurityLogsReportMessage();
        msg.fromTimestamp = oldLogs.getCreationTime();
        msg.toTimestamp = Instant.now().toEpochMilli();
        msg.dbmsId = TopologyMapper.getDbmsId(transactor.getDatabases());
        msg.serverId = serverIdentity.serverId().uuid().toString();
        msg.projectId = upstream.getApiKey().projectId();
        msg.logs = oldLogs.getLogs().entrySet().stream()
                .map(entry -> new SecurityLogsDefaultAggregation.Value(entry.getKey(), entry.getValue()))
                .toList();

        transmitReport(msg, Upstream.Endpoint.SECURITY_LOGS);
        this.resetBuffer();
    }

    public static class SecurityLogsReportingTask extends FleetManagerTask {
        private final SecurityLogsService securityLogsService;

        public SecurityLogsReportingTask(
                State state, ClusterSync clusterSync, SecurityLogsService securityLogsService) {
            super(state, clusterSync);
            this.securityLogsService = securityLogsService;
        }

        @Override
        protected void execute() {
            if (this.state.isConnected()) {
                this.securityLogsService.report();
            }
        }
    }
}

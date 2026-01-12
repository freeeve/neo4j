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

import java.util.List;
import java.util.Map;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.fleetmanagement.bootstrap.FleetManagerTask;
import org.neo4j.fleetmanagement.communication.model.DataPoint;
import org.neo4j.fleetmanagement.communication.model.MetricsMessage;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.configuration.ClusterSync;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.metrics.MetricsCollection;
import org.neo4j.fleetmanagement.topology.TopologyMapper;
import org.neo4j.fleetmanagement.transactions.ITransactor;

public class MetricsService extends AbstractReportingService {
    private final ServerIdentity serverIdentity;

    private final MetricsCollection metricsCollection;

    public MetricsService(ITransactor transactor, ServerIdentity serverIdentity, Upstream upstream, Config config) {
        super(transactor, upstream);

        this.metricsCollection = new MetricsCollection(config);
        this.serverIdentity = serverIdentity;
    }

    public void report() {
        if (!this.state.isConnected()) {
            // Fleet manager is not connected - skip metrics collection
            return;
        }

        Map<String, List<DataPoint>> metricsData;
        try {
            metricsData = metricsCollection.collect();
        } catch (Exception e) {
            this.userLog.error("Fleet manager failed to report metrics - exception in metric collection", e);
            return;
        }

        MetricsMessage msg = new MetricsMessage();
        msg.metrics = metricsData;
        msg.dbmsId = TopologyMapper.getDbmsId(transactor.getDatabases());
        msg.serverId = serverIdentity.serverId().uuid().toString();
        msg.projectId = upstream.getApiKey().projectId();

        transmitReport(msg, Upstream.Endpoint.METRICS);
    }

    public static class MetricsReportingTask extends FleetManagerTask {
        private final MetricsService metricsService;

        public MetricsReportingTask(State state, ClusterSync clusterSync, MetricsService metricsService) {
            super(state, clusterSync);
            this.metricsService = metricsService;
        }

        protected void execute() {
            if (this.state.isConnected()) {
                this.metricsService.report();
            }
        }
    }
}

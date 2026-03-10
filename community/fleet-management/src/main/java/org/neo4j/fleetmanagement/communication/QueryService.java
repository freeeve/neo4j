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

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.fleetmanagement.bootstrap.FleetManagerTask;
import org.neo4j.fleetmanagement.communication.model.QueryReportMessage;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.configuration.ClusterSync;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.queries.model.AggregatedQueriesTimeSlice;
import org.neo4j.fleetmanagement.topology.TopologyMapper;
import org.neo4j.fleetmanagement.transactions.ITransactor;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.kernel.api.query.ExecutingQuery;
import org.neo4j.logging.Log;

public class QueryService extends AbstractReportingService {
    // Maximum number of missed reports before pausing log collection
    private static final int MAX_CACHED_REPORTS = 3;

    private final ServerIdentity serverIdentity;
    private final AtomicReference<AggregatedQueriesTimeSlice> current =
            new AtomicReference<>(new AggregatedQueriesTimeSlice());
    private final Log userLog;
    private int missedReports;

    public QueryService(
            ITransactor transactor,
            ServerIdentity serverIdentity,
            Upstream upstream,
            State state,
            Config config,
            Configuration configuration) {
        super(transactor, upstream, state, configuration);
        this.serverIdentity = serverIdentity;
        this.userLog = Logger.getNeo4jLogger();
        missedReports = 0;
    }

    public void add(ExecutingQuery query, ErrorGqlStatusObject errorGqlStatusObject) {
        if (missedReports < MAX_CACHED_REPORTS) {
            current.get().add(query, errorGqlStatusObject);
        }
    }

    public AggregatedQueriesTimeSlice getCurrent() {
        return current.get();
    }

    @Override
    public void report() {
        if (!this.state.isConnected()) {
            missedReports++;
        }

        var oldQueries = current.getAndSet(new AggregatedQueriesTimeSlice());
        if (oldQueries.getAggregations().isEmpty()) {
            return;
        }

        var msg = new QueryReportMessage(oldQueries.getCreationTime());
        msg.dbmsId = TopologyMapper.getDbmsId(transactor.getDatabases());
        msg.serverId = serverIdentity.serverId().uuid().toString();
        msg.projectId = upstream.getApiKey().projectId();
        msg.queries = oldQueries.getAggregations().entrySet().stream()
                .map(entry -> new QueryReportMessage.AggregatedQueryData(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        transmitReport(msg, Upstream.Endpoint.QUERIES);
        missedReports = 0;
    }

    public static class QueryReportingTask extends FleetManagerTask {
        private final QueryService queryService;

        public QueryReportingTask(State state, ClusterSync clusterSync, QueryService queryService) {
            super(state, clusterSync);
            this.queryService = queryService;
        }

        @Override
        protected void execute() {
            if (this.state.isConnected()) {
                this.queryService.report();
            }
        }
    }
}

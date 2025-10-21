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

import static org.neo4j.fleetmanagement.configuration.State.TOPOLOGY_INITIALIZED;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.fleetmanagement.communication.model.Neo4jConfigMessage;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.topology.Neo4jConfigMapper;
import org.neo4j.fleetmanagement.topology.TopologyMapper;
import org.neo4j.fleetmanagement.transactions.ITransactor;

public class ConfigService extends AbstractReportingService implements PropertyChangeListener {

    private final Neo4jConfigMapper neo4jConfigMapper;
    private final ITransactor transactor;
    private final ServerIdentity serverIdentity;

    public ConfigService(ITransactor transactor, ServerIdentity serverIdentity, Upstream upstream, Config config) {
        super(transactor, upstream);
        this.neo4jConfigMapper = new Neo4jConfigMapper(config);
        this.transactor = transactor;
        this.serverIdentity = serverIdentity;
        var state = State.getInstance();
        state.addPropertyChangeListener(this);
    }

    @Override
    public void report() {
        var configMessage = new Neo4jConfigMessage();
        configMessage.neo4jConfig = neo4jConfigMapper.mapConfig();
        configMessage.dbmsId = TopologyMapper.getDbmsId(transactor.getDatabases());
        configMessage.serverId = serverIdentity.serverId().uuid().toString();
        configMessage.projectId = upstream.getApiKey().projectId();

        transmitReport(configMessage, Upstream.Endpoint.CONFIG);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(TOPOLOGY_INITIALIZED)) {
            var isTopologyInitialized = (Boolean) evt.getNewValue();
            if (isTopologyInitialized) {
                this.report();
            }
        }
    }
}

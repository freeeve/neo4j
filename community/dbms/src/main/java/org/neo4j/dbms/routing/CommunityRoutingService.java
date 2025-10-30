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
package org.neo4j.dbms.routing;

import static org.neo4j.dbms.routing.RoutingTableServiceHelpers.ensureBoltAddressIsUsable;

import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.database.DatabaseContextProvider;
import org.neo4j.kernel.database.DefaultDatabaseResolver;
import org.neo4j.values.virtual.MapValue;

public class CommunityRoutingService implements RoutingService {
    private final DatabaseContextProvider<?> databaseContextProvider;
    private final DefaultDatabaseResolver defaultDatabaseResolver;
    private final ConnectorPortRegister portRegister;

    private final boolean boltDisabled;
    private final long ttl;
    private final SocketAddress localAdvertisedAddress;

    public CommunityRoutingService(
            DatabaseContextProvider<?> databaseContextProvider,
            DefaultDatabaseResolver defaultDatabaseResolver,
            ConnectorPortRegister portRegister,
            Config config) {
        this.databaseContextProvider = databaseContextProvider;
        this.defaultDatabaseResolver = defaultDatabaseResolver;
        this.portRegister = portRegister;

        this.boltDisabled = !config.get(BoltConnector.enabled);
        this.ttl = config.get(GraphDatabaseSettings.routing_ttl).toMillis();
        this.localAdvertisedAddress = config.get(BoltConnector.advertised_address);
    }

    @Override
    public RoutingResult route(String databaseName, String user, MapValue routingContext, boolean isDefaultDatabase)
            throws RoutingException {
        if (databaseName == null || databaseName.isEmpty() || databaseName.isBlank()) {
            databaseName = defaultDatabaseResolver.defaultDatabase(user);
        }
        var context = databaseContextProvider.getDatabaseContext(databaseName);
        if (context.isEmpty()) {
            throw RoutingTableServiceHelpers.databaseNotFoundException(databaseName);
        }
        if (!context.get().database().getDatabaseAvailabilityGuard().isAvailable()) {
            throw RoutingException.routingTableForUnavailableDb(databaseName);
        }
        if (boltDisabled) {
            throw RoutingException.boltNotEnabled(databaseName);
        }
        var singleAddress = ensureBoltAddressIsUsable(routingContext, portRegister, localAdvertisedAddress);
        return RoutingResult.single(singleAddress, ttl);
    }
}

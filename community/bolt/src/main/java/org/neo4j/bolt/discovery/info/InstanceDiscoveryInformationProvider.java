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
package org.neo4j.bolt.discovery.info;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.kernel.impl.factory.DbmsInfo;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.internal.Version;
import org.neo4j.storageengine.api.StoreIdProvider;
import org.neo4j.storageengine.util.StoreIdDecodeUtils;

public class InstanceDiscoveryInformationProvider implements DiscoveryInformationProvider {

    private final DatabaseManagementService databaseManagementService;
    private final DbmsInfo dbmsInfo;
    private final ServerIdentity identityModule;
    private final SocketAddress advertisedAddress;

    public InstanceDiscoveryInformationProvider(
            DatabaseManagementService databaseManagementService,
            DbmsInfo dbmsInfo,
            ServerIdentity identityModule,
            SocketAddress advertisedAddress) {
        this.databaseManagementService = databaseManagementService;
        this.dbmsInfo = dbmsInfo;
        this.identityModule = identityModule;
        this.advertisedAddress = advertisedAddress;
    }

    @Override
    public DiscoveryInformation getCurrentInfo() {
        var database =
                (GraphDatabaseAPI) this.databaseManagementService.database(GraphDatabaseSettings.SYSTEM_DATABASE_NAME);
        var storeIdProvider = database.getDependencyResolver().resolveDependency(StoreIdProvider.class);

        var dbmsId = StoreIdDecodeUtils.decodeId(storeIdProvider);
        var serverId = this.identityModule.serverId().uuid().toString();

        var hostString = this.advertisedAddress.getHostname();
        var advertisedAddress = new StringBuilder();
        if (hostString != null) {
            advertisedAddress.append(hostString);
        }
        advertisedAddress.append(":");
        advertisedAddress.append(this.advertisedAddress.getPort());

        return new DiscoveryInformation(
                dbmsId, serverId, Version.getNeo4jVersion(), this.dbmsInfo.edition, advertisedAddress.toString());
    }
}

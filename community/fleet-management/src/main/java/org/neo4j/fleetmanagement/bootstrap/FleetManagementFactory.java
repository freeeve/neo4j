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
package org.neo4j.fleetmanagement.bootstrap;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.extension.ExtensionType;
import org.neo4j.kernel.extension.context.ExtensionContext;
import org.neo4j.kernel.impl.factory.DbmsInfo;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.logging.LogProvider;
import org.neo4j.logging.internal.LogService;
import org.neo4j.monitoring.Monitors;

@ServiceProvider
public class FleetManagementFactory extends ExtensionFactory<FleetManagementFactory.Dependencies> {
    public FleetManagementFactory() {
        super(ExtensionType.GLOBAL, "FleetManagement");
    }

    @Override
    public Lifecycle newInstance(ExtensionContext context, Dependencies dependencies) {
        return new FleetManagement(
                dependencies.logService(),
                dependencies.databaseManagementService(),
                dependencies.config(),
                dependencies.dbmsInfo(),
                dependencies.fs(),
                dependencies.serverIdentity(),
                dependencies.monitoring(),
                dependencies.state());
    }

    public interface Dependencies {
        Config config();

        DatabaseManagementService databaseManagementService();

        LogService logService();

        LogProvider logProvider();

        FileSystemAbstraction fileSystem();

        DbmsInfo dbmsInfo();

        FileSystemAbstraction fs();

        ServerIdentity serverIdentity();

        Monitors monitoring();

        State state();
    }
}

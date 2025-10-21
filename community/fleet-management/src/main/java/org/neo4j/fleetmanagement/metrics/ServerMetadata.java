package org.neo4j.fleetmanagement.metrics;

import org.neo4j.fleetmanagement.communication.model.ReportingMessage;
import org.neo4j.fleetmanagement.utils.FleetManagerVersion;

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
public class ServerMetadata {
    private static ServerMetadata instance;

    private final String fleetManagerVersion;
    private final String osName;
    private final String osVersion;
    private final String osArch;
    private final String jvmVersion;
    private final String jvmVendor;

    public static synchronized ServerMetadata getInstance() {
        if (instance == null) {
            instance = new ServerMetadata();
        }
        return instance;
    }

    private ServerMetadata() {
        this.osName = System.getProperty("os.name");
        this.osVersion = System.getProperty("os.version");
        this.osArch = System.getProperty("os.arch");
        this.jvmVersion = System.getProperty("java.version");
        this.jvmVendor = System.getProperty("java.vendor");
        this.fleetManagerVersion = FleetManagerVersion.getFleetManagerVersion();
    }

    public void populateStaticInfo(ReportingMessage message) {
        message.pluginVersion = this.fleetManagerVersion;
        message.osName = this.osName;
        message.osVersion = this.osVersion;
        message.osArch = this.osArch;
        message.jvmVersion = this.jvmVersion;
        message.jvmVendor = this.jvmVendor;
    }
}

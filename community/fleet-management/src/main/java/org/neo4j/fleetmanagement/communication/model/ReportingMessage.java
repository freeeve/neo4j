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
package org.neo4j.fleetmanagement.communication.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import org.neo4j.fleetmanagement.topology.model.Dbms;

@JsonClassDescription("Message sent from the server to the Fleet Management service containing reporting data.")
public class ReportingMessage {
    @JsonPropertyDescription("Unix timestamp in milliseconds when the message was created")
    public long timestamp;

    @JsonPropertyDescription("Identifier for the project")
    public String projectId;

    @JsonPropertyDescription("DBMS information object")
    public Dbms dbms;

    @JsonPropertyDescription("Version of the fleet manager")
    public String pluginVersion;

    @JsonPropertyDescription("Operating system name")
    public String osName;

    @JsonPropertyDescription("Operating system version")
    public String osVersion;

    @JsonPropertyDescription("Operating system architecture")
    public String osArch;

    @JsonPropertyDescription("Java Virtual Machine version")
    public String jvmVersion;

    @JsonPropertyDescription("Java Virtual Machine vendor")
    public String jvmVendor;

    public ReportingMessage() {
        this.timestamp = Instant.now().toEpochMilli();
    }
}

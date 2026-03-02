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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import org.neo4j.fleetmanagement.common.ValuesDocumentation;
import org.neo4j.fleetmanagement.procedures.Neo4jConfigNamesSupplier;

@JsonClassDescription("Message sent from the server to the Fleet Manager service containing neo4j configuration data.")
public class Neo4jConfigMessage {
    @JsonPropertyDescription("Unix timestamp in milliseconds when the message was created")
    public long timestamp;

    @JsonProperty("project_id")
    @JsonPropertyDescription("Identifier for the project")
    public String projectId;

    @JsonProperty("dbms_id")
    @JsonPropertyDescription("Unique identifier for the DBMS")
    public String dbmsId;

    @JsonProperty("server_id")
    @JsonPropertyDescription("Unique identifier for the server")
    public String serverId;

    @JsonProperty("neo4j_config")
    @JsonPropertyDescription(
            "Map of neo4j config names to config values. Values are dynamically populated from the Fleet Management server.")
    @ValuesDocumentation(valueSupplier = Neo4jConfigNamesSupplier.class)
    public Map<String, Object> neo4jConfig;

    public Neo4jConfigMessage() {
        this.timestamp = System.currentTimeMillis();
    }
}

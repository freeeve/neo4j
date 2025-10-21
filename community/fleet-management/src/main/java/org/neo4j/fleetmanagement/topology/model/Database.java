package org.neo4j.fleetmanagement.topology.model;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public class Database {

    @JsonPropertyDescription("Name of the database")
    public String name;

    @JsonPropertyDescription("Role of the database in the cluster")
    public String role;

    @JsonPropertyDescription("Type of the database in the cluster")
    public String type;

    @JsonPropertyDescription("List of database aliases")
    public List<String> aliases;

    @JsonPropertyDescription("Access level of the database")
    public String access;

    @JsonPropertyDescription("Unique identifier for the database")
    public String databaseId;

    @JsonPropertyDescription("Requested status of the database")
    public String requestedStatus;

    @JsonPropertyDescription("Current status of the database")
    public String currentStatus;

    @JsonPropertyDescription("Status message describing the current state")
    public String statusMessage;

    @JsonProperty("default")
    @JsonPropertyDescription("Whether this is the default database")
    public boolean _default;

    @JsonPropertyDescription("Whether this is the home database")
    public boolean home;

    @JsonPropertyDescription("Current number of primaries for this database")
    public Integer currentPrimariesCount;

    @JsonPropertyDescription("Current number of secondaries for this database")
    public Integer currentSecondariesCount;

    @JsonPropertyDescription("Requested number of primaries for this database")
    public Integer requestedPrimariesCount;

    @JsonPropertyDescription("Requested number of secondaries for this database")
    public Integer requestedSecondariesCount;

    @JsonPropertyDescription("Timestamp when the database was created")
    public long creationTime;

    @JsonPropertyDescription("Timestamp when the database was last started")
    public long lastStartTime;

    @JsonPropertyDescription("Store format of the database")
    public String store;

    @JsonPropertyDescription("Whether this instance is a writer for this database")
    public boolean writer;

    @JsonPropertyDescription("Last committed transaction ID")
    public Integer lastCommittedTxn;

    @JsonPropertyDescription("Current replication lag")
    public Integer replicationLag;

    @JsonPropertyDescription("Current graph counts for the database")
    public GraphCount graphCount;

    @JsonPropertyDescription("List of graph shards of this database")
    public List<String> graphShards;

    @JsonPropertyDescription("List of property shards of this database")
    public List<String> propertyShards;

    @JsonIgnore
    public boolean isComposite() {
        return "composite".equalsIgnoreCase(type);
    }
}

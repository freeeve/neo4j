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

package org.neo4j.fleetmanagement.topology.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public class Dbms {

    @JsonPropertyDescription("Unique identifier for the DBMS")
    public String dbmsId;

    @JsonPropertyDescription("Unique identifier for the server")
    public String serverId;

    @JsonPropertyDescription("List of database names")
    public List<String> databases;

    @JsonPropertyDescription("Edition of the DBMS (e.g., Enterprise, Community)")
    public String edition;

    @JsonPropertyDescription("Packaging of DBMS installation")
    public String packaging;

    @JsonPropertyDescription("List of servers in the DBMS cluster")
    public List<Server> servers;
}

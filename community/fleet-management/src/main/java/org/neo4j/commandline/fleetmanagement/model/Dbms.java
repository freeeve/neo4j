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
package org.neo4j.commandline.fleetmanagement.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Model class representing a DBMS
 */
public class Dbms {
    private List<String> addresses;
    private String dbmsId;
    private ArrayList<Server> servers;

    public Dbms(String dbmsId) {
        super();
        this.dbmsId = dbmsId;

        addresses = new ArrayList<>();
        servers = new ArrayList<>();
    }

    public List<String> getAddresses() {
        if (addresses.isEmpty()) {
            addresses = this.servers.stream()
                    .map(Server::getAdvertisedAddress)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return addresses;
    }

    public String getDbmsId() {
        return dbmsId;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void addServer(Server server) {
        this.servers.add(server);
    }

    public int size() {
        return this.servers.size();
    }
}

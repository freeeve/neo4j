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

import java.util.Collection;
import java.util.HashMap;

/**
 * Model class representing multiple DBMSs
 */
public class Dbmss extends HashMap<String, Dbms> {

    // Create a list of deduplicated DBMSs from servers
    public static Dbmss fromServers(Collection<Server> servers) {
        var d = new Dbmss();
        for (Server s : servers) {
            d.computeIfAbsent(s.getDbmsId(), dbmsId -> new Dbms(s.getDbmsId())).addServer(s);
        }
        return d;
    }

    public int serverCount() {
        return this.values().stream().mapToInt(Dbms::size).sum();
    }
}

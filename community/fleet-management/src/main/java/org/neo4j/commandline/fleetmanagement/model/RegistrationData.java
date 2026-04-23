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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Model class representing registration data
 */
public class RegistrationData {
    @JsonProperty("dbmsId")
    private final String dbmsId;

    @JsonProperty("serverCount")
    private final int serverCount;

    @JsonProperty("addresses")
    private final List<String> addresses;

    public RegistrationData(String dbmsId, int serverCount, List<String> addresses) {
        this.dbmsId = dbmsId;
        this.serverCount = serverCount;
        this.addresses = addresses;
    }

    public String getDbmsId() {
        return dbmsId;
    }

    public int getServerCount() {
        return serverCount;
    }

    public List<String> getAddresses() {
        return addresses;
    }
}

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

public class Server {

    @JsonPropertyDescription("Unique identifier for the server")
    public String serverId;

    @JsonPropertyDescription("Name of the server")
    public String name;

    @JsonPropertyDescription("Network address of the server")
    public String address;

    @JsonPropertyDescription("Current health status of the server")
    public String health;

    @JsonPropertyDescription("Current state of the server")
    public String state;

    @JsonPropertyDescription("Mode constraint of the server")
    public String modeConstraint;

    @JsonPropertyDescription("Neo4j version of the server")
    public String version;

    @JsonPropertyDescription("List of databases hosted on this server")
    public List<Database> databases;

    @JsonPropertyDescription("Main license information")
    public License license;

    @JsonPropertyDescription("Bloom license information")
    public License bloomLicense;

    @JsonPropertyDescription("GDS license information")
    public License gdsLicense;

    @JsonPropertyDescription("List of plugins installed on the server")
    public List<Plugin> plugins;

    public static class License {

        public enum LicenseType {
            FREE,
            COMMERCIAL,
            EVALUATION,
            UNSUPPORTED,
        }

        public enum LicenseState {
            VALID,
            EXPIRED,
            NOT_ACCEPTED,
            INVALID,
            UNKNOWN
        }

        @JsonPropertyDescription("Type of the license")
        public LicenseType type;

        @JsonPropertyDescription("Current state of the license")
        public LicenseState state;

        @JsonPropertyDescription("Number of days remaining in trial period")
        public Integer daysLeftOnTrial;

        @JsonPropertyDescription("Total number of days in trial period")
        public Integer totalTrialDays;
    }

    public static class Plugin {
        @JsonPropertyDescription("Filename of the plugin")
        public String filename;

        @JsonPropertyDescription("Manifest name of the plugin, if available")
        public String name;

        @JsonPropertyDescription("Manifest version of the plugin, if available")
        public String version;

        @JsonPropertyDescription("Manifest vendor of the plugin, if available")
        public String vendor;
    }
}

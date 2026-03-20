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
package org.neo4j.fleetmanagement.logs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityLog {
    @JsonPropertyDescription("Timestamp of the security log event")
    public long timestamp;

    @JsonPropertyDescription("Log event main message")
    public String message;

    @JsonPropertyDescription("Connection information for the log event source")
    public String source;

    @JsonPropertyDescription("Severity level of the log event")
    public String level;

    @JsonPropertyDescription("Database in context of the log event occurred")
    public String database;

    @JsonPropertyDescription("User associated with the execution of the process that caused the log event")
    public String executingUser;

    @JsonPropertyDescription(
            "User authenticated to the Database for execution of the process that caused the log event")
    public String authenticatedUser;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SecurityLog{");
        sb.append("timestamp=").append(timestamp);
        sb.append(", message='").append(message).append('\'');
        sb.append(", source='").append(source).append('\'');
        sb.append(", level='").append(level).append('\'');
        sb.append(", database='").append(database).append('\'');
        sb.append(", executingUser='").append(executingUser).append('\'');
        sb.append(", authenticatedUser='").append(authenticatedUser);
        sb.append('}');
        return sb.toString();
    }
}

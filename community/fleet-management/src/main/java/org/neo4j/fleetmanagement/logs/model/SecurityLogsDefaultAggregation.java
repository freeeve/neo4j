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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class SecurityLogsDefaultAggregation {
    public static class Key {
        @JsonPropertyDescription("Log event main message")
        public String message;

        @JsonPropertyDescription("Severity level of the log event")
        public String level;

        @JsonPropertyDescription("User associated with the execution of the process that caused the log event")
        public String executingUser;

        public static Key from(SecurityLog log) {
            var aggregationKey = new Key();
            aggregationKey.message = log.message;
            aggregationKey.level = log.level;
            aggregationKey.executingUser = log.executingUser;
            return aggregationKey;
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, level, executingUser);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return Objects.equals(message, key.message)
                    && Objects.equals(level, key.level)
                    && Objects.equals(executingUser, key.executingUser);
        }

        @Override
        public String toString() {
            return String.format("Key{message='%s', level='%s', executingUser='%s'}", message, level, executingUser);
        }
    }

    public static class Meta {
        @JsonPropertyDescription("Security logs count for unique set of log fields")
        public int count;

        @JsonPropertyDescription("Databases in context of the log event occurred")
        public Set<String> databases = new HashSet<>();

        @JsonPropertyDescription(
                "Users authenticated to the Database for execution of the process that caused the log event")
        public Set<String> authenticatedUsers = new HashSet<>();

        @JsonPropertyDescription("Connection information for the log event sources")
        public Set<String> sources = new HashSet<>();

        public void aggregate(SecurityLog log) {
            if (log == null) {
                return;
            }

            count++;

            if (log.database != null) {
                databases.add(log.database);
            }
            authenticatedUsers.add(Objects.requireNonNullElse(log.authenticatedUser, "anonymous"));
            if (log.source != null) {
                sources.add(log.source);
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Meta{");
            sb.append("count=").append(count);
            sb.append(", databases=").append(databases);
            sb.append(", authenticatedUsers=").append(authenticatedUsers);
            sb.append(", sources=").append(sources);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class Value extends Key {
        @JsonPropertyDescription("Aggregated metadata for the log event")
        public Meta meta;

        public Value(Key key, Meta meta) {
            this.message = key.message;
            this.level = key.level;
            this.executingUser = key.executingUser;
            this.meta = meta;
        }
    }
}

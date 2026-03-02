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
import java.time.Instant;
import java.util.List;
import org.neo4j.fleetmanagement.queries.model.QueryAggregationMeta;
import org.neo4j.fleetmanagement.queries.model.SimplifiedGqlError;
import org.neo4j.fleetmanagement.queries.model.UniqueKey;

@JsonClassDescription(
        "Message sent from the server to the Fleet Manager service containing aggregated query data. Only sent if query logging is enabled explicitly through Aura Console.")
public class QueryReportMessage {
    @JsonPropertyDescription("Unix timestamp in milliseconds from the first query in the set")
    public long fromTimestamp;

    @JsonPropertyDescription("Unix timestamp in milliseconds to the last query in the set")
    public long toTimestamp;

    @JsonProperty("project_id")
    @JsonPropertyDescription("Identifier for the project")
    public String projectId;

    @JsonProperty("dbms_id")
    @JsonPropertyDescription("Unique identifier for the DBMS")
    public String dbmsId;

    @JsonProperty("server_id")
    @JsonPropertyDescription("Unique identifier for the server")
    public String serverId;

    @JsonPropertyDescription("Aggregated query data")
    public List<AggregatedQueryData> queries;

    public QueryReportMessage(long fromTimestamp) {
        this.fromTimestamp = fromTimestamp;
        this.toTimestamp = Instant.now().toEpochMilli();
    }

    @JsonClassDescription("A specific query in the set")
    public static class AggregatedQueryData {
        @JsonPropertyDescription("Obfuscated query text")
        public String query;

        @JsonPropertyDescription("Whether the query was successful")
        public boolean success;

        @JsonPropertyDescription("GQL error object (if query failed)")
        public SimplifiedGqlError gqlError;

        @JsonPropertyDescription("Language of the query")
        public String queryLanguage;

        @JsonPropertyDescription("Metadata representing the aggregated executions of this query")
        public QueryAggregationMeta meta;

        public AggregatedQueryData(UniqueKey key, QueryAggregationMeta meta) {
            this.query = key.getQueryText();
            this.success = key.getGqlError() == null;
            this.gqlError = key.getGqlError();
            this.queryLanguage = key.getQueryLanguage();
            this.meta = meta;
        }
    }
}

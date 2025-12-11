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
package org.neo4j.queryapi.jsonl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.queryapi.QueryApiTestUtil.setupLogging;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.configuration.connectors.ConnectorType;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.queryapi.QueryApiTestUtil;
import org.neo4j.queryapi.QueryResponseJsonlAssertions;
import org.neo4j.queryapi.testclient.QueryAPITestClient;
import org.neo4j.queryapi.testclient.QueryContentType;
import org.neo4j.queryapi.testclient.QueryRequest;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

class QueryResourceQueryStatsJsonlIT {

    private static DatabaseManagementService dbms;
    private static QueryAPITestClient testClient;

    @BeforeAll
    static void beforeAll() {
        setupLogging();
        var builder = new TestDatabaseManagementServiceBuilder();
        dbms = builder.setConfig(HttpConnector.enabled, true)
                .setConfig(HttpConnector.listen_address, new SocketAddress("localhost", 0))
                .setConfig(
                        BoltConnectorInternalSettings.local_channel_address,
                        QueryResourceQueryStatsJsonlIT.class.getSimpleName())
                .setConfig(BoltConnector.enabled, true)
                .impermanent()
                .build();
        var portRegister = QueryApiTestUtil.resolveDependency(dbms, ConnectorPortRegister.class);
        var queryEndpoint =
                "http://" + portRegister.getLocalAddress(ConnectorType.HTTP) + "/db/{databaseName}/query/v2";
        testClient =
                new QueryAPITestClient(queryEndpoint, QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));
    }

    @AfterAll
    static void teardown() {
        dbms.shutdown();
    }

    @Test
    void shouldIncludeQueryStats() throws IOException, InterruptedException {
        var response = testClient.autoCommitJsonl(QueryRequest.newBuilder()
                .statement("RETURN 1")
                .includeCounters()
                .build());

        QueryResponseJsonlAssertions.assertThat(response)
                .isTransferEncodingChunked()
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesNRecords(1)
                .receivesSummary(summary -> summary.hasCounters(counters -> {
                    assertThat(counters.size()).isEqualTo(14);

                    assertThat(counters.get("containsUpdates").asBoolean()).isEqualTo(false);
                    assertThat(counters.get("containsSystemUpdates").asBoolean())
                            .isEqualTo(false);
                    assertThat(counters.get("nodesCreated").asInt()).isEqualTo(0);
                    assertThat(counters.get("nodesDeleted").asInt()).isEqualTo(0);
                    assertThat(counters.get("propertiesSet").asInt()).isEqualTo(0);
                    assertThat(counters.get("relationshipsCreated").asInt()).isEqualTo(0);
                    assertThat(counters.get("relationshipsDeleted").asInt()).isEqualTo(0);
                    assertThat(counters.get("labelsAdded").asInt()).isEqualTo(0);
                    assertThat(counters.get("labelsRemoved").asInt()).isEqualTo(0);
                    assertThat(counters.get("indexesAdded").asInt()).isEqualTo(0);
                    assertThat(counters.get("indexesRemoved").asInt()).isEqualTo(0);
                    assertThat(counters.get("constraintsAdded").asInt()).isEqualTo(0);
                    assertThat(counters.get("constraintsRemoved").asInt()).isEqualTo(0);
                    assertThat(counters.get("systemUpdates").asInt()).isEqualTo(0);
                }))
                .hasNoRemainingEvents();
    }

    @Test
    void shouldNotIncludeQueryStats() throws IOException, InterruptedException {
        var response = testClient.autoCommitJsonl(QueryRequest.newBuilder()
                .statement("RETURN 1")
                .withoutCounters()
                .build());

        QueryResponseJsonlAssertions.assertThat(response)
                .isTransferEncodingChunked()
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesNRecords(1)
                .receivesSummary(QueryResponseJsonlAssertions.SummaryAssertions::doesNotHaveCounters)
                .hasNoRemainingEvents();
    }

    @Test
    void shouldNotIncludeQueryStatsByDefault() throws IOException, InterruptedException {
        var response = testClient.autoCommitJsonl(QueryRequest.returnOne());

        QueryResponseJsonlAssertions.assertThat(response)
                .isTransferEncodingChunked()
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesNRecords(1)
                .receivesSummary(QueryResponseJsonlAssertions.SummaryAssertions::doesNotHaveCounters)
                .hasNoRemainingEvents();
    }

    @Test
    void shouldErrorIfInvalidInput() throws IOException, InterruptedException {
        var response = testClient.sendRawJsonl("{\"statement\": \"RETURN 1\", " + "\"includeCounters\": \"banana\"}");

        QueryResponseJsonlAssertions.assertThat(response)
                .hasStatus(400)
                .receivesError(Status.Request.Invalid)
                .hasNoRemainingEvents();
    }
}

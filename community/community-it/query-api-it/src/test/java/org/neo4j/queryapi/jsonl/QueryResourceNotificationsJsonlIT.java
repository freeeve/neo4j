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
import org.neo4j.notifications.NotificationCodeWithDescription;
import org.neo4j.queryapi.QueryApiTestUtil;
import org.neo4j.queryapi.QueryResponseJsonlAssertions;
import org.neo4j.queryapi.testclient.QueryAPITestClient;
import org.neo4j.queryapi.testclient.QueryContentType;
import org.neo4j.queryapi.testclient.QueryRequest;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

class QueryResourceNotificationsJsonlIT {

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
                        QueryResourceNotificationsJsonlIT.class.getSimpleName())
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
    void shouldReturnLabelDoesNotExistNotification() throws IOException, InterruptedException {
        var response = testClient.autoCommitJsonl(QueryRequest.newBuilder()
                .statement("MATCH (n:thisLabelDoesNotExist) return n")
                .build());

        QueryResponseJsonlAssertions.assertThat(response)
                .isTransferEncodingChunked()
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesSummary(assertions -> assertions.hasNotifications(notificationsJson -> {
                    assertThat(notificationsJson.size()).isEqualTo(1);
                    assertThat(notificationsJson.getFirst().get("code").asText())
                            .isEqualTo(NotificationCodeWithDescription.MISSING_LABEL
                                    .getStatus()
                                    .code()
                                    .serialize());
                    assertThat(notificationsJson.getFirst().get("title").asText())
                            .isEqualTo(NotificationCodeWithDescription.MISSING_LABEL
                                    .getStatus()
                                    .code()
                                    .description());
                    assertThat(notificationsJson.getFirst().get("description").asText())
                            .isEqualTo(
                                    "One of the labels in your query is not available in the database, make sure you didn't misspell it or that the label is available when you run this statement in your application (the missing label name is: thisLabelDoesNotExist)");
                    assertThat(notificationsJson
                                    .getFirst()
                                    .get("position")
                                    .get("offset")
                                    .asInt())
                            .isEqualTo(9);
                    assertThat(notificationsJson
                                    .getFirst()
                                    .get("position")
                                    .get("line")
                                    .asInt())
                            .isEqualTo(1);
                    assertThat(notificationsJson
                                    .getFirst()
                                    .get("position")
                                    .get("column")
                                    .asInt())
                            .isEqualTo(10);
                    assertThat(notificationsJson.getFirst().get("severity").asText())
                            .isEqualTo("WARNING");
                    assertThat(notificationsJson.getFirst().get("category").asText())
                            .isEqualTo("UNRECOGNIZED");
                }))
                .hasNoRemainingEvents();
    }

    @Test
    void shouldReturnMultipleNotifications() throws IOException, InterruptedException {
        var response = testClient.autoCommitJsonl(QueryRequest.newBuilder()
                .statement("MATCH (n:thisLabelDoesNotExist), (m:thisLabelDoesNotExist) return m, n")
                .build());

        QueryResponseJsonlAssertions.assertThat(response)
                .isTransferEncodingChunked()
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesSummary(assertions -> assertions.hasNotifications(notificationsJson -> {
                    assertThat(notificationsJson.size()).isEqualTo(3);
                    assertThat(notificationsJson.get(0).get("code").asText())
                            .isEqualTo(
                                    Status.Statement.UnknownLabelWarning.code().serialize());
                    assertThat(notificationsJson.get(1).get("code").asText())
                            .isEqualTo(
                                    Status.Statement.UnknownLabelWarning.code().serialize());
                    assertThat(notificationsJson.get(2).get("code").asText())
                            .isEqualTo(Status.Statement.CartesianProduct.code().serialize());
                }))
                .hasNoRemainingEvents();
    }

    @Test
    void shouldNotReturnNotificationsIfNonePresent() throws IOException, InterruptedException {
        var response = testClient.autoCommitJsonl(QueryRequest.returnOne());

        QueryResponseJsonlAssertions.assertThat(response)
                .isTransferEncodingChunked()
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesNRecords(1)
                .receivesSummary(QueryResponseJsonlAssertions.SummaryAssertions::doesNotHaveNotifications)
                .hasNoRemainingEvents();
    }
}

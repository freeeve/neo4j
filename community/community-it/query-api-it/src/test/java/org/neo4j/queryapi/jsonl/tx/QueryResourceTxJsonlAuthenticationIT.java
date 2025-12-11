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
package org.neo4j.queryapi.jsonl.tx;

import static org.neo4j.queryapi.QueryApiTestUtil.resolveDependency;
import static org.neo4j.queryapi.QueryApiTestUtil.setupLogging;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.neo4j.configuration.GraphDatabaseSettings;
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
import org.neo4j.queryapi.assertions.Capture;
import org.neo4j.queryapi.testclient.QueryAPITestClient;
import org.neo4j.queryapi.testclient.QueryApiTestClientException;
import org.neo4j.queryapi.testclient.QueryContentType;
import org.neo4j.queryapi.testclient.QueryRequest;
import org.neo4j.queryapi.tx.QueryResourceTxIT;
import org.neo4j.server.queryapi.tx.TransactionManager;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

class QueryResourceTxJsonlAuthenticationIT {

    private static QueryAPITestClient testClient;
    private static DatabaseManagementService dbms;
    private static TransactionManager txManager;
    private static String endpoint;

    @BeforeEach
    void beforeAll() {
        setupLogging();
        var builder = new TestDatabaseManagementServiceBuilder();
        dbms = builder.setConfig(HttpConnector.enabled, true)
                .setConfig(HttpConnector.listen_address, new SocketAddress("localhost", 0))
                .setConfig(BoltConnectorInternalSettings.local_channel_address, QueryResourceTxIT.class.getSimpleName())
                .setConfig(BoltConnector.enabled, true)
                .setConfig(BoltConnectorInternalSettings.enable_local_connector, true)
                .setConfig(GraphDatabaseSettings.auth_enabled, true)
                .impermanent()
                .build();

        txManager = resolveDependency(dbms, TransactionManager.class);
        var portRegister = QueryApiTestUtil.resolveDependency(dbms, ConnectorPortRegister.class);
        endpoint = "http://" + portRegister.getLocalAddress(ConnectorType.HTTP) + "/db/{databaseName}/query/v2";
        testClient = new QueryAPITestClient(
                endpoint, "neo4j", "neo4j", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));
    }

    @AfterEach
    void cleanUp() {
        dbms.shutdown();
    }

    @AfterEach
    void afterEach() {
        Assertions.assertThat(txManager.openTransactionCount()).isEqualTo(0);
    }

    @Test
    void shouldRequireCredentialChange() throws IOException, InterruptedException {
        var beginReq = testClient.beginTxJsonl(
                QueryRequest.newBuilder().statement("SHOW USERS").build());

        QueryResponseJsonlAssertions.assertThat(beginReq)
                .hasStatus(400)
                .receivesError(Status.Security.CredentialsExpired)
                .hasNoRemainingEvents();
    }

    @Test
    void shouldAllowAccessWhenPasswordChanged() throws IOException, InterruptedException, QueryApiTestClientException {
        testClient.autoCommitJsonl(
                QueryRequest.newBuilder()
                        .statement("ALTER CURRENT USER SET PASSWORD FROM 'neo4j' TO 'secretPassword'")
                        .build(),
                "system");

        var updateAuthClient = new QueryAPITestClient(
                endpoint, "neo4j", "secretPassword", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));

        var res = updateAuthClient.beginTxJsonl(
                QueryRequest.newBuilder().statement("RETURN 1").build());

        var txIdCapture = new Capture<String>();

        QueryResponseJsonlAssertions.assertThat(res)
                .wasSuccessful()
                .receivesHeader("1")
                .receivesRecord(1)
                .receivesSummary(that -> that.hasTransaction(txIdCapture.capture()))
                .hasNoRemainingEvents();

        updateAuthClient.commitTxJsonl(txIdCapture.getCaptured().getFirst());
    }

    @Test
    @Disabled("Need to check error handling for Auth")
    void shouldReturnUnauthorizedWithWrongCredentials() throws IOException, InterruptedException {
        var badAuthClient = new QueryAPITestClient(
                endpoint, "neo4j", "I'm sneaky!", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));

        var res = badAuthClient.beginTxJsonl(
                QueryRequest.newBuilder().statement("RETURN 1").build());

        QueryResponseJsonlAssertions.assertThat(res)
                .hasContentType(QueryContentType.UNTYPED_L)
                .hasStatus(401)
                .receivesError(Status.Security.Unauthorized, "Invalid credential.")
                .hasNoRemainingEvents();
    }

    @Test
    @Disabled("Need to check error handling for Auth")
    void shouldReturnUnauthorizedWithMissingAuthHeader() throws IOException, InterruptedException {
        var noAuth = new QueryAPITestClient(endpoint, QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));

        var res = noAuth.beginTxJsonl(
                QueryRequest.newBuilder().statement("RETURN 1").build());

        QueryResponseJsonlAssertions.assertThat(res)
                .hasContentType(QueryContentType.UNTYPED_L)
                .hasStatus(401)
                .receivesError(Status.Security.Unauthorized, "Invalid credential.")
                .hasNoRemainingEvents();
    }

    @Test
    @Disabled("Need to check error handling for Auth")
    @Timeout(30)
    void shouldErrorWhenTooManyIncorrectPasswordAttempts() throws IOException, InterruptedException {
        testClient.autoCommitJsonl(
                QueryRequest.newBuilder()
                        .statement("ALTER CURRENT USER SET PASSWORD FROM 'neo4j' TO 'secretPassword'")
                        .build(),
                "system");

        HttpResponse<Stream<String>> response;

        do {
            response = testClient.beginTxJsonl();
        } while (response.statusCode() != 429);

        QueryResponseJsonlAssertions.assertThat(response)
                .hasContentType(QueryContentType.UNTYPED_L)
                .receivesError(Status.Security.AuthenticationRateLimit)
                .hasNoRemainingEvents();
    }

    @Test
    void shouldNotAllowUserToChangeMidTx() throws IOException, InterruptedException, QueryApiTestClientException {
        setupUsers();

        var txIdCapture = new Capture<String>();
        var bobClient = new QueryAPITestClient(
                endpoint, "bob", "secretPassword", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));
        var aliceClient = new QueryAPITestClient(
                endpoint, "alice", "secretPassword", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));

        var bobsTx = bobClient.beginTxJsonl();

        QueryResponseJsonlAssertions.assertThat(bobsTx)
                .wasSuccessful()
                .receivesHeader(QueryResponseJsonlAssertions.HeaderAssertions::doesNotHaveFields)
                .receivesSummary(that -> that.hasTransaction(txIdCapture.capture()))
                .hasNoRemainingEvents();

        var aliceTriesToSneak =
                aliceClient.runInTxJsonl(txIdCapture.getCaptured().getFirst());

        QueryResponseJsonlAssertions.assertThat(aliceTriesToSneak)
                .hasContentType(QueryContentType.UNTYPED_L)
                .hasStatus(404)
                .receivesError(Status.Request.Invalid)
                .hasNoRemainingEvents();

        var commit = bobClient.commitTxJsonl(txIdCapture.getCaptured().getFirst());
        QueryResponseJsonlAssertions.assertThat(commit)
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesSummary()
                .hasNoRemainingEvents();
    }

    @Test
    void shouldNotAllowUserToChangeOnCommit() throws IOException, InterruptedException, QueryApiTestClientException {
        setupUsers();

        var txIdCapture = new Capture<String>();
        var bobClient = new QueryAPITestClient(
                endpoint, "bob", "secretPassword", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));
        var aliceClient = new QueryAPITestClient(
                endpoint, "alice", "secretPassword", QueryContentType.UNTYPED, List.of(QueryContentType.UNTYPED_L));

        var bobsTx = bobClient.beginTxJsonl();

        QueryResponseJsonlAssertions.assertThat(bobsTx)
                .wasSuccessful()
                .receivesHeader(QueryResponseJsonlAssertions.HeaderAssertions::doesNotHaveFields)
                .receivesSummary(that -> that.hasTransaction(txIdCapture.capture()))
                .hasNoRemainingEvents();

        var aliceTriesToSneak =
                aliceClient.commitTxJsonl(txIdCapture.getCaptured().getFirst());

        QueryResponseJsonlAssertions.assertThat(aliceTriesToSneak)
                .hasContentType(QueryContentType.UNTYPED_L)
                .hasStatus(404)
                .receivesError(Status.Request.Invalid)
                .hasNoRemainingEvents();

        var commit = bobClient.commitTxJsonl(txIdCapture.getCaptured().getFirst());
        QueryResponseJsonlAssertions.assertThat(commit)
                .hasContentType(QueryContentType.UNTYPED_L)
                .wasSuccessful()
                .receivesNHeaders(1)
                .receivesSummary()
                .hasNoRemainingEvents();
    }

    private void setupUsers() throws IOException, InterruptedException {
        testClient.autoCommitJsonl(
                QueryRequest.newBuilder()
                        .statement("ALTER CURRENT USER SET PASSWORD FROM 'neo4j' TO 'secretPassword'")
                        .build(),
                "system");

        var updatedAuthClient = new QueryAPITestClient(endpoint, "neo4j", "secretPassword");
        updatedAuthClient.autoCommitJsonl(
                QueryRequest.newBuilder()
                        .statement("CREATE USER bob SET PASSWORD 'secretPassword' SET PASSWORD CHANGE NOT REQUIRED")
                        .build(),
                "system");
        updatedAuthClient.autoCommitJsonl(
                QueryRequest.newBuilder()
                        .statement("CREATE USER alice SET PASSWORD 'secretPassword' SET PASSWORD CHANGE NOT REQUIRED")
                        .build(),
                "system");
        updatedAuthClient.autoCommitJsonl(
                QueryRequest.newBuilder().statement("GRANT ROLE admin to bob").build(), "system");
        updatedAuthClient.autoCommitJsonl(
                QueryRequest.newBuilder().statement("GRANT ROLE admin to alice").build(), "system");
    }
}

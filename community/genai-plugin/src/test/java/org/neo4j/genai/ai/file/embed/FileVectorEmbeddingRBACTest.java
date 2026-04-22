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
package org.neo4j.genai.ai.file.embed;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo.EMBEDDED_CONNECTION;
import static org.neo4j.kernel.api.security.AuthToken.newBasicAuthToken;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.neo4j.kernel.enterprise.api.security.EnterpriseAuthManager;
import com.neo4j.test.extension.EnterpriseDbmsExtension;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.genai.GenAiPluginExtension;
import org.neo4j.genai.ai.text.embed.provider.azure.AzureOpenAi;
import org.neo4j.genai.ai.text.embed.provider.bedrock.BedrockTitan;
import org.neo4j.genai.ai.text.embed.provider.openai.OpenAi;
import org.neo4j.genai.ai.text.embed.provider.vertexai.VertexAi;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.security.exception.InvalidAuthTokenException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnterpriseDbmsExtension(configurationCallback = "configure")
public class FileVectorEmbeddingRBACTest implements GenAITestExtension {

    private static final String EMBED_QUERY = """
            WITH { token: 'dummy-openai-token', model: 'text-embedding-3-small' } AS conf
            CALL ai.file.embedBatch($file, 'openai', conf)
            YIELD index, vector, resource
            RETURN *
            """;

    @Inject
    private EnterpriseAuthManager authManager;

    @Inject
    private DatabaseManagementService databaseManagementService;

    @Inject
    private TestDirectory testDirectory;

    private WireMockServer wireMock;

    private Path testFile;

    private GraphDatabaseAPI db;
    private GraphDatabaseService system;

    private static final String user = "procuser";
    private static final String password = "password";
    private static final String role = "procrole";

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        testFile = testDirectory.createFile("test.txt");
        Files.writeString(testFile, "Hello!");

        this.wireMock = new WireMockServer(options()
                .dynamicPort()
                .usingFilesUnderClasspath("wiremock/embeddings")
                .http2PlainDisabled(true));
        this.wireMock.start();
        final var baseUrl = this.wireMock.baseUrl();
        builder.addExtension(new GenAiPluginExtension(
                new AzureOpenAi(),
                new OpenAi(baseUrl + "/v1"),
                new VertexAi(p -> URI.create(baseUrl)),
                new BedrockTitan(p -> URI.create(baseUrl))));

        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
        builder.setConfig(GraphDatabaseSettings.allow_file_urls, true);
        builder.setConfig(GraphDatabaseSettings.auth_enabled, true);
    }

    @BeforeAll
    void setup() {
        db = (GraphDatabaseAPI) databaseManagementService.database(DEFAULT_DATABASE_NAME);
        system = databaseManagementService.database(SYSTEM_DATABASE_NAME);

        system.executeTransactionally(
                "CREATE USER $user SET PASSWORD $password CHANGE NOT REQUIRED",
                Map.of("user", user, "password", password));
        system.executeTransactionally("CREATE ROLE $role", Map.of("role", role));
        system.executeTransactionally("GRANT ROLE $role TO $user", Map.of("user", user, "role", role));
    }

    @AfterAll
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void shouldDenyFileEmbedWhenLoadPrivilegeMissing() throws InvalidAuthTokenException {
        system.executeTransactionally("DENY LOAD ON CIDR \"0.0.0.0/0\" TO $role", Map.of("role", role));
        LoginContext loginContext = authManager.login(newBasicAuthToken(user, password), EMBEDDED_CONNECTION);
        try (var tx = db.beginTransaction(KernelTransaction.Type.EXPLICIT, loginContext)) {
            assertThatThrownBy(() -> tx
                            .execute(
                                    EMBED_QUERY, Map.of("file", testFile.toUri().toString()))
                            .stream()
                            .toList())
                    .hasMessageContaining("LOAD on URL")
                    .satisfies(throwable -> assertThat(throwable.getMessage())
                            .matches(msg -> msg.contains("is not allowed for") || msg.contains("is denied for")));
        }
    }

    @Test
    void shouldDenyWebEmbedWhenLoadPrivilegeMissing() throws InvalidAuthTokenException {
        String webUrl = this.wireMock.baseUrl() + "/web-test.txt";
        system.executeTransactionally("DENY LOAD ON ALL DATA TO $role", Map.of("role", role));
        LoginContext loginContext = authManager.login(newBasicAuthToken(user, password), EMBEDDED_CONNECTION);
        try (var tx = db.beginTransaction(KernelTransaction.Type.EXPLICIT, loginContext)) {
            assertThatThrownBy(() -> tx.execute(EMBED_QUERY, Map.of("file", webUrl)).stream()
                            .toList())
                    .hasMessageContaining("LOAD on URL")
                    .satisfies(throwable -> assertThat(throwable.getMessage())
                            .matches(msg -> msg.contains("is not allowed for") || msg.contains("is denied for")));
        }
    }

    @Test
    void shouldNotDenyFileEmbedWhenLoadPrivilegeGranted() throws InvalidAuthTokenException {
        system.executeTransactionally("GRANT LOAD ON ALL DATA TO $role", Map.of("role", role));
        LoginContext loginContext = authManager.login(newBasicAuthToken(user, password), EMBEDDED_CONNECTION);
        try (var tx = db.beginTransaction(KernelTransaction.Type.EXPLICIT, loginContext)) {
            var result = tx.execute(EMBED_QUERY, Map.of("file", testFile.toUri().toString())).stream()
                    .toList();

            assertThat(result).isNotEmpty();
            var row = result.getFirst();
            assertThat(row.get("resource")).isEqualTo("Hello!");
            assertThat(row.get("vector")).isNotNull();
        }
    }
}

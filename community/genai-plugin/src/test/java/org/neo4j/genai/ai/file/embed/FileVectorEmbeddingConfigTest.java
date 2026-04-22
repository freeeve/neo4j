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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import inet.ipaddr.IPAddressString;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.GenAiPluginExtension;
import org.neo4j.genai.ai.text.embed.provider.azure.AzureOpenAi;
import org.neo4j.genai.ai.text.embed.provider.bedrock.BedrockTitan;
import org.neo4j.genai.ai.text.embed.provider.openai.OpenAi;
import org.neo4j.genai.ai.text.embed.provider.vertexai.VertexAi;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ImpermanentDbmsExtension(configurationCallback = "configure")
public class FileVectorEmbeddingConfigTest implements GenAITestExtension {

    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private TestDirectory testDirectory;

    private WireMockServer wireMock;

    private Path testFile;

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        testFile = testDirectory.createFile("test.txt");
        Files.writeString(testFile, "Hej!");

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
        builder.setConfig(GraphDatabaseSettings.allow_file_urls, false);
        builder.setConfig(
                GraphDatabaseInternalSettings.cypher_ip_blocklist,
                List.of(new IPAddressString("127.0.0.1/32"), new IPAddressString("::1/128")));
    }

    @AfterAll
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void shouldFailIfFileUrlsDisabled() {
        String query = """
            WITH { token: 'dummy-openai-token', model: 'text-embedding-3-small' } AS conf
            CALL ai.file.embedBatch($file, 'openai', conf)
            YIELD index, vector, resource
            RETURN *
        """;
        assertThatThrownBy(() -> db.executeTransactionally(
                        query, Map.of("file", testFile.toUri().toString()), res -> {
                            res.accept(row -> true);
                            return null;
                        }))
                .hasMessageContaining(
                        "configuration property 'dbms.security.allow_csv_import_from_file_urls' is false");
    }

    @Test
    void shouldFailIfProviderUrlIsBlockedByIpBlocklist() {
        String webUrl = this.wireMock.baseUrl() + "/web-test.txt";

        String query = """
            WITH { token: 'dummy-openai-token', model: 'text-embedding-3-small' } AS conf
            CALL ai.file.embedBatch($file, 'openai', conf)
            YIELD index, vector, resource
            RETURN *
        """;

        assertThatThrownBy(() -> db.executeTransactionally(
                        query, Map.of("file", webUrl), res -> res.stream().toList()))
                .hasMessageContaining("is blocked via the configuration property internal.dbms.cypher_ip_blocklist");
    }

    @Test
    void shouldFailForUnsupportedProtocols() {
        String query = """
            WITH { token: 'dummy-openai-token', model: 'text-embedding-3-small' } AS conf
            CALL ai.file.embedBatch($file, 'openai', conf)
            YIELD index, vector, resource
            RETURN *
        """;

        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of("file", "s3://my-bucket/file.txt"), res -> {
                    while (res.hasNext()) res.next();
                    return null;
                }))
                .hasMessageContaining("Unsupported protocol: s3");

        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of("file", "gs://my-bucket/file.txt"), res -> {
                    while (res.hasNext()) res.next();
                    return null;
                }))
                .hasMessageContaining("Unsupported protocol: gs");

        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of("file", "boom://dir/file.csv"), res -> {
                    while (res.hasNext()) res.next();
                    return null;
                }))
                .hasMessageContaining("Unsupported protocol: boom");
    }
}

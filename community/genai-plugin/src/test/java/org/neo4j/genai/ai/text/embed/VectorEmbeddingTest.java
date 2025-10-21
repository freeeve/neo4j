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
package org.neo4j.genai.ai.text.embed;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.collections.api.factory.Maps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.GenAiPluginExtension;
import org.neo4j.genai.ai.ProviderArgs;
import org.neo4j.genai.ai.ProviderArguments;
import org.neo4j.genai.ai.text.embed.provider.OpenAi;
import org.neo4j.genai.ai.text.embed.provider.VertexAi;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ImpermanentDbmsExtension(configurationCallback = "configure")
public class VectorEmbeddingTest implements GenAITestExtension {
    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private TestDirectory testDirectory;

    private WireMockServer wireMock;

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        this.wireMock = new WireMockServer(options()
                .dynamicPort()
                .usingFilesUnderClasspath("wiremock/completion")
                // Uncomment for wiremock debug prints:
                // .notifier(new ConsoleNotifier(true))
                .http2PlainDisabled(true));
        this.wireMock.start();
        final var baseUrl = this.wireMock.baseUrl();
        builder.addExtension(new GenAiPluginExtension(new OpenAi(baseUrl), new VertexAi(p -> URI.create(baseUrl))));
        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
    }

    @AfterAll
    void stopWireMock() {
        if (this.wireMock != null) this.wireMock.stop();
    }

    private final List<Map<String, Object>> EXPECTED_PROVIDERS = List.of(
            Map.of(
                    "name",
                    "OpenAI",
                    "requiredConfigType",
                    """
                    {
                      token :: STRING NOT NULL,
                      model :: STRING NOT NULL
                    }""",
                    "optionalConfigType",
                    """
                    {
                      vendorOptions :: MAP NOT NULL
                    }""",
                    "defaultConfig",
                    Map.of("vendorOptions", Map.of())),
            Map.of(
                    "name",
                    "VertexAI",
                    "requiredConfigType",
                    """
                    {
                      token :: STRING NOT NULL,
                      model :: STRING NOT NULL,
                      project :: STRING NOT NULL,
                      region :: STRING NOT NULL
                    }""",
                    "optionalConfigType",
                    """
                    {
                      publisher :: STRING NOT NULL,
                      vendorOptions :: MAP NOT NULL
                    }""",
                    "defaultConfig",
                    Map.of("publisher", "google", "vendorOptions", Map.of())));

    @Test
    void listProviders() {
        assertThat(db.executeTransactionally("CALL ai.text.embed.providers()", Map.of(), consume()))
                .containsExactlyElementsOf(EXPECTED_PROVIDERS);
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredConfArguments.class)
    void embeddingWithRequiredArgs(ProviderArgs args) {
        final var query =
                """
                WITH %s AS conf
                RETURN ai.text.embed('Hello!', '%s', conf) IS :: VECTOR<FLOAT32> AS result"""
                        .formatted(args.conf(), args.provider());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .singleElement(resultMap())
                .containsEntry("result", true);
    }

    @ParameterizedTest
    @ArgumentsSource(AllOptionsArguments.class)
    void embeddingWithAllArgs(ProviderArgs args) {
        final var query =
                """
                WITH %s as conf
                RETURN ai.text.embed('Hello!', '%s', conf) IS :: VECTOR<FLOAT32> AS result
                """
                        .formatted(args.conf(), args.provider());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .singleElement(resultMap())
                .containsEntry("result", true);
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredConfArguments.class)
    void batchEmbeddingWithRequiredArgs1(ProviderArgs args) {
        final var query =
                """
                    CALL ai.text.embedBatch(
                      ['Hello!'],
                      '%s',
                      %s
                    )
                    YIELD index, vector, resource
                    RETURN index, resource, vector IS :: VECTOR<FLOAT32> AS vector
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(Map.of("vector", true, "index", 0L, "resource", "Hello!"));
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredConfArguments.class)
    void batchEmbeddingWithRequiredArgs2(ProviderArgs args) {
        final var query =
                """
                    CALL ai.text.embedBatch(
                      [null, 'Hello!'],
                      '%s',
                      %s
                    )
                    YIELD index, vector, resource
                    LET vectorResult = CASE WHEN vector IS NULL THEN null ELSE vector IS :: VECTOR<FLOAT32> END
                    RETURN index, resource, vectorResult AS vector
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(
                        Maps.immutable
                                .of("vector", null, "index", (Object) 0L, "resource", null)
                                .castToMap(),
                        Map.of("vector", true, "index", 1L, "resource", "Hello!"));
    }

    @ParameterizedTest
    @ArgumentsSource(AllOptionsArguments.class)
    void batchEmbeddingWithAllArgs1(ProviderArgs args) {
        final var query =
                """
                    CALL ai.text.embedBatch(
                      ['Hello!'],
                      '%s',
                      %s
                    )
                    YIELD index, vector, resource
                    RETURN index, resource, vector IS :: VECTOR<FLOAT32> AS vector
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(Map.of("vector", true, "index", 0L, "resource", "Hello!"));
    }

    @ParameterizedTest
    @ArgumentsSource(AllOptionsArguments.class)
    void batchEmbedWithAllArgs2(ProviderArgs args) {
        final var query =
                """
                    CALL ai.text.embedBatch(
                      [null, 'Hello!'],
                      '%s',
                      %s
                    )
                    YIELD index, vector, resource
                    LET vectorResult = CASE WHEN vector IS NULL THEN null ELSE vector IS :: VECTOR<FLOAT32> END
                    RETURN index, resource, vectorResult AS vector
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(
                        Maps.immutable
                                .of("vector", null, "index", (Object) 0L, "resource", null)
                                .castToMap(),
                        Map.of("vector", true, "index", 1L, "resource", "Hello!"));
    }

    @Disabled // Enable when procedures are not internal
    @Test
    void embeddingDocsAreUpToDate() {
        final var expectedProviders = EXPECTED_PROVIDERS.stream()
                .map(m -> m.get("name").toString())
                .sorted()
                .collect(Collectors.joining("', '", "'", "'."));
        final var showFuncQuery =
                """
                        SHOW FUNCTIONS YIELD name, argumentDescription
                        WHERE name = 'ai.text.embed'
                        RETURN *
                        """;
        assertThat(db.executeTransactionally(showFuncQuery, Map.of(), consume()))
                .singleElement(map(String.class, Object.class))
                .extracting("argumentDescription", InstanceOfAssertFactories.LIST)
                .filteredOn(d -> d instanceof Map map && "provider".equals(map.get("name")))
                .singleElement(map(String.class, Object.class))
                .containsEntry("description", "The identifier of the provider: " + expectedProviders);

        final var showProcQuery =
                """
                        SHOW PROCEDURES YIELD name, argumentDescription
                        WHERE name = 'ai.text.embedBatch'
                        RETURN *
                        """;
        assertThat(db.executeTransactionally(showProcQuery, Map.of(), consume()))
                .singleElement(map(String.class, Object.class))
                .extracting("argumentDescription", InstanceOfAssertFactories.LIST)
                .filteredOn(d -> d instanceof Map map && "provider".equals(map.get("name")))
                .singleElement(map(String.class, Object.class))
                .containsEntry("description", "The identifier of the provider: " + expectedProviders);
    }
}

class RequiredConfArguments implements ProviderArguments {
    @Override
    public Stream<ProviderArgs> providers() {
        return Stream.of(
                new ProviderArgs("openai", "{ token: 'dummy-openai-token', model: 'text-embedding-3-small' }"),
                new ProviderArgs(
                        "vertexai",
                        "{ token: 'dummy-vertex-token', model: 'gemini-embedding-001', region: 'tasman', project: 'gem', publisher: 'google' }"));
    }
}

class AllOptionsArguments implements ProviderArguments {
    @Override
    public Stream<ProviderArgs> providers() {
        return Stream.of(
                new ProviderArgs(
                        "openai",
                        """
                        {
                          token: 'dummy-openai-token',
                          model: 'text-embedding-3-small',
                          vendorOptions: { dimensions: 1536, user: 'gem' }
                        }"""),
                new ProviderArgs(
                        "vertexai",
                        """
                        {
                          token: 'dummy-vertex-token',
                          model: 'gemini-embedding-001',
                          region: 'tasman',
                          project: 'gem',
                          publisher: 'google',
                          vendorOptions: { autoTruncate: true, task_type: 'QUESTION_ANSWERING' }
                        }"""));
    }
}

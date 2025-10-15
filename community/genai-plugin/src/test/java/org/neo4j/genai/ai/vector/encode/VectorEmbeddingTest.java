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
package org.neo4j.genai.ai.vector.encode;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.collections.api.factory.Maps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.GenAiPluginExtension;
import org.neo4j.genai.ai.vector.encode.provider.OpenAi;
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
        builder.addExtension(new GenAiPluginExtension(new OpenAi(baseUrl)));
        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
    }

    @AfterAll
    void stopWireMock() {
        if (this.wireMock != null) this.wireMock.stop();
    }

    private final List<Map<String, Object>> EXPECTED_PROVIDERS = List.of(Map.of(
            "name",
            "OpenAI",
            "requiredConfigType",
            "{ token :: STRING NOT NULL, model :: STRING NOT NULL }",
            "optionalConfigType",
            "{ dimensions :: INTEGER }",
            "defaultConfig",
            Map.of()));

    @Test
    void listProviders() {
        assertThat(db.executeTransactionally("CALL ai.vector.embed.providers()", Map.of(), consume()))
                .containsExactlyElementsOf(EXPECTED_PROVIDERS);
    }

    @Test
    void embeddingWithRequiredArgs() {
        final var query =
                """
                        WITH { token: 'dummy-openai-token', model: 'text-embedding-3-small' } as conf
                        RETURN ai.vector.embed('Hello!', 'openai', conf) IS :: VECTOR<FLOAT32>(1536) AS result
                        """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", true);
    }

    @Test
    void embeddingWithAllArgs() {
        final var query =
                """
                        WITH { token: 'dummy-openai-token', model: 'text-embedding-3-small', dimensions: 1536 } as conf
                        RETURN ai.vector.embed('Hello!', 'openai', conf) IS :: VECTOR<FLOAT32>(1536) AS result
                        """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", true);
    }

    @Test
    void batchEmbeddingWithRequiredArgs1() {
        final var query =
                """
                        CALL ai.vector.embedBatch(
                          ['Hello!'],
                          'openai',
                          { token: 'dummy-openai-token', model: 'text-embedding-3-small' }
                        )
                        YIELD index, vector, resource
                        RETURN index, resource, vector IS :: VECTOR<FLOAT32>(1536) AS vector
                        """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(Map.of("vector", true, "index", 0L, "resource", "Hello!"));
    }

    @Test
    void batchEmbeddingWithRequiredArgs2() {
        final var query =
                """
                        CALL ai.vector.embedBatch(
                          [null, 'Hello!'],
                          'openai',
                          { token: 'dummy-openai-token', model: 'text-embedding-3-small' }
                        )
                        YIELD index, vector, resource
                        LET vectorResult = CASE WHEN vector IS NULL THEN null ELSE vector IS :: VECTOR<FLOAT32>(1536) END
                        RETURN index, resource, vectorResult AS vector
                        """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(
                        Maps.immutable
                                .of("vector", null, "index", (Object) 0L, "resource", null)
                                .castToMap(),
                        Map.of("vector", true, "index", 1L, "resource", "Hello!"));
    }

    @Test
    void batchEmbeddingWithAllArgs1() {
        final var query =
                """
                        CALL ai.vector.embedBatch(
                          ['Hello!'],
                          'openai',
                          { token: 'dummy-openai-token', model: 'text-embedding-3-small', dimensions : 1536 }
                        )
                        YIELD index, vector, resource
                        RETURN index, resource, vector IS :: VECTOR<FLOAT32>(1536) AS vector
                        """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(Map.of("vector", true, "index", 0L, "resource", "Hello!"));
    }

    @Test
    void batchEmbeddingWithAllArgs2() {
        final var query =
                """
                        CALL ai.vector.embedBatch(
                          [null, 'Hello!'],
                          'openai',
                          { token: 'dummy-openai-token', model: 'text-embedding-3-small', dimensions : 1536 }
                        )
                        YIELD index, vector, resource
                        LET vectorResult = CASE WHEN vector IS NULL THEN null ELSE vector IS :: VECTOR<FLOAT32>(1536) END
                        RETURN index, resource, vectorResult AS vector

                        """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
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
                        WHERE name = 'ai.vector.embed'
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
                        WHERE name = 'ai.vector.embedBatch'
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

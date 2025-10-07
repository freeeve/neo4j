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
package org.neo4j.genai.ai.text.completion;

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
import org.neo4j.genai.ai.text.completion.provider.OpenAi;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ImpermanentDbmsExtension(configurationCallback = "configure")
public class TextCompletionTest implements GenAITestExtension {
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
            "{ maxOutputTokens :: INTEGER, store :: BOOLEAN }",
            "defaultConfig",
            Map.of()));

    @Test
    void listProviders() {
        assertThat(db.executeTransactionally("call ai.text.completion.providers()", Map.of(), consume()))
                .containsExactlyElementsOf(EXPECTED_PROVIDERS);
    }

    @Test
    void completionWithRequiredArgs() {
        final var query =
                """
                with { token: 'dummy-openai-token', model: 'gpt-5' } as conf
                return ai.text.completion('Hello!', 'openai', conf) as result
                """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", "Bla bla bla...");
    }

    @Test
    void completionWithAllArgs() {
        final var query =
                """
                with { token: 'dummy-openai-token', model: 'gpt-5', maxOutputTokens: 1024, store: false } as conf
                return ai.text.completion('Hello!', 'openai', conf) as result
                """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", "Bla bla bla...");
    }

    @Test
    void batchCompletionWithRequiredArgs1() {
        final var query =
                """
                call ai.text.completion(
                  ['Hello!'],
                  'openai',
                  { token: 'dummy-openai-token', model: 'gpt-5' }
                )
                """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(Map.of("completion", "Bla bla bla...", "index", 0L));
    }

    @Test
    void batchCompletionWithRequiredArgs2() {
        final var query =
                """
                call ai.text.completion(
                  [null, 'Hello!'],
                  'openai',
                  { token: 'dummy-openai-token', model: 'gpt-5' }
                )
                """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(
                        Maps.immutable
                                .of("completion", null, "index", (Object) 0L)
                                .castToMap(),
                        Map.of("completion", "Bla bla bla...", "index", 1L));
    }

    @Test
    void batchCompletionWithAllArgs1() {
        final var query =
                """
                call ai.text.completion(
                  ['Hello!'],
                  'openai',
                  { token: 'dummy-openai-token', model: 'gpt-5', maxOutputTokens: 1024, store: false }
                )
                """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(Map.of("completion", "Bla bla bla...", "index", 0L));
    }

    @Test
    void batchCompletionWithAllArgs2() {
        final var query =
                """
                call ai.text.completion(
                  [null, 'Hello!'],
                  'openai',
                  { token: 'dummy-openai-token', model: 'gpt-5', maxOutputTokens: 1024, store: false }
                )
                """;
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .containsExactly(
                        Maps.immutable
                                .of("completion", null, "index", (Object) 0L)
                                .castToMap(),
                        Map.of("completion", "Bla bla bla...", "index", 1L));
    }

    @Disabled // Enable when procedures are not internal
    @Test
    void completionDocsAreUpToDate() {
        final var expectedProviders = EXPECTED_PROVIDERS.stream()
                .map(m -> m.get("name").toString())
                .collect(Collectors.joining("', '", "'", "'."));
        final var showFuncQuery =
                """
                show functions yield name, argumentDescription
                where name = 'ai.text.completion'
                return *
                """;
        assertThat(db.executeTransactionally(showFuncQuery, Map.of(), consume()))
                .singleElement(map(String.class, Object.class))
                .extracting("argumentDescription", InstanceOfAssertFactories.LIST)
                .filteredOn(d -> d instanceof Map map && "provider".equals(map.get("name")))
                .singleElement(map(String.class, Object.class))
                .containsEntry("description", "The identifier of the provider: " + expectedProviders);

        final var showProcQuery =
                """
                show procedures yield name, argumentDescription
                where name = 'ai.text.completion'
                return *
                """;
        assertThat(db.executeTransactionally(showProcQuery, Map.of(), consume()))
                .singleElement(map(String.class, Object.class))
                .extracting("argumentDescription", InstanceOfAssertFactories.LIST)
                .filteredOn(d -> d instanceof Map map && "provider".equals(map.get("name")))
                .singleElement(map(String.class, Object.class))
                .containsEntry("description", "The identifier of the provider: " + expectedProviders);
    }
}

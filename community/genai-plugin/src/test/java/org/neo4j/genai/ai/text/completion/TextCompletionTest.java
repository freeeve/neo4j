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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.GenAiPluginExtension;
import org.neo4j.genai.ai.text.completion.provider.OpenAi;
import org.neo4j.genai.ai.text.completion.provider.VertexAi;
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
                    "{ token :: STRING NOT NULL, model :: STRING NOT NULL }",
                    "optionalConfigType",
                    "{ vendorOptions :: MAP NOT NULL }",
                    "defaultConfig",
                    Map.of("vendorOptions", Map.of())),
            Map.of(
                    "name",
                    "VertexAI",
                    "requiredConfigType",
                    "{ token :: STRING NOT NULL, model :: STRING NOT NULL, project :: STRING NOT NULL, region :: STRING NOT NULL }",
                    "optionalConfigType",
                    "{ publisher :: STRING NOT NULL, vendorOptions :: MAP NOT NULL }",
                    "defaultConfig",
                    Map.of("publisher", "google", "vendorOptions", Map.of())));

    @Test
    void listProviders() {
        assertThat(db.executeTransactionally("call ai.text.completion.providers()", Map.of(), consume()))
                .containsExactlyElementsOf(EXPECTED_PROVIDERS);
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredConfArguments.class)
    void completionWithRequiredArgs(ProviderArgs args) {
        final var query =
                """
                with %s as conf
                return ai.text.completion('Hello!', '%s', conf) as result"""
                        .formatted(args.conf(), args.provider());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .singleElement(resultMap())
                .containsEntry("result", "Bla bla bla... (%s)".formatted(args.provider()));
    }

    @ParameterizedTest
    @ArgumentsSource(AllOptionsArguments.class)
    void completionWithAllArgs(ProviderArgs args) {
        final var query =
                """
                with %s as conf
                return ai.text.completion('Hello!', '%s', conf) as result
                """
                        .formatted(args.conf(), args.provider());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .singleElement(resultMap())
                .containsEntry("result", "Jag tog korven! (%s)".formatted(args.provider()));
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredConfArguments.class)
    void batchCompletionWithRequiredArgs1(ProviderArgs args) {
        final var query =
                """
                call ai.text.completion(
                  ['Hello!'],
                  '%s',
                  %s
                )
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(Map.of("completion", "Bla bla bla... (%s)".formatted(args.provider()), "index", 0L));
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredConfArguments.class)
    void batchCompletionWithRequiredArgs2(ProviderArgs args) {
        final var query =
                """
                call ai.text.completion(
                  [null, 'Hello!'],
                  '%s',
                  %s
                )
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(
                        Maps.immutable
                                .of("completion", null, "index", (Object) 0L)
                                .castToMap(),
                        Map.of("completion", "Bla bla bla... (%s)".formatted(args.provider()), "index", 1L));
    }

    @ParameterizedTest
    @ArgumentsSource(AllOptionsArguments.class)
    void batchCompletionWithAllArgs1(ProviderArgs args) {
        final var query =
                """
                call ai.text.completion(
                  ['Hello!'],
                  '%s',
                  %s
                )
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(Map.of("completion", "Jag tog korven! (%s)".formatted(args.provider()), "index", 0L));
    }

    @ParameterizedTest
    @ArgumentsSource(AllOptionsArguments.class)
    void batchCompletionWithAllArgs2(ProviderArgs args) {
        final var query =
                """
                call ai.text.completion(
                  [null, 'Hello!'],
                  '%s',
                  %s
                )
                """
                        .formatted(args.provider(), args.conf());
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .containsExactly(
                        Maps.immutable
                                .of("completion", null, "index", (Object) 0L)
                                .castToMap(),
                        Map.of("completion", "Jag tog korven! (%s)".formatted(args.provider()), "index", 1L));
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

record ProviderArgs(String provider, String conf) {}

interface ProviderArguments extends ArgumentsProvider {
    Stream<ProviderArgs> providers();

    @Override
    default Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context)
            throws Exception {
        return providers().map(p -> Arguments.argumentSet(p.provider(), p));
    }
}

class RequiredConfArguments implements ProviderArguments {
    @Override
    public Stream<ProviderArgs> providers() {
        return Stream.of(
                new ProviderArgs("openai", "{ token: 'dummy-openai-token', model: 'gpt-5' }"),
                new ProviderArgs(
                        "vertexai",
                        "{ token: 'dummy-vertex-token', model: 'gemini-3', region: 'smaland', project: 'astrid', publisher: 'google' }"));
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
                          model: 'gpt-5',
                          vendorOptions: {
                            max_output_tokens: 1024,
                            store: false
                          }
                        }"""),
                new ProviderArgs(
                        "vertexai",
                        """
                        {
                          token: 'dummy-vertex-token',
                          model: 'gemini-3',
                          region: 'smaland',
                          project: 'astrid',
                          publisher: 'google',
                          vendorOptions: {
                            systemInstruction: 'You are Kommendoran',
                            labels: { labelA: 'x' }
                          }
                        }"""));
    }
}

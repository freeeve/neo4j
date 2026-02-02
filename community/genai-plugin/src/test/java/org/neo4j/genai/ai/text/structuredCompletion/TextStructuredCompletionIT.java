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
package org.neo4j.genai.ai.text.structuredCompletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.ai.Tokens;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

class TextStructuredCompletionIT {

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.OpenAi.TOKEN_ENV, matches = ".*")
    class OpenAi extends TextStructuredCompletionITBase {
        @Override
        Map<String, Object> params() {
            return Map.of("token", System.getenv(Tokens.OpenAi.TOKEN_ENV));
        }

        @Override
        String schema() {
            return """
                    {
                      type: 'object',
                      properties: { answer: { type: 'string' } },
                      required: ['answer'],
                      additionalProperties: false
                    }
                    """;
        }

        @Override
        List<String> confRequired() {
            return List.of("{ token: $token, model: 'gpt-5-nano' }");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ token: $token, model: 'gpt-5-nano', vendorOptions: { store: false, instructions: 'Always answer with a single emoji.' } }");
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.AzureOpenAi.TOKEN_ENV, matches = ".*")
    @EnabledIfEnvironmentVariable(named = Tokens.AzureOpenAi.RESOURCE_ENV, matches = ".*")
    class AzureOpenAi extends TextStructuredCompletionITBase {
        @Override
        String provider() {
            return "azure-openai";
        }

        @Override
        Map<String, Object> params() {
            return Map.of(
                    "token",
                    System.getenv(Tokens.AzureOpenAi.TOKEN_ENV),
                    "resource",
                    System.getenv(Tokens.AzureOpenAi.RESOURCE_ENV));
        }

        @Override
        String schema() {
            return """
                    {
                      type: 'object',
                      properties: { answer: { type: 'string' } },
                      required: ['answer'],
                      additionalProperties: false
                    }
                    """;
        }

        @Override
        List<String> confRequired() {
            return List.of("{ token: $token, resource: $resource, model: 'gpt-5-mini' }");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ token: $token, resource: $resource, model: 'gpt-5-mini', vendorOptions: { store: false, instructions: 'Always answer with a single emoji.' } }");
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.Vertex.TOKEN_ENV, matches = ".*")
    @EnabledIfEnvironmentVariable(named = Tokens.Vertex.PROJECT_ENV, matches = ".*")
    class VertexAi extends TextStructuredCompletionITBase {
        @Override
        String provider() {
            return "vertexai";
        }

        @Override
        Map<String, Object> params() {
            return Map.of(
                    "token",
                    System.getenv(Tokens.Vertex.TOKEN_ENV),
                    "project",
                    System.getenv(Tokens.Vertex.PROJECT_ENV),
                    "region",
                    System.getenv(Tokens.Vertex.REGION_ENV));
        }

        @Override
        String schema() {
            // Vertex uses responseSchema; simple schema is fine
            return """
                    {
                      type: 'object',
                      properties: { answer: { type: 'string' } },
                      required: ['answer'],
                      additionalProperties: false
                    }
                    """;
        }

        @Override
        List<String> confRequired() {
            var isApiKeyEnv = System.getenv(Tokens.Vertex.IS_API_KEY);
            var isApiKey = isApiKeyEnv != null && isApiKeyEnv.equalsIgnoreCase("true");
            var tokenOrKey = isApiKey ? "apiKey" : "token";
            return List.of(
                    "{ %s: $token, model: 'gemini-2.5-flash-lite', region: $region, project: $project, publisher: 'google' }"
                            .formatted(tokenOrKey));
        }

        @Override
        List<String> confWithVendorOptions() {
            var isApiKeyEnv = System.getenv(Tokens.Vertex.IS_API_KEY);
            var isApiKey = isApiKeyEnv != null && isApiKeyEnv.equalsIgnoreCase("true");
            var tokenOrKey = isApiKey ? "apiKey" : "token";
            return List.of(
                    ("{ %s: $token, model: 'gemini-2.5-flash-lite', region: $region, project: $project, publisher: 'google', vendorOptions: { maxOutputTokens: 1024 } }")
                            .formatted(tokenOrKey));
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.Bedrock.ACCESS_KEY_ENV, matches = ".*")
    @EnabledIfEnvironmentVariable(named = Tokens.Bedrock.SECRET_ACCESS_KEY_ENV, matches = ".*")
    class BedrockConverse extends TextStructuredCompletionITBase {
        @Override
        String provider() {
            return "bedrock";
        }

        @Override
        Map<String, Object> params() {
            return Map.of(
                    "key",
                    System.getenv(Tokens.Bedrock.ACCESS_KEY_ENV),
                    "secret",
                    System.getenv(Tokens.Bedrock.SECRET_ACCESS_KEY_ENV));
        }

        @Override
        String schema() {
            return """
                    {
                      type: 'object',
                      properties: { answer: { type: 'string' } },
                      required: ['answer'],
                      additionalProperties: false
                    }
                    """;
        }

        @Override
        List<String> confRequired() {
            return List.of(
                    "{ accessKeyId: $key, secretAccessKey: $secret, region: 'us-east-1', model: 'amazon.nova-micro-v1:0' }");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ accessKeyId: $key, secretAccessKey: $secret, region: 'us-east-1', model: 'amazon.nova-micro-v1:0', vendorOptions: { temperature: 0.3 } }");
        }
    }
}

@ImpermanentDbmsExtension(configurationCallback = "configure")
abstract class TextStructuredCompletionITBase implements GenAITestExtension {

    @Inject
    GraphDatabaseAPI db;

    @Inject
    TestDirectory testDirectory;

    abstract Map<String, Object> params();

    abstract List<String> confRequired();

    abstract List<String> confWithVendorOptions();

    String provider() {
        return getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    abstract String schema();

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
        // Avoid logging the tokens
        builder.setConfig(GraphDatabaseSettings.log_queries_parameter_logging_enabled, false);
    }

    @Test
    void completionWithRequiredArgs() {
        for (final var conf : confRequired()) {
            assertNonEmptyMap("""
                    WITH %s AS conf, %s AS schema
                    RETURN ai.text.structuredCompletion('Hello!', schema, '%s', conf) AS result
                    """.formatted(conf, schema(), provider()));
        }
    }

    @Test
    void completionWithAllArgs() {
        for (final var conf : confWithVendorOptions()) {
            assertNonEmptyMap("""
                    WITH %s AS conf, %s AS schema
                    RETURN ai.text.structuredCompletion('Hello!', schema, '%s', conf) AS result
                    """.formatted(conf, schema(), provider()));
        }
    }

    private void assertNonEmptyMap(String query) {
        assertThat(db.executeTransactionally(query, params(), consume()))
                .singleElement(resultMap())
                .extracting("result")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .isNotEmpty();
    }
}

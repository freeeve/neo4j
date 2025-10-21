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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

class TextCompletionIT {

    @Nested
    @EnabledIfEnvironmentVariable(named = OpenAi.TOKEN_ENV, matches = ".*")
    class OpenAi extends TextCompletionITBase {
        static final String TOKEN_ENV = "OPEN_AI_TOKEN";

        @Override
        Map<String, Object> params() {
            return Map.of("token", System.getenv(TOKEN_ENV));
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
    @EnabledIfEnvironmentVariable(named = VertexAi.TOKEN_ENV, matches = ".*")
    @EnabledIfEnvironmentVariable(named = VertexAi.PROJECT_ENV, matches = ".*")
    class VertexAi extends TextCompletionITBase {
        static final String TOKEN_ENV = "VERTEX_AI_TOKEN";
        static final String PROJECT_ENV = "VERTEX_AI_PROJECT";

        @Override
        Map<String, Object> params() {
            return Map.of("token", System.getenv(TOKEN_ENV), "project", System.getenv(PROJECT_ENV));
        }

        @Override
        List<String> confRequired() {
            return List.of(
                    "{ token: $token, model: 'gemini-2.5-flash-lite', region: 'europe-west2', project: $project }");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ token: $token, model: 'gemini-2.5-flash-lite', region: 'europe-west2', project: $project, vendorOptions: { systemInstructions: 'Always answer with a single emoji.' } }");
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = BedrockNova.KEY, matches = ".*")
    @EnabledIfEnvironmentVariable(named = BedrockNova.SECRET, matches = ".*")
    class BedrockNova extends TextCompletionITBase {
        static final String KEY = "BEDROCK_KEY";
        static final String SECRET = "BEDROCK_SECRET_KEY";

        @Override
        String provider() {
            return "bedrock-nova";
        }

        @Override
        Map<String, Object> params() {
            return Map.of("key", System.getenv(KEY), "secret", System.getenv(SECRET));
        }

        @Override
        List<String> confRequired() {
            return List.of(
                    "{ model: 'amazon.nova-micro-v1:0', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret }",
                    "{ model: 'arn:aws:bedrock:eu-west-2::foundation-model/amazon.nova-micro-v1:0', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret }");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ model: 'amazon.nova-micro-v1:0', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret, vendorOptions: { system: [{ text: 'Include an emoji in the answer.' }] } }",
                    "{ model: 'arn:aws:bedrock:eu-west-2::foundation-model/amazon.nova-micro-v1:0', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret, vendorOptions: { system: [{ text: 'Include an emoji in the answer.' }] } }");
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = BedrockTitan.KEY, matches = ".*")
    @EnabledIfEnvironmentVariable(named = BedrockTitan.SECRET, matches = ".*")
    class BedrockTitan extends TextCompletionITBase {
        static final String KEY = "BEDROCK_KEY";
        static final String SECRET = "BEDROCK_SECRET_KEY";

        @Override
        String provider() {
            return "bedrock-titan";
        }

        @Override
        Map<String, Object> params() {
            return Map.of("key", System.getenv(KEY), "secret", System.getenv(SECRET));
        }

        @Override
        List<String> confRequired() {
            return List.of(
                    "{ model: 'amazon.titan-text-lite-v1', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret }",
                    "{ model: 'arn:aws:bedrock:eu-west-2::foundation-model/amazon.titan-text-lite-v1', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret }");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ model: 'amazon.titan-text-lite-v1', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret, vendorOptions: { textGenerationConfig: { maxTokenCount: 1024 } } }",
                    "{ model: 'arn:aws:bedrock:eu-west-2::foundation-model/amazon.titan-text-lite-v1', region: 'eu-west-2', accessKeyId: $key, secretAccessKey: $secret, vendorOptions: { textGenerationConfig: { maxTokenCount: 1024 } } }");
        }
    }
}

@ImpermanentDbmsExtension(configurationCallback = "configure")
abstract class TextCompletionITBase implements GenAITestExtension {

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
            assertNonBlankResult(
                    """
                    with %s as conf
                    with ai.text.completion('Hello!', '%s', conf) as result
                    return result"""
                            .formatted(conf, provider()));
        }
    }

    @Test
    void completionWithAllArgs() {
        for (final var conf : confWithVendorOptions()) {
            assertNonBlankResult(
                    """
                    with %s as conf
                    with ai.text.completion('Hello!', '%s', conf) as result
                    return result"""
                            .formatted(conf, provider()));
        }
    }

    private void assertNonBlankResult(String query) {
        assertThat(execute(query))
                .singleElement(resultMap())
                .extracting("result")
                .asString()
                .isNotBlank();
    }

    private List<Map<String, Object>> execute(String query) {
        try {
            return db.executeTransactionally(query, params(), consume());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to execute query:\n" + query, t);
        }
    }
}

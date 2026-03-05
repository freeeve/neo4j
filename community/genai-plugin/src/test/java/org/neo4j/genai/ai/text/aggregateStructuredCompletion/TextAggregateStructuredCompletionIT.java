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
package org.neo4j.genai.ai.text.aggregateStructuredCompletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

class TextAggregateStructuredCompletionIT {

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.OpenAi.TOKEN_ENV, matches = ".*")
    class OpenAi extends TextStructuredCompletionITBase {
        @Override
        Map<String, Object> params() {
            return Map.of("token", System.getenv(Tokens.OpenAi.TOKEN_ENV));
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
        List<String> confRequired() {
            var isApiKeyEnv = System.getenv(Tokens.Vertex.IS_API_KEY);
            var isApiKey = isApiKeyEnv != null && isApiKeyEnv.equalsIgnoreCase("true");
            var tokenOrKey = isApiKey ? "apiKey" : "token";
            return List.of("{ %s: $token, model: 'gemini-2.5-flash-lite', region: $region, project: $project}"
                    .formatted(tokenOrKey));
        }

        @Override
        List<String> confWithVendorOptions() {
            var isApiKeyEnv = System.getenv(Tokens.Vertex.IS_API_KEY);
            var isApiKey = isApiKeyEnv != null && isApiKeyEnv.equalsIgnoreCase("true");
            var tokenOrKey = isApiKey ? "apiKey" : "token";
            return List.of(
                    "{ %s: $token, model: 'gemini-2.5-flash-lite', region: $region, project: $project, vendorOptions: { system_instruction: { parts: [ { text: 'Always answer with a single emoji.'}]} } }"
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
        List<String> confRequired() {
            return List.of(
                    "{ model: 'amazon.nova-lite-v1:0', region: 'eu-north-1', accessKeyId: $key, secretAccessKey: $secret}",
                    "{ model: 'arn:aws:bedrock:eu-north-1::foundation-model/amazon.nova-lite-v1:0', region: 'eu-north-1', accessKeyId: $key, secretAccessKey: $secret}");
        }

        @Override
        List<String> confWithVendorOptions() {
            return List.of(
                    "{ model: 'openai.gpt-oss-20b-1:0', region: 'eu-north-1', accessKeyId: $key, secretAccessKey: $secret, vendorOptions: { inferenceConfig: { temperature: 0.5, maxTokens: 2048 } }}",
                    "{ model: 'arn:aws:bedrock:eu-north-1::foundation-model/amazon.nova-lite-v1:0', region: 'eu-north-1', accessKeyId: $key, secretAccessKey: $secret, vendorOptions: { inferenceConfig: { temperature: 0.5, maxTokens: 2048 } }}");
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

    String schema() {
        return """
            {
              type: 'object',
              properties: {
                painPoints: { type: 'array', items: { type: 'string' } },
                thingsToUpdate: { type: 'array', items: { type: 'string' } },
                sentiment: { type: 'string', enum: ['negative','neutral','positive'] }
              },
              required: ['painPoints','thingsToUpdate', 'sentiment'],
              additionalProperties: false
            }
         """;
    }

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
        // Avoid logging the tokens
        builder.setConfig(GraphDatabaseSettings.log_queries_parameter_logging_enabled, false);
    }

    @BeforeEach
    void setUp() {
        db.executeTransactionally("""
                CREATE (:UserReview {review: "The food was okay, but the service was terrible!"})
                CREATE (:UserReview {review: "Love this place!!!"})
                CREATE (:UserReview {review: "Rude staff and meh food"})
                CREATE (:UserReview {review: "Best pancakes, a little slow, but I had time"})
                CREATE (:UserReview {review: "Alright"})
                CREATE (:UserReview {review: "Lovely wee place, recommend for a calm breakfast"})
                CREATE (:UserReview {review: "Had to wait ages for the food to come! Almost left"})
                CREATE (:UserReview {review: "mmmmm pancakes with syrup"})
                """);
    }

    @Test
    void completionWithRequiredArgs() {
        for (final var conf : confRequired()) {
            assertNonBlankResult("""
                    WITH %s as conf, %s AS schema
                    MATCH (u:UserReview)
                    WITH ai.text.aggregateStructuredCompletion(u.review, 'From these reviews, extract common pain points and desired updates.', schema, '%s', conf) as result
                    RETURN result""".formatted(conf, schema(), provider()));
        }
    }

    @Test
    void completionWithAllArgs() {
        for (final var conf : confWithVendorOptions()) {
            assertNonBlankResult("""
                    WITH %s as conf, %s AS schema
                    MATCH (u:UserReview)
                    WITH ai.text.aggregateStructuredCompletion(u.review, 'From these reviews, extract common pain points and desired updates.', schema, '%s', conf) as result
                    RETURN result""".formatted(conf, schema(), provider()));
        }
    }

    private void assertNonBlankResult(String query) {
        var q = execute(query);
        System.out.println(q);
        assertThat(q).singleElement(resultMap()).extracting("result").asString().isNotBlank();
    }

    private List<Map<String, Object>> execute(String query) {
        try {
            return db.executeTransactionally(query, params(), consume());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to execute query:\n" + query, t);
        }
    }
}

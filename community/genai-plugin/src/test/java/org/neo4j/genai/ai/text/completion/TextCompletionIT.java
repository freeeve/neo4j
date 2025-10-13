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
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
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
        String confRequired() {
            return "{ token: $token, model: 'gpt-5-nano' }";
        }

        @Override
        String confWithVendorOptions() {
            return "{ token: $token, model: 'gpt-5-nano', vendorOptions: { store: false, instructions: 'Always answer with a single emoji.' } }";
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
        String confRequired() {
            return "{ token: $token, model: 'gemini-2.5-flash-lite', region: 'europe-west2', project: $project }";
        }

        @Override
        String confWithVendorOptions() {
            return "{ token: $token, model: 'gemini-2.5-flash-lite', region: 'europe-west2', project: $project, vendorOptions: { systemInstructions: 'Always answer with a single emoji.' } }";
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

    abstract String confRequired();

    abstract String confWithVendorOptions();

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
        assertNonBlankResult(
                """
                with %s as conf
                with ai.text.completion('Hello!', '%s', conf) as result
                return result"""
                        .formatted(confRequired(), provider()));
    }

    @Test
    void completionWithAllArgs() {
        assertNonBlankResult(
                """
                with %s as conf
                with ai.text.completion('Hello!', '%s', conf) as result
                return result"""
                        .formatted(confWithVendorOptions(), provider()));
    }

    @Test
    void batchCompletionWithRequiredArgs() {
        final var query =
                """
                call ai.text.completion(
                  ['Testing', null, 'Hello!'],
                  '%s',
                  %s
                )
                """
                        .formatted(provider(), confRequired());
        assertThat(db.executeTransactionally(query, params(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .satisfiesExactly(batchedNonBlankRow(0), batchedNullRow(1), batchedNonBlankRow(2));
    }

    @Test
    void batchCompletionWithAllArgs() {
        final var query =
                """
                call ai.text.completion(
                  ['Testing', null, 'Hello!'],
                  '%s',
                  %s
                )
                """
                        .formatted(provider(), confWithVendorOptions());
        assertThat(db.executeTransactionally(query, params(), consume()))
                .as("Query:%n```%n%s%n```%n", query)
                .satisfiesExactly(batchedNonBlankRow(0), batchedNullRow(1), batchedNonBlankRow(2));
    }

    private void assertNonBlankResult(String query) {
        final var result = db.executeTransactionally(query, params(), consume());
        assertThat(result)
                .as("Query:%n```%n%s%n```%n", query)
                .singleElement(resultMap())
                .extracting("result")
                .asString()
                .isNotBlank();
    }

    private Consumer<Map<String, Object>> batchedNullRow(long index) {
        return row -> assertThat(row).containsEntry("index", index).containsEntry("completion", null);
    }

    private Consumer<Map<String, Object>> batchedNonBlankRow(long index) {
        return row -> assertThat(row)
                .containsEntry("index", index)
                .hasEntrySatisfying("completion", c -> assertThat(c).asString().isNotBlank());
    }
}

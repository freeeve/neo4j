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
package org.neo4j.genai.ai.text.chat;

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

class TextChatIT {

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.OpenAi.TOKEN_ENV, matches = ".*")
    class OpenAi extends TextChatITBase {

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
                    "{ token: $token, model: 'gpt-5-nano', vendorOptions: { store: true, instructions: 'Always answer with a single emoji.' } }");
        }
    }

    @Nested
    @EnabledIfEnvironmentVariable(named = Tokens.AzureOpenAi.TOKEN_ENV, matches = ".*")
    @EnabledIfEnvironmentVariable(named = Tokens.AzureOpenAi.RESOURCE_ENV, matches = ".*")
    class AzureOpenAi extends TextChatITBase {
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
                    "{ token: $token, resource: $resource, model: 'gpt-5-mini', vendorOptions: { store: true, instructions: 'Always answer with a single emoji.' } }");
        }
    }
}

@ImpermanentDbmsExtension(configurationCallback = "configure")
abstract class TextChatITBase implements GenAITestExtension {

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
    void chatWithRequiredArgs() {
        for (final var conf : confRequired()) {
            var id = execute("""
                    WITH %s AS conf
                    WITH ai.text.chat('Hello!', null, '%s', conf) AS result
                    RETURN result.chatId AS chatId""".formatted(conf, provider())).getFirst().get("chatId");

            assertNonBlankResult("""
                    with %s as conf
                    with ai.text.chat('Hello a second time!', '%s', '%s', conf) as result
                    return result""".formatted(conf, id, provider()));
        }
    }

    @Test
    void chatWithAllArgs() {
        for (final var conf : confWithVendorOptions()) {
            var id = execute("""
                    WITH %s AS conf
                    WITH ai.text.chat('Hello!', null, '%s', conf) AS result
                    RETURN result.chatId AS chatId""".formatted(conf, provider())).getFirst().get("chatId");

            assertNonBlankResult("""
                    with %s as conf
                    with ai.text.chat('Hello a second time!', '%s', '%s', conf) as result
                    return result""".formatted(conf, id, provider()));
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

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
package org.neo4j.genai.ai.text.chunkByTokenLimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ImpermanentDbmsExtension(configurationCallback = "configure")
public class TextChunkByTokenTest implements GenAITestExtension {
    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private TestDirectory testDirectory;

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
    }

    @Test
    void chunkByTokenTokenLimitLargerThanGivenText() {
        final var query = "RETURN ai.text.chunkByTokenLimit('Hello!', 100) AS result";
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", List.of("Hello!"));
    }

    @Test
    void shouldHandleMultipleChunks() {
        // "This is a sentence with many words that should be split." is roughly 11 tokens (cl100k_base/gpt-4)
        var startingText = "This is a sentence with many words that should be split.";
        final var query = "RETURN ai.text.chunkByTokenLimit($prompt, 5) AS result";
        assertThat(db.executeTransactionally(query, Map.of("prompt", startingText), consume()))
                .singleElement(resultMap())
                .satisfies(res -> {
                    List<String> result = (List<String>) res.get("result");
                    assertThat(result).hasSizeGreaterThan(1);
                    for (String chunk : result) {
                        assertThat(chunk).isNotEmpty();
                    }
                    assertThat(String.join("", result)).isEqualTo(startingText);
                });
    }

    @Test
    void shouldHandleOverlap() {
        var startingText = "One Two Three Four Five Six Seven Eight Nine Ten";
        final var query = "RETURN ai.text.chunkByTokenLimit($prompt, 5, 'gpt-4', 2) AS result";
        assertThat(db.executeTransactionally(query, Map.of("prompt", startingText), consume()))
                .singleElement(resultMap())
                .satisfies(res -> {
                    List<String> result = (List<String>) res.get("result");
                    assertThat(result).hasSizeGreaterThan(1);
                    // The overlap should cause some words to be shared between chunks.
                    assertThat(result.get(0)).contains("Four Five");
                    assertThat(result.get(1)).contains("Four Five");
                });
    }

    @Test
    void shouldReturnNullOnNullInput() {
        final var query = "RETURN ai.text.chunkByTokenLimit(null, 10) AS result";
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", null);
    }

    @Test
    void shouldHandleEmptyInput() {
        final var query = "RETURN ai.text.chunkByTokenLimit('', 10) AS result";
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", List.of(""));
    }

    @Test
    void shouldThrowErrorOnNullLimit() {
        final var query = "RETURN ai.text.chunkByTokenLimit('some text', null) AS result";
        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of(), consume()))
                .hasMessageContaining("'limit' must not be null");
    }

    @Test
    void shouldFallbackToDefaultModelOnInvalidModel() {
        // Model 'invalid-model' should fallback to R50K_BASE (OpenAI default)
        final var query = "RETURN ai.text.chunkByTokenLimit('Some text', 10, 'invalid-model') AS result";
        assertThat(db.executeTransactionally(query, Map.of(), consume()))
                .singleElement(resultMap())
                .containsEntry("result", List.of("Some text"));
    }

    @Test
    void shouldHandleZeroLimit() {
        final var query = "RETURN ai.text.chunkByTokenLimit('A B C', 0) AS result";
        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of(), consume()))
                .hasMessageContaining("'limit' must be greater than 0");
    }

    @Test
    void shouldHandleNegativeOverlap() {
        final var query = "RETURN ai.text.chunkByTokenLimit('A B C', 10, 'gpt-4', -1) AS result";
        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of(), consume()))
                .hasMessageContaining("'overlap' must be greater than or equal to 0");
    }

    @Test
    void shouldHandleOverlapEqualToLimit() {
        final var query = "RETURN ai.text.chunkByTokenLimit('A B C', 10, 'gpt-4', 10) AS result";
        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of(), consume()))
                .hasMessageContaining("'overlap' must be less than 'limit'");
    }

    @Test
    void shouldHandleOverlapLargerThanLimit() {
        final var query = "RETURN ai.text.chunkByTokenLimit('A B C', 10, 'gpt-4', 11) AS result";
        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of(), consume()))
                .hasMessageContaining("'overlap' must be less than 'limit'");
    }

    @Test
    void shouldHandleLimitGreaterThanMaxInt() {
        final var query = "RETURN ai.text.chunkByTokenLimit('A B C', 2147483650) AS result";
        assertThatThrownBy(() -> db.executeTransactionally(query, Map.of(), consume()))
                .hasMessageContaining("'limit' must be less than or equal to 2147483647");
    }

    @Test
    void differentModelsGiveDifferentResults() {
        var startingText = """
            This is a sentence with many words that should be split. So many words in fact, that I will tip tap typing all day.
            There is even another sentence which makes more splitting. Wow more words! incredible.
            And then even one more, this should help make many chunks. I would like lots of chunks.
            Chunky, chunky paragraph, lots of splitting to do here.
            Oh we are still going? amazing I wonder when this will end, it could be soon.
            Maybe now?
            Yes, now.
        """;
        final var query = """
            RETURN
                ai.text.chunkByTokenLimit($prompt, 120, 'davinci') AS result1,
                ai.text.chunkByTokenLimit($prompt, 120, 'gpt-4o') AS result2
        """;
        assertThat(db.executeTransactionally(query, Map.of("prompt", startingText), consume()))
                .singleElement(resultMap())
                .satisfies(res -> {
                    List<String> result1 = (List<String>) res.get("result1");
                    List<String> result2 = (List<String>) res.get("result2");
                    assertThat(result1).hasSizeGreaterThan(result2.size());
                    assertThat(String.join("", result1)).isEqualTo(startingText);
                    assertThat(String.join("", result2)).isEqualTo(startingText);
                });
    }
}

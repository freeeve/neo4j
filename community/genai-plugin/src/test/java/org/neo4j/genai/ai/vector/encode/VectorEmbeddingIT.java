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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
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
import org.neo4j.values.storable.VectorValue;

@ImpermanentDbmsExtension(configurationCallback = "configure")
@EnabledIfEnvironmentVariable(named = VectorEmbeddingIT.OPEN_AI_TOKEN_ENV, matches = ".*")
public class VectorEmbeddingIT implements GenAITestExtension {
    static final String OPEN_AI_TOKEN_ENV = "OPEN_AI_TOKEN";

    final String openAiToken = System.getenv(OPEN_AI_TOKEN_ENV);
    final Map<String, Object> tokenParam = Collections.singletonMap("token", openAiToken);

    @Inject
    GraphDatabaseAPI db;

    @Inject
    TestDirectory testDirectory;

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        builder.setConfig(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
    }

    @Test
    void embedWithRequiredArgs() {
        final var query =
                """
                WITH { token: $token, model: 'text-embedding-3-small' } AS conf
                RETURN ai.vector.embed('Hello!', 'openaiEmbedding', conf) AS result
                """;
        assertThat(db.executeTransactionally(query, tokenParam, consume()))
                .singleElement(resultMap())
                .extracting("result")
                .asInstanceOf(InstanceOfAssertFactories.type(VectorValue.class))
                .isNotNull();
    }

    @Test
    void embedWithAllArgs() {
        final var query =
                """
                WITH { token: $token, model: 'text-embedding-3-small', dimension: 1536 } AS conf
                RETURN ai.vector.embed('Hello!', 'openaiEmbedding', conf) AS result
                """;
        assertThat(db.executeTransactionally(query, tokenParam, consume()))
                .singleElement(resultMap())
                .extracting("result")
                .asInstanceOf(InstanceOfAssertFactories.type(VectorValue.class))
                .isNotNull();
    }

    @Test
    void batchEmbedWithRequiredArgs() {
        final var query =
                """
                CALL ai.vector.embedBatch(
                  ['Testing', null, 'Hello!'],
                  'openaiEmbedding',
                  { token: $token, model: 'text-embedding-3-small' }
                )
                """;
        assertThat(db.executeTransactionally(query, tokenParam, consume()))
                .satisfiesExactly(batchedNonBlankRow(0), batchedNullRow(1), batchedNonBlankRow(2));
    }

    @Test
    void batchEmbedWithAllArgs() {
        final var query =
                """
                CALL ai.vector.embedBatch(
                  ['Testing', null, 'Hello!'],
                  'openaiEmbedding',
                  { token: $token, model: 'text-embedding-3-small', dimension: 1536 }
                )
                """;
        assertThat(db.executeTransactionally(query, tokenParam, consume()))
                .satisfiesExactly(batchedNonBlankRow(0), batchedNullRow(1), batchedNonBlankRow(2));
    }

    private Consumer<Map<String, Object>> batchedNullRow(long index) {
        return row -> assertThat(row).containsEntry("index", index).containsEntry("vector", null);
    }

    private Consumer<Map<String, Object>> batchedNonBlankRow(long index) {
        return row -> assertThat(row).containsEntry("index", index).hasEntrySatisfying("vector", c -> assertThat(c)
                .asInstanceOf(InstanceOfAssertFactories.type(VectorValue.class))
                .isNotNull());
    }
}

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
package org.neo4j.genai.vector.providers;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(value = "authIsSet", disabledReason = "token needs to be set in the config map")
public class VertexAIIT {
    private static final String VERTEX_AI_TOKEN_ENV = "VERTEX_AI_TOKEN";
    private static final String VERTEX_AI_PROJECT_ID_ENV = "VERTEX_AI_PROJECT_ID";
    private static final Map<String, ?> BASE_CONFIG;

    static {
        HashMap<String, String> config = new HashMap<>();
        String token = System.getenv(VERTEX_AI_TOKEN_ENV);
        if (token != null) {
            config.put("token", token);
        }
        String projectId = System.getenv(VERTEX_AI_PROJECT_ID_ENV);
        if (token != null) {
            config.put("projectId", projectId);
        }

        BASE_CONFIG = config;
    }

    private static boolean authIsSet() {
        return BASE_CONFIG.containsKey("token");
    }

    @Nested
    class GeminiEmbedding001 extends BaseIT {
        GeminiEmbedding001() {
            super(
                    VertexAI.NAME,
                    "vertexai/gemini-embedding-001.txt",
                    BASE_CONFIG,
                    Map.of("model", "gemini-embedding-001"));
        }
    }

    @Nested
    class TextEmbedding005 extends BaseIT {
        TextEmbedding005() {
            super(VertexAI.NAME, "vertexai/text-embedding-005.txt", BASE_CONFIG, Map.of("model", "text-embedding-005"));
        }
    }

    @Nested
    class TextMultiLingualEmbedding002 extends BaseIT {
        TextMultiLingualEmbedding002() {
            super(
                    VertexAI.NAME,
                    "vertexai/text-multilingual-embedding-002.txt",
                    BASE_CONFIG,
                    Map.of("model", "text-multilingual-embedding-002"));
        }
    }

    @Nested
    class LargeBatchedInput extends BaseIT {
        LargeBatchedInput() {
            super(
                    VertexAI.NAME,
                    BASE_CONFIG,
                    Map.of(
                            "model",
                            VertexAI.KNOWN_BATCH_SUPPORTED_MODELS.iterator().next()));
        }
    }
}

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
package org.neo4j.genai.ai.text.completion.provider.openai;

import static org.neo4j.genai.util.HttpService.jsonBody;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.genai.ai.text.completion.TextCompletion;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.util.VisibleForTesting;

public interface OpenAiBase<PARAMS> extends TextCompletion.Provider.Implementation {

    URI endpoint();

    HttpService httpService();

    PARAMS params();

    String[] authHeader();

    void extendPayload(MutableMap<String, Object> payload);

    @Override
    default String complete(String prompt) {
        final var payload = payload(List.of(prompt));
        final var response = httpService()
                .request(
                        endpoint(),
                        builder -> builder.headers(
                                        "Content-Type",
                                        "application/json; charset=" + StandardCharsets.UTF_8,
                                        "Accept",
                                        "application/json")
                                .headers(authHeader())
                                .POST(jsonBody(payload))
                                .build(),
                        OpenAiBase::parseResponse);
        if (response.size() != 1) {
            throw new MalformedGenAIResponseException("Expected exactly one message, but found " + response.size());
        }
        return response.getFirst();
    }

    @VisibleForTesting
    private static List<String> parseResponse(InputStream inputStream) throws MalformedGenAIResponseException {
        final var response = JsonUtils.readValue(inputStream, ResponseModel.Response.class);
        return response.output().stream()
                .filter(o -> "message".equals(o.type()))
                .flatMap(o -> o.content().stream())
                .filter(c -> "output_text".equals(c.type()))
                .map(ResponseModel.Content::text)
                .toList();
    }

    private MutableMap<String, Object> payload(List<String> prompts) {
        final var payload = Maps.mutable.<String, Object>empty();
        extendPayload(payload);

        final var messages = prompts.stream()
                .map(prompt -> Map.of("role", "user", "content", prompt))
                .toList();
        payload.put("input", messages);

        return payload;
    }

    /*
     * {
     *   "output": [
     *     {
     *       "type": "message",
     *       "content": [
     *           {
     *             "type": "output_text",
     *             "text": "Hello! How can I assist you today?",
     *           }
     *       ],
     *     }
     *   ],
     * }
     */
    interface ResponseModel {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Content(@JsonProperty(required = true) String type, String text) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Output(@JsonProperty(required = true) String type, List<Content> content) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Response(@JsonProperty(required = true) List<Output> output) {}
    }
}

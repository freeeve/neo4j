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
package org.neo4j.genai.ai.text.completion.provider;

import static org.neo4j.genai.util.HttpService.jsonBody;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.genai.ai.text.completion.TextCompletion;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.genai.util.Parameters;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.virtual.MapValue;

public interface OpenAiBase<PARAMS> extends TextCompletion.Provider.Implementation {

    URI endpoint();

    HttpService httpService();

    Class<PARAMS> paramType();

    String[] authHeader(PARAMS params);

    void extendPayload(MutableMap<String, Object> payload, PARAMS params);

    @Override
    default String complete(String prompt, MapValue paramMap) {
        return complete(prompt, Parameters.parse(paramType(), paramMap));
    }

    private String complete(String prompt, PARAMS params) {
        final var payload = payload(List.of(prompt), params);
        final var response = httpService()
                .request(
                        endpoint(),
                        builder -> builder.headers(
                                        "Content-Type",
                                        "application/json; charset=" + StandardCharsets.UTF_8,
                                        "Accept",
                                        "application/json")
                                .headers(authHeader(params))
                                .POST(jsonBody(payload))
                                .build(),
                        OpenAiBase::parseResponse);
        if (response.size() != 1) {
            throw new MalformedGenAIResponseException("Expected exactly one message, but found " + response.size());
        }
        return response.getFirst();
    }

    /*
    {
      "choices": [
        {
          "index": 0,
          "message": {
            "role": "assistant",
            "content": "Hello! How can I assist you today?",
          },
        }
      ],
    }
        */
    @VisibleForTesting
    private static List<String> parseResponse(InputStream inputStream) throws MalformedGenAIResponseException {
        final var outputs = JsonUtils.readTree(inputStream).path("output");
        if (!outputs.isArray() || outputs.isEmpty()) {
            throw new MalformedGenAIResponseException("`output` is expected to be a non empty array");
        }
        final var messages = new ArrayList<String>(outputs.size());
        for (final var output : outputs) {
            if ("message".equals(output.path("type").asText())) {
                final var contents = output.path("content");
                if (!contents.isArray()) {
                    throw new MalformedGenAIResponseException("`content` is expected to be an array");
                }
                for (final var content : contents) {
                    if ("output_text".equals(content.path("type").asText())) {
                        messages.add(content.path("text").asText());
                    }
                }
            }
        }
        return messages;
    }

    private MutableMap<String, Object> payload(List<String> prompts, PARAMS params) {
        final var messages = prompts.stream()
                .map(prompt -> Map.of("role", "user", "content", prompt))
                .toList();

        final var payload = Maps.mutable.<String, Object>empty();
        payload.put("input", messages);
        extendPayload(payload, params);
        return payload;
    }
}

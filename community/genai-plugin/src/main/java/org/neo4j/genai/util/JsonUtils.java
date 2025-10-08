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
package org.neo4j.genai.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.genai.vector.VectorEncoding.BatchRow;

public final class JsonUtils {

    private static volatile ObjectMapper LAZY_OBJECT_MAPPER_INSTANCE;

    public static final TypeReference<float[]> TYPE_REF_FLOAT_VECTOR = new TypeReference<>() {};
    public static final TypeReference<Map<String, Object>> TYPE_REF_MAP_STRING_OBJECT = new TypeReference<>() {};
    public static final TypeReference<Map<String, Map<?, ?>>> TYPE_REF_MAP_STRING_MAP = new TypeReference<>() {};

    public static Stream<BatchRow> parseResponse(
            String name,
            String topLevelKey,
            String[] properties,
            List<String> resources,
            InputStream inputStream,
            int[] nullIndexes)
            throws MalformedGenAIResponseException {
        final JsonNode tree = readTree(inputStream);

        final var predictions = getExpectedFrom(name, tree, topLevelKey);
        if (!predictions.isArray()) {
            throw new MalformedGenAIResponseException("Expected response to contain an array of embeddings");
        } else if (predictions.size() != resources.size()) {
            throw new MalformedGenAIResponseException("Expected to receive %d embeddings; however got %d"
                    .formatted(resources.size(), predictions.size()));
        }

        final var offset = new MutableInt();
        return IntStream.range(0, resources.size() + nullIndexes.length).mapToObj(index -> {
            try {
                if (Arrays.binarySearch(nullIndexes, index) >= 0) {
                    offset.increment();
                    return new BatchRow(index, null, null);
                }
                final int offsetIndex = index - offset.intValue();
                final var embedding = getExpectedFrom(name, predictions.get(offsetIndex), properties);
                if (!embedding.isArray()) {
                    throw new MalformedGenAIResponseException("Expected embedding to be an array");
                }
                try (final var parser = embedding.traverse(getObjectMapper())) {
                    return new BatchRow(index, resources.get(offsetIndex), parser.readValueAs(TYPE_REF_FLOAT_VECTOR));
                } catch (IOException e) {
                    throw new MalformedGenAIResponseException(
                            "Unexpected error occurred while parsing the embedding", e);
                }
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
    }

    /**
     * Provides access to a shared, lazily initialized instance of an object mapper.
     * @return a shared object mapper
     */
    public static ObjectMapper getObjectMapper() {

        var objectMapper = LAZY_OBJECT_MAPPER_INSTANCE;
        if (objectMapper == null) {
            synchronized (JsonUtils.class) {
                objectMapper = LAZY_OBJECT_MAPPER_INSTANCE;
                if (objectMapper == null) {
                    LAZY_OBJECT_MAPPER_INSTANCE = new ObjectMapper();
                    objectMapper = LAZY_OBJECT_MAPPER_INSTANCE;
                }
            }
        }
        return objectMapper;
    }

    public static JsonNode readTree(InputStream inputStream) {
        try {
            return getObjectMapper().readTree(inputStream);
        } catch (IOException e) {
            throw new MalformedGenAIResponseException("Unexpected error occurred while parsing the API response", e);
        }
    }

    public static <T> T readValue(InputStream inputStream, Class<T> type) {
        try {
            return getObjectMapper().readValue(inputStream, type);
        } catch (IOException e) {
            throw new MalformedGenAIResponseException("Unexpected error occurred while parsing the API response", e);
        }
    }

    public static JsonNode getExpectedFrom(String provider, JsonNode json, String property)
            throws MalformedGenAIResponseException {
        try {
            if (!json.isObject()) {
                throw isNotObjectNode("provided json node");
            }
            return json.required(property);
        } catch (IllegalArgumentException e) {
            throw doesNotExist(provider, property, e);
        }
    }

    public static JsonNode getExpectedFrom(String provider, JsonNode json, String... properties)
            throws MalformedGenAIResponseException {
        var parent = "provided json node";
        for (final var property : properties) {
            if (!json.isObject()) {
                throw isNotObjectNode(parent);
            }

            json = getExpectedFrom(provider, json, property);
            parent = "'" + property + "'";
        }
        return json;
    }

    private static MalformedGenAIResponseException isNotObjectNode(String parent) {
        return new MalformedGenAIResponseException("Expected %s to be an object".formatted(parent));
    }

    private static MalformedGenAIResponseException doesNotExist(String provider, String property, Throwable cause) {
        return new MalformedGenAIResponseException(
                "'%s' is expected to exist in the response from %s".formatted(property, provider), cause);
    }

    private JsonUtils() {}
}

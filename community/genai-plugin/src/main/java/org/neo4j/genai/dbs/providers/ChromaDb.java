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
package org.neo4j.genai.dbs.providers;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.dbs.CollectionNotFoundException;
import org.neo4j.genai.dbs.RowMappingConfig;
import org.neo4j.genai.dbs.VectorDatabaseProvider;
import org.neo4j.genai.dbs.VectorDatabaseRequest;
import org.neo4j.genai.dbs.VectorDatabases;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;

/**
 *
 */
@ServiceProvider
public class ChromaDb implements VectorDatabaseProvider {

    private static final UnaryOperator<String> CREATE_BASE_URI = "%s/api/v1/collections"::formatted;

    private static final BinaryOperator<String> CREATE_COLLECTION_BASE_URI =
            (host, collection) -> CREATE_BASE_URI.apply(host) + "/" + collection;

    private static final BiFunction<String, String, String> CREATE_GET_POINTS_BASE_URI =
            CREATE_COLLECTION_BASE_URI.andThen(v -> v + "/get");
    private static final BiFunction<String, String, String> CREATE_UPSERT_POINTS_BASE_URI =
            CREATE_COLLECTION_BASE_URI.andThen(v -> v + "/upsert");
    private static final BiFunction<String, String, String> CREATE_QUERY_BASE_URI =
            CREATE_COLLECTION_BASE_URI.andThen(v -> v + "/query");

    private static final String IDS_KEY = "ids";

    @Override
    public <T> VectorDatabaseRequest<T> createRequestFor(
            Command command,
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {
        var rowMappingConfig = (RowMappingConfig) additionalArguments.get("rowMappingConfig");
        var procedureArguments = (VectorDatabases.ProcedureArguments) additionalArguments.get("procedureArguments");
        Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder =
                (httpRequestBuilder) -> addHttpVersion(addAuthorizationHeader(configuration, httpRequestBuilder));

        if (command == Command.GET_COLLECTION_METADATA) {
            return createGetCollectionMetadataRequest(host, collection, commonRequestBuilder);
        } else if (command == Command.GET) {
            return createGetRequest(
                    host, collection, additionalArguments, procedureArguments, rowMappingConfig, commonRequestBuilder);
        } else if (command == Command.QUERY) {
            return createQueryRequest(
                    host, collection, additionalArguments, procedureArguments, rowMappingConfig, commonRequestBuilder);
        } else if (command == Command.UPSERT) {
            return createUpsertRequest(host, collection, configuration, additionalArguments, commonRequestBuilder);
        } else if (command == Command.DELETE_COLLECTION) {
            return createDeleteCollectionRequest(host, collection, commonRequestBuilder);
        } else if (command == Command.CREATE_COLLECTION) {
            return createCreateCollectionRequest(host, collection, additionalArguments, commonRequestBuilder);
        } else if (command == Command.DELETE) {
            return createDeleteRequest(host, collection, configuration, additionalArguments, commonRequestBuilder);
        }
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteRequest(
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host, collection) + "/delete");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                var body =
                        JsonUtils.getObjectMapper().writeValueAsString(Map.of("ids", additionalArguments.get(IDS_KEY)));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), in -> (T) VectorDatabases.StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateCollectionRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_BASE_URI.apply(host));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                "name",
                                collection,
                                "metadata",
                                Map.of(
                                        "size",
                                        additionalArguments.get("size"),
                                        "hnsw:space",
                                        additionalArguments
                                                .get("similarity")
                                                .toString()
                                                .toLowerCase())));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), in -> (T) VectorDatabases.StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createUpsertRequest(
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_UPSERT_POINTS_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                List<List<Double>> embeddings = new ArrayList<>();
                List<Map<String, Object>> metadatas = new ArrayList<>();
                List<Object> ids = new ArrayList<>();
                List<Map<String, Object>> vectors = (List<Map<String, Object>>) additionalArguments.get("vectors");
                for (Map<String, Object> vector : vectors) {
                    embeddings.add((List<Double>) vector.get("vector"));
                    ids.add(vector.get("id"));
                    metadatas.add((Map<String, Object>) vector.get("metadata"));
                }

                String body =
                        JsonUtils.getObjectMapper().writeValueAsString(new UpsertPayload(embeddings, metadatas, ids));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<>(
                target, commonRequestBuilder.andThen(requestCustomizer), in -> (T) VectorDatabases.StatusDTO.ok(null));
    }

    @Override
    public BiFunction<Integer, String, Optional<GenAIProcedureException>> getProviderSpecificStatusHandler(
            String collection) {
        return (statusCode, message) -> {
            boolean collectionNotFoundMessageExists = message.contains("Collection " + collection + " does not exist.");
            if (statusCode == 400 && collectionNotFoundMessageExists) {
                return Optional.of(new CollectionNotFoundException(collection));
            }
            if (statusCode == 500 && collectionNotFoundMessageExists) {
                return Optional.of(new GenAIProcedureException(
                        // textually, we are showing it as 403 for behaviour compatibility but keep the 500
                        "API request forbidden (HTTP response code: 403); check your credentials.", 500));
            }
            return Optional.empty();
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createQueryRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            VectorDatabases.ProcedureArguments procedureArguments,
            RowMappingConfig rowMappingConfig,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_QUERY_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                Map<String, Object> requestParameters = new HashMap<>(Map.of(
                        "query_embeddings",
                        List.of(additionalArguments.get("vector")),
                        "n_results",
                        additionalArguments.get("limit"),
                        "include",
                        List.of("metadatas", "embeddings", "distances")));
                requestParameters.put(
                        "where", ((Optional<Map<?, ?>>) additionalArguments.get("filter")).orElseGet(Map::of));
                String body = JsonUtils.getObjectMapper().writeValueAsString(requestParameters);
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };
        Function<InputStream, ?> responseTransformer = inputStream -> {
            try {
                Map<String, Object> map =
                        JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                List<Map<String, Object>> ids =
                        (List<Map<String, Object>>) ((List<Map<String, Object>>) map.get("ids")).get(0);
                List<Map<String, Object>> embeddings =
                        (List<Map<String, Object>>) ((List<Map<String, Object>>) map.get("embeddings")).get(0);
                List<Map<String, Object>> metadatas =
                        (List<Map<String, Object>>) ((List<Map<String, Object>>) map.get("metadatas")).get(0);
                List<Map<String, Object>> distances =
                        (List<Map<String, Object>>) ((List<Map<String, Object>>) map.get("distances")).get(0);
                return IntStream.rangeClosed(0, ids.size() - 1).mapToObj(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.idKey(), ids.get(i));
                    row.put(rowMappingConfig.metadataKey(), metadatas.get(i));
                    row.put(rowMappingConfig.scoreKey(), distances.get(i));
                    if (procedureArguments.allResults()) {
                        if (embeddings != null && embeddings.get(i) != null) {
                            row.put(rowMappingConfig.vectorKey(), embeddings.get(i));
                        }
                    }
                    return row;
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), (Function<InputStream, T>)
                        responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            VectorDatabases.ProcedureArguments procedureArguments,
            RowMappingConfig rowMappingConfig,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_GET_POINTS_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                String body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                IDS_KEY,
                                additionalArguments.get(IDS_KEY),
                                "include",
                                List.of("metadatas", "embeddings")));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };
        Result result = new Result(target, commonRequestBuilder.andThen(requestCustomizer));

        Function<InputStream, Stream<Map<String, Object>>> resultTransformer = (inputStream -> {
            try {
                Map<String, Object> map =
                        JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                List<Map<String, Object>> ids = (List<Map<String, Object>>) map.get("ids");
                List<Map<String, Object>> embeddings = (List<Map<String, Object>>) map.get("embeddings");
                List<Map<String, Object>> metadatas = (List<Map<String, Object>>) map.get("metadatas");
                return IntStream.rangeClosed(0, ids.size() - 1).mapToObj(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.idKey(), ids.get(i));
                    row.put(rowMappingConfig.metadataKey(), metadatas.get(i));
                    if (procedureArguments.allResults()) {
                        if (embeddings != null && embeddings.get(i) != null) {
                            row.put(rowMappingConfig.vectorKey(), embeddings.get(i));
                        }
                    }
                    return row;
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return new VectorDatabaseRequest<>(
                result.target(), result.requestCustomizer(), (Function<InputStream, T>) resultTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetCollectionMetadataRequest(
            String host, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host, collection));

        Function<InputStream, T> responseTransformer = (Function<InputStream, T>)
                HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(VectorDatabases.InfoDTO::of);

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(HttpRequest.Builder::build), responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteCollectionRequest(
            String host, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer =
                httpRequestBuilder -> httpRequestBuilder.DELETE().build();
        Function<InputStream, ?> responseTransformer = HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(
                something -> new VectorDatabases.StatusDTO("ok", Map.of()));

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), (Function<InputStream, T>)
                        responseTransformer);
    }

    private static HttpRequest.Builder addHttpVersion(HttpRequest.Builder httpRequestBuilder) {
        return httpRequestBuilder.version(HttpClient.Version.HTTP_1_1);
    }

    private record Result(URI target, Function<HttpRequest.Builder, HttpRequest> requestCustomizer) {}

    private record UpsertPayload(
            List<List<Double>> embeddings, List<Map<String, Object>> metadatas, List<Object> ids) {}
}

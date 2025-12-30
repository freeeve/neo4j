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
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.dbs.RequestConfig;
import org.neo4j.genai.dbs.RowMappingConfig;
import org.neo4j.genai.dbs.VectorDatabaseProvider;
import org.neo4j.genai.dbs.VectorDatabaseRequest;
import org.neo4j.genai.dbs.VectorDatabases;
import org.neo4j.genai.dbs.VectorDatabases.ProcedureArguments;
import org.neo4j.genai.dbs.VectorDatabases.StatusDTO;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;

/**
 * Implements access <a href="https://qdrant.tech">Qdrant</a>, either to a custom hosted instance or to the Qdrant Cloud.
 */
@ServiceProvider
public final class Qdrant implements VectorDatabaseProvider {

    private static final BiFunction<String, String, String> CREATE_COLLECTION_BASE_URI =
            (host, collection) -> host + "/collections/" + collection;

    private static final BiFunction<String, String, String> CREATE_POINTS_BASE_URI =
            (host, collection) -> CREATE_COLLECTION_BASE_URI.apply(host, collection) + "/points";

    private static final String POINTS_KEY = "points";
    private static final String IDS_KEY = "ids";

    @Override
    public <T> VectorDatabaseRequest<T> createRequestFor(
            Command command,
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {

        var rowMappingConfig = (RowMappingConfig) additionalArguments.get("rowMappingConfig");
        var procedureArguments = (ProcedureArguments) additionalArguments.get("procedureArguments");
        Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder =
                (httpRequestBuilder) -> addAuthorizationHeader(configuration, httpRequestBuilder);

        if (command == Command.GET_COLLECTION_METADATA) {
            return createGetCollectionMetadataRequest(host, collection, commonRequestBuilder);
        } else if (command == Command.GET) {
            return createGetRequest(
                    host, collection, additionalArguments, procedureArguments, rowMappingConfig, commonRequestBuilder);
        } else if (command == Command.DELETE_COLLECTION) {
            return createDeleteCollectionRequest(host, collection, commonRequestBuilder);
        } else if (command == Command.CREATE_COLLECTION) {
            return createCreateCollectionRequest(host, collection, additionalArguments, commonRequestBuilder);
        } else if (command == Command.UPSERT) {
            return createUpsertRequest(host, collection, configuration, additionalArguments, commonRequestBuilder);
        } else if (command == Command.QUERY) {
            return createQueryRequest(
                    host, collection, additionalArguments, procedureArguments, rowMappingConfig, commonRequestBuilder);
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
        URI target = URI.create(CREATE_POINTS_BASE_URI.apply(host, collection) + "/delete?wait="
                + RequestConfig.Keys.WAIT.get(Boolean.class, configuration));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(POINTS_KEY, additionalArguments.get(IDS_KEY)));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        Function<InputStream, T> responseTransformer =
                (Function<InputStream, T>) HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(response -> {
                    var result = (Map<String, Object>) response.get("result");
                    var status = (String) response.get("status");
                    if (!"ok".equals(status)) {
                        return StatusDTO.failure(result);
                    }
                    return StatusDTO.ok(result);
                });

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createQueryRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            ProcedureArguments procedureArguments,
            RowMappingConfig rowMappingConfig,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_POINTS_BASE_URI.apply(host, collection) + "/search");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                Map<String, Object> requestParameters = new HashMap<>(Map.of(
                        "vector",
                        additionalArguments.get("vector"),
                        "limit",
                        additionalArguments.get("limit"),
                        "with_payload",
                        true, // always return payload
                        "with_vector",
                        procedureArguments.allResults()));
                if (((Optional<String>) additionalArguments.get("filter")).isPresent()) {
                    requestParameters.put("filter", ((Optional<?>) additionalArguments.get("filter")).get());
                }
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
                var result = JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                var data = (List<Map<String, Object>>) result.get("result");
                return data.stream().map(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.metadataKey(), i.get("payload"));
                    row.put(rowMappingConfig.scoreKey(), i.get("score"));
                    row.put(rowMappingConfig.idKey(), i.get("id"));
                    row.put(rowMappingConfig.vectorKey(), i.get("vector"));
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
    private static <T> VectorDatabaseRequest<T> createUpsertRequest(
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_POINTS_BASE_URI.apply(host, collection) + "?wait="
                + RequestConfig.Keys.WAIT.get(Boolean.class, configuration));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                List<Map<String, Object>> points = new ArrayList<>();
                for (Map<String, Object> vector : ((List<Map<String, Object>>) additionalArguments.get("vectors"))) {
                    Map<String, Object> point = HashMap.newHashMap(3);
                    point.put("id", vector.get("id"));
                    point.put("payload", vector.get("metadata"));
                    point.put("vector", vector.get("vector"));
                    points.add(point);
                }

                String body = JsonUtils.getObjectMapper().writeValueAsString(Map.of(POINTS_KEY, points));
                return httpRequestBuilder
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<>(
                target, commonRequestBuilder.andThen(requestCustomizer), in -> (T) StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateCollectionRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                "vectors",
                                Map.of(
                                        "size",
                                        additionalArguments.get("size"),
                                        "distance",
                                        additionalArguments.get("similarity"))));
                return httpRequestBuilder
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), in -> (T) StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteCollectionRequest(
            String host, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer =
                httpRequestBuilder -> httpRequestBuilder.DELETE().build();
        Function<InputStream, ?> responseTransformer =
                HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(qdrantStatus -> {
                    if (!"ok".equalsIgnoreCase((String) qdrantStatus.getOrDefault("status", "ok"))) {
                        return StatusDTO.unknown(null);
                    }
                    return StatusDTO.ok(null);
                });

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(requestCustomizer), (Function<InputStream, T>)
                        responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            ProcedureArguments procedureArguments,
            RowMappingConfig rowMappingConfig,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_POINTS_BASE_URI.apply(host, collection));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                String body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                IDS_KEY,
                                additionalArguments.get(IDS_KEY),
                                "with_vector",
                                procedureArguments.allResults(),
                                "with_payload",
                                true));
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
                List<Map<String, Object>> resultList = (List<Map<String, Object>>) map.get("result");
                return resultList.stream().map(resultEntry -> {
                    Map<String, Object> row = new HashMap<>();
                    resultEntry.forEach((k, v) -> {
                        if ("payload".equals(k)) {
                            row.put(rowMappingConfig.metadataKey(), v);
                        } else if (!"id".equals(k) || procedureArguments.allResults()) {
                            row.put(k, v);
                        }
                    });
                    return row;
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return new VectorDatabaseRequest<T>(
                result.target(), result.requestCustomizer(), (Function<InputStream, T>) resultTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetCollectionMetadataRequest(
            String host, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host, collection));

        Function<InputStream, T> responseTransformer =
                (Function<InputStream, T>) HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(
                        response -> VectorDatabases.InfoDTO.of((Map<String, Object>) response.get("result")));

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(HttpRequest.Builder::build), responseTransformer);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public BiFunction<Integer, String, Optional<GenAIProcedureException>> getProviderSpecificStatusHandler(
            String collection) {
        return (statusCode, message) -> {
            if (statusCode == 400) {
                try {
                    var response = JsonUtils.getObjectMapper().readValue(message, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                    var status = response.get("status");
                    if (status instanceof Map m && m.containsKey("error")) {
                        return Optional.of(new GenAIProcedureException((String) m.get("error")));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return Optional.of(new GenAIProcedureException(message));
            }

            return Optional.empty();
        };
    }

    private record Result(URI target, Function<HttpRequest.Builder, HttpRequest> requestCustomizer) {}
}

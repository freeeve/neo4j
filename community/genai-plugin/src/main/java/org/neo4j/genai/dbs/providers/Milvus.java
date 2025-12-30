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
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.dbs.CollectionNotFoundException;
import org.neo4j.genai.dbs.RowMappingConfig;
import org.neo4j.genai.dbs.VectorDatabaseProvider;
import org.neo4j.genai.dbs.VectorDatabaseRequest;
import org.neo4j.genai.dbs.VectorDatabases;
import org.neo4j.genai.dbs.VectorDatabases.ProcedureArguments;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;

/**
 * Implements access <a href="https://qdrant.tech">Milvus</a>, either to a custom hosted instance or to the Zilliz Cloud.
 */
@ServiceProvider
public final class Milvus implements VectorDatabaseProvider {

    private static final UnaryOperator<String> CREATE_COLLECTION_BASE_URI = (host) -> host + "/v2/vectordb/collections";

    private static final UnaryOperator<String> CREATE_VECTOR_BASE_URI = (host) -> host + "/v2/vectordb/entities";

    private static final String DATA_KEY = "data";
    private static final String IDS_KEY = "ids";
    private static final String ID_KEY = "id";

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
        URI target = URI.create(CREATE_VECTOR_BASE_URI.apply(host) + "/delete");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of("collectionName", collection, "filter", createIdFilter((List<Object>)
                                additionalArguments.get(IDS_KEY))));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<T>(target, commonRequestBuilder.andThen(requestCustomizer), in -> {
            Map<String, Object> result = null;
            try {
                result = JsonUtils.getObjectMapper().readValue(in, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (result.get("message") != null && result.get("message") instanceof String message) {
                if (message.contains("can't find collection")) {
                    throw new CollectionNotFoundException(collection);
                }
            }
            return (T) VectorDatabases.StatusDTO.ok(null);
        });
    }

    private static String createIdFilter(List<Object> ids) {
        return ids.stream()
                .map(Object::toString)
                .map(stringId -> "id == " + stringId)
                .collect(Collectors.joining(" || "));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createQueryRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            ProcedureArguments procedureArguments,
            RowMappingConfig rowMappingConfig,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_VECTOR_BASE_URI.apply(host) + "/search");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                Map<String, Object> requestParameters = new HashMap<>(Map.of(
                        "collectionName",
                        collection,
                        "outputFields",
                        List.of("payload", "vector"),
                        "data",
                        List.of(additionalArguments.get("vector")),
                        "filter",
                        ((Optional<String>) additionalArguments.get("filter")).orElse(""),
                        "limit",
                        additionalArguments.get("limit")));
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
                if (result.get("message") != null && result.get("message") instanceof String message) {
                    if (message.contains("can't find collection")) {
                        throw new CollectionNotFoundException(collection);
                    }
                }
                var data = (List<Map<String, Object>>) result.get("data");
                return data.stream().map(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.metadataKey(), i.get("payload"));
                    row.put(rowMappingConfig.scoreKey(), i.get("distance"));
                    Object idValue = i.get("id");
                    Long outputIdValue = null;
                    if (idValue instanceof Integer integerValue) {
                        outputIdValue = integerValue.longValue();
                    } else if (idValue instanceof Long longValue) {
                        outputIdValue = longValue;
                    } else {
                        throw new IllegalStateException("Could not convert " + idValue + " to a long value.");
                    }
                    row.put(rowMappingConfig.idKey(), outputIdValue);
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
        URI target = URI.create(CREATE_VECTOR_BASE_URI.apply(host) + "/upsert");
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

                String body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(DATA_KEY, points, "collectionName", collection));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<>(target, commonRequestBuilder.andThen(requestCustomizer), in -> {
            Map<String, Object> result = null;
            try {
                result = JsonUtils.getObjectMapper().readValue(in, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (result.get("message") != null && result.get("message") instanceof String message) {
                if (message.contains("can't find collection")) {
                    throw new CollectionNotFoundException(collection);
                }
            }
            return (T) VectorDatabases.StatusDTO.ok(null);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateCollectionRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host) + "/create");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                "collectionName",
                                collection,
                                "dimension",
                                additionalArguments.get("size"),
                                "metricType",
                                additionalArguments.get("similarity").toString().toUpperCase()));
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };
        return new VectorDatabaseRequest<T>(target, commonRequestBuilder.andThen(requestCustomizer), in -> {
            Map<String, Object> result = null;
            try {
                result = JsonUtils.getObjectMapper().readValue(in, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (result.get("message") != null && result.get("message") instanceof String message) {
                if (message.contains("can't find collection")) {
                    throw new CollectionNotFoundException(collection);
                }
            }
            return (T) VectorDatabases.StatusDTO.ok(null);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteCollectionRequest(
            String host, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host) + "/drop");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(
                                JsonUtils.getObjectMapper().writeValueAsString(Map.of("collectionName", collection))))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<T>(target, commonRequestBuilder.andThen(requestCustomizer), in -> {
            Map<String, Object> result = null;
            try {
                result = JsonUtils.getObjectMapper().readValue(in, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (result.get("message") != null && result.get("message") instanceof String message) {
                if (message.contains("can't find collection")) {
                    throw new CollectionNotFoundException(collection);
                }
            }
            return (T) VectorDatabases.StatusDTO.ok(null);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetRequest(
            String host,
            String collection,
            Map<String, Object> additionalArguments,
            ProcedureArguments procedureArguments,
            RowMappingConfig rowMappingConfig,
            Function<HttpRequest.Builder, HttpRequest.Builder> commonRequestBuilder) {
        URI target = URI.create(CREATE_VECTOR_BASE_URI.apply(host) + "/get");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = httpRequestBuilder -> {
            try {
                String body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                ID_KEY,
                                additionalArguments.get(IDS_KEY),
                                "collectionName",
                                collection,
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
                List<Map<String, Object>> resultList = (List<Map<String, Object>>) map.get("data");
                return resultList.stream().map(resultEntry -> {
                    if (resultEntry.get("message") != null && resultEntry.get("message") instanceof String message) {
                        if (message.contains("can't find collection")) {
                            throw new CollectionNotFoundException(collection);
                        }
                    }
                    Map<String, Object> row = new HashMap<>();
                    resultEntry.forEach((k, v) -> {
                        if ("payload".equals(k)) {
                            row.put(rowMappingConfig.metadataKey(), v);
                        } else if (!"id".equals(k) || procedureArguments.allResults()) {
                            if ("id".equals(k)) {
                                Long outputIdValue = null;
                                if (v instanceof Integer integerValue) {
                                    outputIdValue = integerValue.longValue();
                                } else if (v instanceof Long longValue) {
                                    outputIdValue = longValue;
                                } else {
                                    throw new IllegalStateException("Could not convert " + v + " to a long value.");
                                }
                                row.put(k, outputIdValue);
                            } else {
                                row.put(k, v);
                            }
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
        URI target = URI.create(CREATE_COLLECTION_BASE_URI.apply(host) + "/describe");

        Function<InputStream, T> responseTransformer =
                (Function<InputStream, T>) HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(vendor -> {
                    if (vendor.get("message") != null && vendor.get("message") instanceof String message) {
                        if (message.contains("can't find collection")) {
                            throw new CollectionNotFoundException(collection);
                        }
                    }
                    return VectorDatabases.InfoDTO.of(vendor);
                });

        Function<HttpRequest.Builder, HttpRequest> httpRequestCustomizer = (httpRequestBuilder) -> {
            try {
                return httpRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(
                                JsonUtils.getObjectMapper().writeValueAsString(Map.of("collectionName", collection))))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        return new VectorDatabaseRequest<T>(
                target, commonRequestBuilder.andThen(httpRequestCustomizer), responseTransformer);
    }

    private record Result(URI target, Function<HttpRequest.Builder, HttpRequest> requestCustomizer) {}
}

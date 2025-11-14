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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.dbs.RowMappingConfig;
import org.neo4j.genai.dbs.VectorDatabaseProvider;
import org.neo4j.genai.dbs.VectorDatabaseRequest;
import org.neo4j.genai.dbs.VectorDatabases.InfoDTO;
import org.neo4j.genai.dbs.VectorDatabases.ProcedureArguments;
import org.neo4j.genai.dbs.VectorDatabases.StatusDTO;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;

/**
 * Implements access <a href="https://weaviate.io">Weaviate</a>, either to a custom hosted instance or to the Weaviate Cloud.
 */
@ServiceProvider
public final class Weaviate implements VectorDatabaseProvider {

    @SuppressWarnings("unchecked")
    @Override
    public <T> VectorDatabaseRequest<T> createRequestFor(
            Command command,
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {

        ProcedureArguments procedureArguments = (ProcedureArguments) additionalArguments.get("procedureArguments");

        String baseUrl = host + "/v1";
        Function<HttpRequest.Builder, HttpRequest.Builder> prep =
                (httpRequestBuilder) -> addAuthorizationHeader(configuration, httpRequestBuilder);

        if (command == Command.CREATE_COLLECTION) {
            return createCreateCollectionRequest(baseUrl, collection, prep, additionalArguments);
        } else if (command == Command.GET_COLLECTION_METADATA) {
            return createGetCollectionMetadataRequest(baseUrl, collection, prep);
        } else if (command == Command.GET) {
            return createGetRequest(baseUrl, collection, prep, additionalArguments, procedureArguments);
        } else if (command == Command.QUERY) {
            return createQueryRequest(
                    baseUrl, collection, prep, additionalArguments, procedureArguments, configuration);
        } else if (command == Command.DELETE_COLLECTION) {
            return createDeleteCollectionRequest(baseUrl, collection, prep);
        } else if (command == Command.DELETE) {
            return createDeleteRequest(baseUrl, collection, prep, additionalArguments);
        } else if (command == Command.UPSERT) {
            return createCreateRequest(baseUrl, collection, prep, additionalArguments);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public <T> VectorDatabaseRequest<T> handleFailedUpsertRequest(
            GenAIProcedureException exception,
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {
        if (exception.getOptionalHttpCode().filter(code -> code == 422).isPresent()
                && exception.getMessage().contains("already exists")) {
            additionalArguments = new HashMap<>(additionalArguments);
            additionalArguments.put("forceUpdate", true);
            return createRequestFor(Command.UPSERT, host, collection, configuration, additionalArguments);
        }
        throw exception;
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateCollectionRequest(
            String baseUrl,
            String collection,
            Function<HttpRequest.Builder, HttpRequest.Builder> prep,
            Map<String, Object> additionalArguments) {

        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = prep.andThen(httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                "class",
                                collection,
                                "vectorIndexConfig",
                                Map.of(
                                        "distance",
                                        ((String) additionalArguments.get("similarity")).toLowerCase(Locale.ROOT),
                                        "size",
                                        additionalArguments.get("size"))));
                return httpRequestBuilder
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        return new VectorDatabaseRequest<T>(
                URI.create(baseUrl + "/schema"), requestCustomizer, in -> (T) StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteRequest(
            String baseUrl,
            String collection,
            Function<HttpRequest.Builder, HttpRequest.Builder> preparingRequestCustomizer,
            Map<String, Object> additionalArguments) {
        var ids = additionalArguments.get("ids");

        String body;
        try {
            body = JsonUtils.getObjectMapper()
                    .writeValueAsString(Map.of(
                            "output",
                            "verbose",
                            "dryRun",
                            false,
                            "match",
                            Map.of(
                                    "class",
                                    collection,
                                    "where",
                                    Map.of(
                                            "path",
                                            List.of("id"),
                                            "operator",
                                            "ContainsAny",
                                            "valueStringArray",
                                            ids))));

        } catch (JsonProcessingException e) {
            throw new GenAIProcedureException("Failed to create body for batch deletion of vectors");
        }

        URI target = URI.create(baseUrl + "/batch/objects");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = preparingRequestCustomizer.andThen(builder -> {
            builder.header("Content-Type", "application/json");
            return builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                    .build();
        });

        Function<InputStream, T> responseTransformer =
                (Function<InputStream, T>) HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(response -> {
                    var results = (Map<String, Object>) response.get("results");
                    var matches = results.get("matches");
                    var successful = results.get("successful");
                    if (!matches.equals(successful)) {
                        return StatusDTO.failure(results);
                    }
                    return StatusDTO.ok(results);
                });

        return new VectorDatabaseRequest<>(target, requestCustomizer, responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetCollectionMetadataRequest(
            String baseUrl, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> prep) {

        Function<InputStream, T> responseTransformer =
                (Function<InputStream, T>) HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(InfoDTO::of);

        return new VectorDatabaseRequest<>(
                URI.create(baseUrl + "/schema/" + collection),
                prep.andThen(HttpRequest.Builder::build),
                responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteCollectionRequest(
            String baseUrl, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> prep) {
        return new VectorDatabaseRequest<T>(
                URI.create(baseUrl + "/schema/" + collection),
                prep.andThen(HttpRequest.Builder::DELETE).andThen(HttpRequest.Builder::build),
                in -> (T) StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateRequest(
            String baseUrl,
            String collection,
            Function<HttpRequest.Builder, HttpRequest.Builder> prep,
            Map<String, Object> additionalArguments) {

        var vector = (Map<String, Object>) additionalArguments.get("vector");
        URI target;
        var forceUpdate = Boolean.TRUE.equals(additionalArguments.get("forceUpdate")) && vector.containsKey("id");
        if (forceUpdate) {
            target = URI.create(baseUrl + "/objects/" + vector.get("id"));
        } else {
            target = URI.create(baseUrl + "/objects");
        }

        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = prep.andThen(httpRequestBuilder -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("class", collection);
                body.put("properties", vector.get("metadata"));
                vector.forEach((k, v) -> {
                    if (!"metadata".equals(k)) {
                        body.put(k, v);
                    }
                });
                return httpRequestBuilder
                        .header("Content-Type", "application/json")
                        .method(
                                forceUpdate ? "PUT" : "POST",
                                HttpRequest.BodyPublishers.ofString(
                                        JsonUtils.getObjectMapper().writeValueAsString(body)))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return new VectorDatabaseRequest<>(target, requestCustomizer, in -> (T) StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetRequest(
            String baseUrl,
            String collection,
            Function<HttpRequest.Builder, HttpRequest.Builder> preparingRequestCustomizer,
            Map<String, Object> additionalArguments,
            ProcedureArguments procedureArguments) {
        var id = additionalArguments.get("id");

        URI target = URI.create(baseUrl + "/objects/" + collection + "/" + id
                + (procedureArguments.hasVector() ? "?include=vector" : ""));
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer =
                preparingRequestCustomizer.andThen(HttpRequest.Builder::GET).andThen(HttpRequest.Builder::build);
        var rowMappingConfig = (RowMappingConfig) additionalArguments.get("rowMappingConfig");
        Function<InputStream, T> responseTransformer = inputStream -> {
            try {
                var result = JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                var row = new HashMap<String, Object>();
                row.put(rowMappingConfig.idKey(), result.get("id"));
                row.put(rowMappingConfig.metadataKey(), result.get("properties"));
                row.put(rowMappingConfig.vectorKey(), result.get("vector"));
                row.put(rowMappingConfig.scoreKey(), result.get("score"));
                return (T) row;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return new VectorDatabaseRequest<>(target, requestCustomizer, responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createQueryRequest(
            String baseUrl,
            String collection,
            Function<HttpRequest.Builder, HttpRequest.Builder> preparingRequestCustomizer,
            Map<String, Object> additionalArguments,
            ProcedureArguments procedureArguments,
            Map<String, Object> configuration) {
        var rowMappingConfig = (RowMappingConfig) additionalArguments.get("rowMappingConfig");
        var vector = (List<Double>) additionalArguments.get("vector");

        var filter = ((Optional<Object>) additionalArguments.get("filter"))
                .map(f -> ", where: " + f)
                .orElse("");
        var limit = (long) additionalArguments.get("limit");
        var fieldList = String.join(
                "\n",
                Objects.requireNonNull(
                        RowMappingConfig.Keys.FIELDS.get(List.class, configuration),
                        "You have to define `field` list of parameter to be returned"));

        var queryTemplate = """
                {
                  Get {
                    %s(limit: %s, nearVector: {vector: %s } %s) {%s  %s}
                  }
                }""";

        var query = queryTemplate.formatted(
                collection,
                limit,
                vector,
                filter,
                fieldList,
                "_additional {id, distance " + (procedureArguments.hasVector() ? ",vector" : "") + "}");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer =
                preparingRequestCustomizer.andThen(httpRequestBuilder -> {
                    httpRequestBuilder.header("Content-Type", "application/json");
                    try {
                        httpRequestBuilder.POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                .writeValueAsString(Map.<String, Object>of("query", query))));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return httpRequestBuilder.build();
                });

        Function<InputStream, T> responseTransformer = inputStream -> {
            try {
                var result = JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                if (result.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
                    var message = new StringBuilder();
                    errors.forEach(error -> message.append(((Map<String, ?>) error).get("message")));
                    throw new GenAIProcedureException(message.toString());
                }
                Map<String, ?> dataMap = (Map<String, ?>) result.get("data");
                Map<String, ?> getMap = (Map<String, ?>) dataMap.get("Get");
                List<Map<String, Object>> data = (List<Map<String, Object>>) getMap.get(collection);
                return (T) data.stream().map(i -> {
                    Map<String, Object> additional = (Map<String, Object>) i.remove("_additional");
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.metadataKey(), i);
                    row.put(rowMappingConfig.scoreKey(), additional.get("distance"));
                    row.put(rowMappingConfig.idKey(), additional.get("id"));
                    row.put(rowMappingConfig.vectorKey(), additional.get("vector"));
                    return row;
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return new VectorDatabaseRequest<>(URI.create(baseUrl + "/graphql"), requestCustomizer, responseTransformer);
    }

    @Override
    public boolean supportsMultipleGet() {
        return false;
    }

    @Override
    public boolean supportsMultipleUpserts() {
        return false;
    }
}

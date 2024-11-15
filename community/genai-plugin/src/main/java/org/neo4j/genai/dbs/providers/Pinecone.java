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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.dbs.RequestConfig;
import org.neo4j.genai.dbs.RowMappingConfig;
import org.neo4j.genai.dbs.VectorDatabaseProvider;
import org.neo4j.genai.dbs.VectorDatabaseRequest;
import org.neo4j.genai.dbs.VectorDatabases;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;

@ServiceProvider
public final class Pinecone implements VectorDatabaseProvider {

    private static final EnumSet<Command> DATA_PLANE_COMMANDS =
            EnumSet.of(Command.QUERY, Command.GET, Command.UPSERT, Command.DELETE);

    @Override
    public <T> VectorDatabaseRequest<T> createRequestFor(
            Command command,
            String baseUrl,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {

        VectorDatabases.ProcedureArguments procedureArguments =
                (VectorDatabases.ProcedureArguments) additionalArguments.get("procedureArguments");
        Function<HttpRequest.Builder, HttpRequest.Builder> prep =
                (httpRequestBuilder) -> addAuthorizationHeader(configuration, httpRequestBuilder);

        if (command == Command.CREATE_COLLECTION) {
            return createCreateCollectionRequest(baseUrl, collection, prep, configuration, additionalArguments);
        } else if (command == Command.GET_COLLECTION_METADATA) {
            return createGetCollectionMetadataRequest(baseUrl, collection, prep);
        } else if (command == Command.GET) {
            return createGetRequest(baseUrl, prep, additionalArguments);
        } else if (command == Command.QUERY) {
            return createQueryRequest(baseUrl, prep, additionalArguments, procedureArguments);
        } else if (command == Command.DELETE_COLLECTION) {
            return createDeleteCollectionRequest(baseUrl, collection, prep);
        } else if (command == Command.DELETE) {
            return createDeleteRequest(baseUrl, prep, additionalArguments);
        } else if (command == Command.UPSERT) {
            return createCreateRequest(baseUrl, prep, additionalArguments);
        }

        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateCollectionRequest(
            String baseUrl,
            String collection,
            Function<HttpRequest.Builder, HttpRequest.Builder> prep,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {

        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = prep.andThen(httpRequestBuilder -> {
            try {
                var body = JsonUtils.getObjectMapper()
                        .writeValueAsString(Map.of(
                                "name",
                                collection,
                                "dimension",
                                additionalArguments.get("size"),
                                "metric",
                                ((String) additionalArguments.get("similarity")).toLowerCase(Locale.ROOT),
                                "spec",
                                configuration.getOrDefault("spec", Map.of())));
                return httpRequestBuilder
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        return new VectorDatabaseRequest<T>(
                URI.create(baseUrl + "/indexes"), requestCustomizer, in -> (T) VectorDatabases.StatusDTO.ok(null));
    }

    @Override
    @SuppressWarnings("unchecked")
    public HttpRequest.Builder addAuthorizationHeader(
            Map<String, Object> configuration, HttpRequest.Builder httpRequestBuilder) {
        Map<String, Object> headerConfig = RequestConfig.Keys.HEADERS.get(Map.class, configuration);
        if (headerConfig != null && headerConfig.containsKey(RequestConfig.Keys.AUTHORIZATION.key())) {
            httpRequestBuilder.header("Api-Key", (String) headerConfig.get(RequestConfig.Keys.AUTHORIZATION.key()));
        } else if (configuration.containsKey(RequestConfig.Keys.TOKEN.key())) {
            httpRequestBuilder =
                    httpRequestBuilder.header("Api-Key", (String) configuration.get(RequestConfig.Keys.TOKEN.key()));
        }
        return httpRequestBuilder;
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetCollectionMetadataRequest(
            String baseUrl, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> prep) {
        Function<InputStream, T> responseTransformer = (Function<InputStream, T>)
                HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(VectorDatabases.InfoDTO::of);

        return new VectorDatabaseRequest<>(
                URI.create(baseUrl + "/indexes/" + collection),
                prep.andThen(HttpRequest.Builder::build),
                responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createGetRequest(
            String baseUrl,
            Function<HttpRequest.Builder, HttpRequest.Builder> preparingRequestCustomizer,
            Map<String, Object> additionalArguments) {

        var ids = (List<Object>) additionalArguments.get("ids");
        URI target = URI.create(baseUrl + "/vectors/fetch?ids="
                + ids.stream().map(Object::toString).collect(Collectors.joining("&ids=")));

        Function<HttpRequest.Builder, HttpRequest> requestCustomizer =
                preparingRequestCustomizer.andThen(HttpRequest.Builder::GET).andThen(HttpRequest.Builder::build);
        var rowMappingConfig = (RowMappingConfig) additionalArguments.get("rowMappingConfig");
        Function<InputStream, T> responseTransformer = inputStream -> {
            try {
                var result = JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                var vectors = (Map<String, Map<String, Object>>) result.get("vectors");
                return (T) vectors.values().stream().map(vector -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.idKey(), vector.get("id"));
                    row.put(rowMappingConfig.metadataKey(), vector.get("metadata"));
                    row.put(rowMappingConfig.vectorKey(), vector.get("values"));
                    return row;
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return new VectorDatabaseRequest<>(target, requestCustomizer, responseTransformer);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createQueryRequest(
            String baseUrl,
            Function<HttpRequest.Builder, HttpRequest.Builder> preparingRequestCustomizer,
            Map<String, Object> additionalArguments,
            VectorDatabases.ProcedureArguments procedureArguments) {

        var rowMappingConfig = (RowMappingConfig) additionalArguments.get("rowMappingConfig");
        var vector = (List<Double>) additionalArguments.get("vector");
        var limit = (long) additionalArguments.get("limit");

        var body = new HashMap<String, Object>();
        body.put("includeMetadata", true);
        body.put("vector", vector);
        body.put("topK", limit);
        body.put("includeValues", procedureArguments.hasVector());
        ((Optional<Object>) additionalArguments.get("filter")).ifPresent(filter -> body.put("filter", filter));

        Function<HttpRequest.Builder, HttpRequest> requestCustomizer =
                preparingRequestCustomizer.andThen(httpRequestBuilder -> {
                    httpRequestBuilder.header("Content-Type", "application/json");
                    try {
                        httpRequestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                                JsonUtils.getObjectMapper().writeValueAsString(body)));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return httpRequestBuilder.build();
                });

        Function<InputStream, T> responseTransformer = inputStream -> {
            try {
                var result = JsonUtils.getObjectMapper().readValue(inputStream, JsonUtils.TYPE_REF_MAP_STRING_OBJECT);
                var data = (List<Map<String, Object>>) result.get("matches");
                return (T) data.stream().map(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put(rowMappingConfig.metadataKey(), i.get("metadata"));
                    row.put(rowMappingConfig.scoreKey(), i.get("score"));
                    row.put(rowMappingConfig.idKey(), i.get("id"));
                    row.put(rowMappingConfig.vectorKey(), i.get("values"));
                    return row;
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return new VectorDatabaseRequest<>(URI.create(baseUrl + "/query"), requestCustomizer, responseTransformer);
    }

    @Override
    public Optional<VectorDatabaseRequest<String>> getCollectionSpecificHost(
            Command command, String defaultHost, String collection, Map<String, Object> configuration) {
        if (DATA_PLANE_COMMANDS.contains(command)) {

            Function<HttpRequest.Builder, HttpRequest.Builder> prep =
                    (httpRequestBuilder) -> addAuthorizationHeader(configuration, httpRequestBuilder);
            Function<InputStream, String> responseTransformer =
                    HttpService.DEFAULT_RESPONSE_TO_MAP_TRANSFORMER.andThen(m -> "https://" + m.get("host"));

            return Optional.of(new VectorDatabaseRequest<>(
                    URI.create(defaultHost + "/indexes/" + collection),
                    prep.andThen(HttpRequest.Builder::build),
                    responseTransformer));
        }
        return VectorDatabaseProvider.super.getCollectionSpecificHost(command, defaultHost, collection, configuration);
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createCreateRequest(
            String baseUrl,
            Function<HttpRequest.Builder, HttpRequest.Builder> prep,
            Map<String, Object> additionalArguments) {
        var predefinedProperties = Set.of("id", "vector", "metadata");
        var target = URI.create(baseUrl + "/vectors/upsert");
        Function<HttpRequest.Builder, HttpRequest> requestCustomizer = prep.andThen(httpRequestBuilder -> {
            try {
                var vectors = new ArrayList<Map<String, Object>>();
                for (Map<String, Object> vector : ((List<Map<String, Object>>) additionalArguments.get("vectors"))) {
                    Map<String, Object> hlp = new HashMap<>();
                    hlp.put("id", vector.get("id"));
                    hlp.put("metadata", vector.get("metadata"));
                    hlp.put("values", vector.get("vector"));
                    vector.forEach((k, v) -> {
                        if (predefinedProperties.contains(k)) {
                            hlp.put(k, v);
                        }
                    });
                    vectors.add(hlp);
                }

                return httpRequestBuilder
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                JsonUtils.getObjectMapper().writeValueAsString(Map.of("vectors", vectors))))
                        .build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return new VectorDatabaseRequest<>(target, requestCustomizer, in -> (T) VectorDatabases.StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteCollectionRequest(
            String baseUrl, String collection, Function<HttpRequest.Builder, HttpRequest.Builder> prep) {
        return new VectorDatabaseRequest<T>(
                URI.create(baseUrl + "/indexes/" + collection),
                prep.andThen(HttpRequest.Builder::DELETE).andThen(HttpRequest.Builder::build),
                in -> (T) VectorDatabases.StatusDTO.ok(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> VectorDatabaseRequest<T> createDeleteRequest(
            String baseUrl,
            Function<HttpRequest.Builder, HttpRequest.Builder> prep,
            Map<String, Object> additionalArguments) {

        String body;
        try {
            body = JsonUtils.getObjectMapper().writeValueAsString(Map.of("ids", additionalArguments.get("ids")));

        } catch (JsonProcessingException e) {
            throw new GenAIProcedureException("Failed to create body for batch deletion of vectors");
        }

        return new VectorDatabaseRequest<T>(
                URI.create(baseUrl + "/vectors/delete"),
                prep.andThen(b -> b.POST(HttpRequest.BodyPublishers.ofString(body)))
                        .andThen(HttpRequest.Builder::build),
                in -> (T) VectorDatabases.StatusDTO.ok(null));
    }
}

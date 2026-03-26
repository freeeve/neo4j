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
package org.neo4j.genai.ai.text.embed.provider.vertexai;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;
import static org.neo4j.genai.util.HttpService.jsonBody;
import static org.neo4j.genai.util.Parameters.parse;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.ai.text.embed.VectorEmbedding;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.genai.util.UrlPath;
import org.neo4j.values.storable.VectorValue;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class VertexAi implements VectorEmbedding.Provider {
    protected static final String DEFAULT_BASE_URL_TEMPLATE = "https://%s-aiplatform.googleapis.com";
    private static final String DEFAULT_API_PATH_TEMPLATE =
            "v1/projects/%s/locations/%s/publishers/%s/models/%s:predict";

    private final Function<Parameters, URI> baseUriResolver;

    public VertexAi() {
        this.baseUriResolver = new DefaultBaseUriResolver();
    }

    public VertexAi(Function<Parameters, URI> baseUriResolver) {
        this.baseUriResolver = baseUriResolver;
    }

    public static class Parameters {
        public String model;
        public String project;
        public String region;
        public Optional<String> token; // OAuth Access Token
        public Optional<String> apiKey; // API Key
        public String publisher = "google";
        public long maxBatchSize = -1;
        // Optional Vendor Options: taskType, autoTruncate
        public Map<String, Object> vendorOptions = Map.of();
    }

    @Override
    public String name() {
        return "VertexAI";
    }

    @Override
    public Class<?> paramType() {
        return Parameters.class;
    }

    @Override
    public Implementation configure(HttpService httpService, MapValue conf, GenAIConfig genAIConfig) {
        final var params = parse(Parameters.class, conf);
        if (params.token.isEmpty() && params.apiKey.isEmpty()) {
            throw new IllegalArgumentException("'token or apiKey' is expected to have been set");
        } else if (params.token.isPresent() && params.apiKey.isPresent()) {
            throw new IllegalArgumentException("Only one of either 'token' or ' apiKey' is expected to have been set");
        }
        return new Implementation(name(), endpoint(params), httpService, params);
    }

    private record Implementation(String name, URI endpoint, HttpService httpService, Parameters params)
            implements VectorEmbedding.Provider.Implementation {

        @Override
        public long maxBatchSize() {
            if (params.maxBatchSize > 0) {
                return params.maxBatchSize;
            }
            return -1; // No automatic batching, VertexAI has a larger limit than OpenAI
        }

        @Override
        public VectorValue encode(String resource) {
            var vectors = encode(List.of(resource), EMPTY_INT_ARRAY, 0).toList();
            if (vectors.size() != 1) {
                throw new MalformedGenAIResponseException(
                        "Expected exactly one vector embedding, but found " + vectors.size());
            }
            return vectors.getFirst().vector();
        }

        @Override
        public Stream<VectorEmbedding.InternalBatchRow> encodeBatch(
                List<String> resources, int[] nullIndexes, int batchOffset) {
            return encode(resources, nullIndexes, batchOffset);
        }

        private Stream<VectorEmbedding.InternalBatchRow> encode(
                List<String> resources, int[] nullIndexes, int batchOffset) {
            final Object payload = buildPayload(resources);

            URI requestEndpoint = endpoint;
            if (params.apiKey.isPresent()) {
                String encodedKey = URLEncoder.encode(params.apiKey.get(), StandardCharsets.UTF_8);
                requestEndpoint = URI.create(endpoint.toString() + "?key=" + encodedKey);
            }

            URI finalRequestEndpoint = requestEndpoint;
            return httpService()
                    .request(
                            finalRequestEndpoint,
                            builder -> {
                                var b = builder.header(
                                                "Content-Type", "application/json; charset=" + StandardCharsets.UTF_8)
                                        .header("Accept", "application/json");
                                if (params.token.isPresent()) {
                                    b = b.header("Authorization", "Bearer " + params.token.get());
                                }
                                return b.POST(jsonBody(payload)).build();
                            },
                            inputStream -> parseResponse(resources, inputStream, nullIndexes, batchOffset));
        }

        /*
         relevant part of response:
            {
                "predictions": [
                    { "embeddings": { "values": (vector:List<Double>) } },
                    { "embeddings": { "values": (vector:List<Double>) } },
                    ...,
                    { "embeddings": { "values": (vector:List<Double>) } },
                ]
            }
        */
        static Stream<VectorEmbedding.InternalBatchRow> parseResponse(
                List<String> resources, InputStream inputStream, int[] nullIndexes, int batchOffset)
                throws MalformedGenAIResponseException {
            final String[] properties = {"embeddings", "values"};
            return JsonUtils.parseResponse(
                    "VertexAI", "predictions", properties, resources, inputStream, nullIndexes, batchOffset);
        }

        /*
         payload:
            {
               "instances": [
                   { "content": (resource:String) },
                   { "content": (resource:String) },
                   ...,
                   { "content": (resource:String) },
               ],
               "parameters": {
                   "autoTruncate": (autoTruncate:boolean)
               }
           }
        */
        private Map<String, Object> buildPayload(List<String> resources) {
            var extraVendorOptions = Maps.mutable.ofMap(params.vendorOptions);
            // clean out task type as that goes inside of instances
            extraVendorOptions.remove("task_type");

            final List<Object> instances = ListAdapter.adapt(resources).collect(this::instance);
            if (extraVendorOptions.isEmpty()) {
                return Maps.mutable.of("instances", instances);
            }
            return Maps.mutable.of("instances", instances, "parameters", extraVendorOptions);
        }

        private Map<String, ?> instance(String resource) {
            final Map<String, Object> instance = Maps.mutable.of("content", resource);
            var taskType = params.vendorOptions.getOrDefault("task_type", "");
            if (taskType instanceof String && !taskType.toString().isBlank()) {
                instance.put("task_type", taskType);
            }
            return instance;
        }
    }

    private URI endpoint(Parameters params) {
        return baseUriResolver
                .apply(params)
                .resolve(DEFAULT_API_PATH_TEMPLATE.formatted(
                        UrlPath.pathSafe(params.project, "project"),
                        UrlPath.pathSafe(params.region, "region"),
                        UrlPath.pathSafe(params.publisher, "publisher"),
                        UrlPath.pathSafe(params.model, "model")));
    }

    private static class DefaultBaseUriResolver implements Function<Parameters, URI> {
        @Override
        public URI apply(Parameters parameters) {
            final var region = UrlPath.pathSafe(parameters.region, "region");
            return URI.create(DEFAULT_BASE_URL_TEMPLATE.formatted(region));
        }
    }
}

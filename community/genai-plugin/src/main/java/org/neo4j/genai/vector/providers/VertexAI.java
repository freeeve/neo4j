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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.genai.vector.VectorEncoding.BatchRow;
import org.neo4j.genai.vector.VectorEncoding.Provider;

@ServiceProvider
public final class VertexAI implements Provider<VertexAI.Parameters> {
    public static final String NAME = "VertexAI";
    private static final String ENDPOINT_TEMPLATE = "https://${region}-aiplatform.googleapis.com/v1"
            + "/projects/${projectId}/locations/${region}/publishers/google/models/${model}:predict";
    static final String DEFAULT_REGION = "us-central1";
    static final Set<String> SUPPORTED_REGIONS = Set.of(
            // https://cloud.google.com/vertex-ai/docs/general/locations
            "africa-south1",
            "asia-east1",
            "asia-east2",
            "asia-northeast1",
            "asia-northeast2",
            "asia-northeast3",
            "asia-south1",
            "asia-southeast1",
            "asia-southeast2",
            "australia-southeast1",
            "australia-southeast2",
            "europe-central2",
            "europe-north1",
            "europe-southwest1",
            "europe-west1",
            "europe-west2",
            "europe-west3",
            "europe-west4",
            "europe-west6",
            "europe-west8",
            "europe-west9",
            "europe-west12",
            "me-central1",
            "me-central2",
            "me-west1",
            "northamerica-northeast1",
            "northamerica-northeast2",
            "southamerica-east1",
            "southamerica-west1",
            "us-central1",
            "us-east1",
            "us-east4",
            "us-south1",
            "us-west1",
            "us-west2",
            "us-west3",
            "us-west4",
            "us-east5");
    private static final String STRINGIFIED_SUPPORTED_REGIONS =
            SUPPORTED_REGIONS.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));

    // safer to assume the single encoder has to be used, and specifically add to the batch set as an optimisation
    static final Set<String> KNOWN_BATCH_SUPPORTED_MODELS =
            Set.of("text-embedding-005", "text-multilingual-embedding-002");
    static final String DEFAULT_BUT_RETIRED_MODEL = "textembedding-gecko@001";

    public static class Parameters {
        public String token;
        public String projectId;
        public String model = DEFAULT_BUT_RETIRED_MODEL;
        public String region = DEFAULT_REGION;
        public Optional<String> taskType;
        public Optional<String> title;
        public Optional<Boolean> autoTruncate;
        public OptionalLong dimensions;
    }

    @Override
    public Class<Parameters> parameterDeclarations() {
        return Parameters.class;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Provider.Encoder configure(Parameters configuration) {
        if (configuration.model.equals(DEFAULT_BUT_RETIRED_MODEL)) {
            throw new IllegalArgumentException("Provided (default) model '%s' has been deprecated by VertexAI."
                    .formatted(DEFAULT_BUT_RETIRED_MODEL));
        }
        if (!SUPPORTED_REGIONS.contains(configuration.region)) {
            throw new IllegalArgumentException("Provided region '%s' is not supported. Supported regions: %s"
                    .formatted(configuration.region, STRINGIFIED_SUPPORTED_REGIONS));
        }

        final var endpoint = URI.create(StringSubstitutor.replace(
                ENDPOINT_TEMPLATE,
                Map.of(
                        "region",
                        configuration.region,
                        "projectId",
                        configuration.projectId,
                        "model",
                        configuration.model)));

        if (KNOWN_BATCH_SUPPORTED_MODELS.contains(configuration.model)) {
            return new BatchEncoder(endpoint, configuration);
        }
        return new SingleEncoder(endpoint, configuration);
    }

    private static class SingleEncoder extends Encoder {
        private SingleEncoder(URI endpoint, Parameters configuration) {
            super(endpoint, configuration);
        }

        @Override
        public float[] encode(HttpService httpService, String resource) {
            return encodeInternal(httpService, List.of(resource), EMPTY_INT_ARRAY)
                    .findFirst()
                    .orElseThrow()
                    .vector();
        }
    }

    private static class BatchEncoder extends SingleEncoder {
        private BatchEncoder(URI endpoint, Parameters configuration) {
            super(endpoint, configuration);
        }

        @Override
        public Stream<BatchRow> encode(HttpService httpService, List<String> resources, int[] nullIndexes) {
            return encodeInternal(httpService, resources, nullIndexes);
        }
    }

    abstract static class Encoder implements Provider.Encoder {
        private final URI endpoint;
        private final Parameters configuration;
        private final Map<String, ?> parameters;

        protected Encoder(URI endpoint, Parameters configuration) {
            this.endpoint = endpoint;
            this.configuration = configuration;
            this.parameters = parameters();
        }

        protected Stream<BatchRow> encodeInternal(HttpService httpService, List<String> resources, int[] nullIndexes) {
            return httpService.request(
                    endpoint,
                    builder -> builder.headers(
                                    "Authorization",
                                    "Bearer " + configuration.token,
                                    "Content-Type",
                                    "application/json; charset=" + StandardCharsets.UTF_8,
                                    "Accept",
                                    "application/json")
                            .POST(HttpService.pipe(outputStream -> writeRequestPayload(outputStream, resources)))
                            .build(),
                    inputStream -> parseResponse(resources, inputStream, nullIndexes));
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
        static Stream<BatchRow> parseResponse(List<String> resources, InputStream inputStream, int[] nullIndexes)
                throws MalformedGenAIResponseException {
            final String[] properties = {"embeddings", "values"};
            return JsonUtils.parseResponse(NAME, "predictions", properties, resources, inputStream, nullIndexes);
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
                   "autoTruncate": (autoTruncate:boolean),
                   "outputDimensionality": (dimensions:int)
               }
           }
        */
        private Object buildPayload(List<String> resources) {
            final List<Object> instances = ListAdapter.adapt(resources).collect(this::instance);
            return Maps.mutable.of("instances", instances, "parameters", parameters);
        }

        private Map<String, ?> instance(String resource) {
            final Map<String, Object> instance = Maps.mutable.of("content", resource);
            configuration.title.ifPresent(title -> instance.put("title", title));
            configuration.taskType.ifPresent(taskType -> instance.put("task_type", taskType));
            return instance;
        }

        private Map<String, ?> parameters() {
            final Map<String, Object> parameters = Maps.mutable.empty();
            configuration.autoTruncate.ifPresent(autoTruncate -> parameters.put("autoTruncate", autoTruncate));
            configuration.dimensions.ifPresent(dimensions -> parameters.put("outputDimensionality", dimensions));
            return parameters;
        }

        private void writeRequestPayload(OutputStream out, List<String> resources) {
            try {
                JsonUtils.getObjectMapper().writeValue(out, buildPayload(resources));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

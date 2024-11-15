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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.collections.impl.factory.Multimaps;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.genai.util.aws.AwsSignatureV4HeaderGenerator;
import org.neo4j.genai.vector.VectorEncoding.Provider;

@ServiceProvider
public final class Bedrock implements Provider<Bedrock.Parameters> {
    public static final String NAME = "Bedrock";
    private static final String ENDPOINT_TEMPLATE =
            "https://bedrock-runtime.${region}.amazonaws.com/model/${model}/invoke";
    static final String DEFAULT_REGION = "us-east-1";
    static final Set<String> SUPPORTED_REGIONS = Set.of(
            // https://docs.aws.amazon.com/bedrock/latest/userguide/what-is-bedrock.html
            "us-east-1", "us-west-2", "ap-southeast-1", "ap-northeast-1", "eu-central-1");
    private static final String STRINGIFIED_SUPPORTED_REGIONS =
            SUPPORTED_REGIONS.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));
    static final String DEFAULT_MODEL = "amazon.titan-embed-text-v1";
    static final Set<String> SUPPORTED_MODELS = Set.of(DEFAULT_MODEL);
    private static final String STRINGIFIED_SUPPORTED_MODELS =
            SUPPORTED_MODELS.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));
    private static final TypeReference<float[]> VECTOR_TYPE_REFERENCE = new TypeReference<>() {};

    public static class Parameters {
        public String accessKeyId;
        public String secretAccessKey;
        public String model = DEFAULT_MODEL;
        public String region = DEFAULT_REGION;
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
        if (!SUPPORTED_MODELS.contains(configuration.model)) {
            throw new IllegalArgumentException("Provided model '%s' is not supported. Supported models: %s"
                    .formatted(configuration.model, STRINGIFIED_SUPPORTED_MODELS));
        }
        if (!SUPPORTED_REGIONS.contains(configuration.region)) {
            throw new IllegalArgumentException("Provided region '%s' is not supported. Supported regions: %s"
                    .formatted(configuration.region, STRINGIFIED_SUPPORTED_REGIONS));
        }
        final var endpoint = URI.create(StringSubstitutor.replace(
                ENDPOINT_TEMPLATE, Map.of("region", configuration.region, "model", configuration.model)));
        return new Encoder(endpoint, configuration);
    }

    record Encoder(URI endpoint, Parameters configuration) implements Provider.Encoder {
        @Override
        public float[] encode(HttpService httpService, String resource) {
            try {
                var body = createRequestBody(resource);
                return httpService.request(
                        endpoint,
                        builder -> {
                            var intermediate = builder.build();
                            var requestProperties = Multimaps.mutable.list.with("Host", endpoint.getHost());
                            intermediate.headers().map().forEach(requestProperties::putAll);

                            var finalHeaders = new AwsSignatureV4HeaderGenerator(
                                            configuration.region, endpoint, body, requestProperties)
                                    .generate(configuration.accessKeyId, configuration.secretAccessKey);

                            var newBuilder =
                                    HttpRequest.newBuilder(intermediate, (k, v) -> !finalHeaders.containsKey(k));
                            finalHeaders.forEachKeyValue(newBuilder::header);
                            return newBuilder
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build();
                        },
                        Encoder::parseResponse);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /*
        relevant part of response:
            {
                "embedding": (vector:List<Double>)
            }
        */
        static float[] parseResponse(InputStream inputStream) throws MalformedGenAIResponseException {
            final JsonNode tree;
            final ObjectMapper objectMapper = JsonUtils.getObjectMapper();
            try {
                tree = objectMapper.readTree(inputStream);
            } catch (IOException e) {
                throw new MalformedGenAIResponseException(
                        "Unexpected error occurred while parsing the API response", e);
            }

            final var embedding = getExpectedFrom(tree, "embedding");
            if (!embedding.isArray()) {
                throw new MalformedGenAIResponseException("Expected embedding to be an array");
            }

            try (final var parser = embedding.traverse(objectMapper)) {
                return parser.readValueAs(VECTOR_TYPE_REFERENCE);
            } catch (IOException e) {
                throw new MalformedGenAIResponseException("Unexpected error occurred while parsing the embedding", e);
            }
        }

        private static JsonNode getExpectedFrom(JsonNode json, String property) throws MalformedGenAIResponseException {
            return JsonUtils.getExpectedFrom(NAME, json, property);
        }

        /*
         payload:
            {
               "inputText": (resource:String)
           }
        */
        private String createRequestBody(String resource) throws IOException {
            return JsonUtils.getObjectMapper().writeValueAsString(Map.of("inputText", resource));
        }
    }
}

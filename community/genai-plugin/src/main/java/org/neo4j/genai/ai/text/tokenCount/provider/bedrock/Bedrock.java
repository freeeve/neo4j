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
package org.neo4j.genai.ai.text.tokenCount.provider.bedrock;

import static org.neo4j.genai.util.Parameters.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.ai.text.tokenCount.TextTokenCount;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.aws.AwsSignatureV4HeaderGenerator;
import org.neo4j.genai.util.aws.URLUtils.URLEncoder;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class Bedrock implements TextTokenCount.Provider {
    static final String DEFAULT_BASE_URL_TEMPLATE = "https://bedrock-runtime.%s.amazonaws.com";
    static final String DEFAULT_API_PATH_TEMPLATE = "/model/%s/count-tokens";

    private final Function<Parameters, URI> baseUriResolver;

    public Bedrock() {
        this.baseUriResolver = new DefaultBaseUriResolver();
    }

    @VisibleForTesting
    public Bedrock(Function<Parameters, URI> baseUriResolver) {
        this.baseUriResolver = baseUriResolver;
    }

    public static class Parameters {
        public String accessKeyId;
        public String secretAccessKey;
        public String region;
        public String model;
        public List<Map<String, Object>> chatHistory = List.of();
    }

    @Override
    public String name() {
        return "Bedrock";
    }

    @Override
    public Class<?> paramType() {
        return Parameters.class;
    }

    @Override
    public TextTokenCount.Provider.Implementation configure(
            HttpService httpService, MapValue configuration, GenAIConfig genAIConfig) {
        final var params = parse(Parameters.class, configuration);
        final var encodedModel = URLEncoder.encode(params.model);
        final var uri = baseUriResolver.apply(params).resolve(DEFAULT_API_PATH_TEMPLATE.formatted(encodedModel));
        return new Implementation(name(), metricsName(), uri, httpService, params);
    }

    record Implementation(
            @Override String name,
            @Override String metricsName,
            URI endpoint,
            HttpService httpService,
            Parameters params)
            implements TextTokenCount.Provider.Implementation {

        @Override
        public long tokenCount(String prompt) {
            var body = createRequestBody(prompt);
            final var endpoint = endpoint();
            final var response = httpService()
                    .request(
                            endpoint,
                            builder -> {
                                var intermediate = builder.build();
                                var requestProperties = Multimaps.mutable.list.with("Host", endpoint.getHost());
                                intermediate.headers().map().forEach(requestProperties::putAll);

                                var finalHeaders = new AwsSignatureV4HeaderGenerator(
                                                params().region, endpoint, body, requestProperties)
                                        .generate(params().accessKeyId, params().secretAccessKey);

                                var newBuilder =
                                        HttpRequest.newBuilder(intermediate, (k, v) -> !finalHeaders.containsKey(k));
                                finalHeaders.forEachKeyValue(newBuilder::header);
                                return newBuilder
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build();
                            },
                            this::parseResponse);
            return response.inputTokens();
        }

        private String createRequestBody(String prompt) {
            try {
                final Object payload = buildPayload(prompt);
                return JsonUtils.getObjectMapper().writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        /*
         *{
         *  "input": {
         *    "converse": {
         *      "messages": [
         *        {
         *          "role": "user",
         *          "content": [
         *            {
         *              "text": "Hello, how many tokens is this?"
         *            }
         *          ]
         *        }
         *      ]
         *     }
         *   }
         * }
         */
        private Map<Object, Object> buildPayload(String prompt) {
            final var payload = Maps.mutable.empty();
            final var newContent = Map.of("role", "user", "content", List.of(Map.of("text", prompt)));

            List<Map<String, Object>> messages;
            if (params.chatHistory != null && !params.chatHistory.isEmpty()) {
                messages = params.chatHistory;
                messages.add(newContent);
            } else {
                messages = List.of(newContent);
            }

            payload.put("input", Map.of("converse", Map.of("messages", messages)));
            return payload;
        }

        private ResponseModel.Response parseResponse(InputStream inputStream) {
            return JsonUtils.readValue(inputStream, ResponseModel.Response.class);
        }

        /*
         * {
         *    "inputTokens": number
         * }
         */
        interface ResponseModel {
            @JsonIgnoreProperties(ignoreUnknown = true)
            record Response(long inputTokens) {}
        }
    }

    static class DefaultBaseUriResolver implements Function<Parameters, URI> {
        @Override
        public URI apply(Parameters conf) {
            return URI.create(DEFAULT_BASE_URL_TEMPLATE.formatted(conf.region));
        }
    }
}

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
package org.neo4j.genai.ai.text.structuredCompletion.provider.bedrock;

import static org.neo4j.genai.util.Parameters.parse;
import static org.neo4j.genai.util.Parameters.toJavaMap;

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
import org.neo4j.genai.ai.text.structuredCompletion.TextStructuredCompletion;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.genai.util.aws.AwsSignatureV4HeaderGenerator;
import org.neo4j.genai.util.aws.URLUtils.URLEncoder;
import org.neo4j.kernel.impl.util.ValueUtils;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.virtual.MapValue;

/**
 * Bedrock Converse provides a consistent interface that works with all models that support messages.
 * This variant supports structured outputs via tool configuration.
 */
@ServiceProvider
public class BedrockConverse implements TextStructuredCompletion.Provider {
    static final String DEFAULT_BASE_URL_TEMPLATE = "https://bedrock-runtime.%s.amazonaws.com";
    static final String DEFAULT_API_PATH_TEMPLATE = "/model/%s/converse";

    private final Function<Parameters, URI> baseUriResolver;

    public BedrockConverse() {
        this.baseUriResolver = new DefaultBaseUriResolver();
    }

    @VisibleForTesting
    public BedrockConverse(Function<Parameters, URI> baseUriResolver) {
        this.baseUriResolver = baseUriResolver;
    }

    public static class Parameters {
        public String accessKeyId;
        public String secretAccessKey;
        public String region;
        public String model;
        public Map<String, Object> vendorOptions = Map.of();
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
    public TextStructuredCompletion.Provider.Implementation configure(
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
            implements TextStructuredCompletion.Provider.Implementation {

        @Override
        public MapValue complete(String prompt, MapValue schema) {
            var body = createRequestBody(prompt, schema);
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
                            this::parseResponseToMap);
            return ValueUtils.asMapValue(response);
        }

        private String createRequestBody(String prompt, MapValue schemaValue) {
            try {
                final Object payload = buildPayload(prompt, schemaValue);
                return JsonUtils.getObjectMapper().writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        public Map<String, Object> buildPayload(String prompt, MapValue schemaValue) {
            final var payload = Maps.mutable.ofMap(params.vendorOptions);
            final var newContent = Map.of("role", "user", "content", List.of(Map.of("text", prompt)));

            List<Map<String, Object>> messages;
            if (params.chatHistory != null && !params.chatHistory.isEmpty()) {
                messages = params.chatHistory;
                messages.add(newContent);
            } else {
                messages = List.of(newContent);
            }

            payload.put("messages", messages);
            // toolConfig with schema tool
            var schema = toJavaMap(schemaValue);
            final var toolSpec = Maps.mutable.of(
                    "name",
                    "reply_with_defined_schema",
                    "description",
                    "Respond to the user's question with the defined input schema",
                    "inputSchema",
                    Map.of("json", schema));
            final var tools = List.of(Map.of("toolSpec", toolSpec));
            payload.put(
                    "toolConfig",
                    Map.of("tools", tools, "toolChoice", Map.of("tool", Map.of("name", "reply_with_defined_schema"))));
            return payload;
        }

        public Map<String, Object> parseResponseToMap(InputStream stream) {
            var jsonResponse = JsonUtils.readValue(stream, ResponseModel.Response.class);
            // Structured input is returned as a toolUse
            final var outputs = jsonResponse.output().message().content().stream()
                    .filter(c -> c.toolUse != null)
                    .toList();
            if (outputs.isEmpty()) {
                throw new MalformedGenAIResponseException("Expected at least one `toolUse`");
            }
            return outputs.getFirst().toolUse().input;
        }

        interface ResponseModel {
            @JsonIgnoreProperties(ignoreUnknown = true)
            record ToolResultContentBlock(Object json, String text) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            record ToolResultBlock(String type, ToolResultContentBlock content) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            record ToolUseBlock(Map<String, Object> input, String name, String type, String toolUseId) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            record Content(String text, ToolResultBlock toolResult, ToolUseBlock toolUse) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            record Message(String role, List<Content> content) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            record Output(Message message) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            record Response(Output output, String stopReason) {}
        }
    }

    static class DefaultBaseUriResolver implements Function<Parameters, URI> {
        @Override
        public URI apply(Parameters conf) {
            return URI.create(DEFAULT_BASE_URL_TEMPLATE.formatted(conf.region));
        }
    }
}

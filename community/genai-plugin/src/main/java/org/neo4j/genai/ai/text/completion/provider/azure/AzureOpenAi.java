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
package org.neo4j.genai.ai.text.completion.provider.azure;

import static org.neo4j.genai.util.Parameters.parse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.ai.text.completion.TextCompletion;
import org.neo4j.genai.ai.text.completion.provider.openai.OpenAiBase;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.UrlPath;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class AzureOpenAi implements TextCompletion.Provider {
    private static final String DEFAULT_BASE_URL_TEMPLATE = "https://%s.openai.azure.com";
    private static final String DEFAULT_API_PATH = "/openai/v1/responses";
    private final Function<Parameters, URI> baseUriResolver;

    public AzureOpenAi() {
        this(new DefaultBaseUriResolver());
    }

    @VisibleForTesting
    public AzureOpenAi(Function<Parameters, URI> baseUriResolver) {
        this.baseUriResolver = baseUriResolver;
    }

    public static class Parameters {
        public String token;
        public String resource;
        public String model;
        public Map<String, Object> vendorOptions = Map.of();
        public List<Map<String, Object>> chatHistory = List.of();
    }

    @Override
    public String name() {
        return "Azure-OpenAI";
    }

    @Override
    public Class<?> paramType() {
        return Parameters.class;
    }

    @Override
    public Implementation configure(HttpService httpService, MapValue configuration, GenAIConfig genAIConfig) {
        final var params = parse(Parameters.class, configuration);
        final var uri = baseUriResolver.apply(params).resolve(DEFAULT_API_PATH);
        return new Impl(uri, httpService, params, name());
    }

    private record Impl(
            @Override URI endpoint,
            @Override HttpService httpService,
            @Override Parameters params,
            @Override String name)
            implements OpenAiBase<Parameters> {
        @Override
        public String[] authHeader() {
            return new String[] {"Authorization", "Bearer " + params.token};
        }

        @Override
        public void extendPayload(MutableMap<String, Object> payload) {
            payload.putAll(params.vendorOptions); // Needs to be first to not override model
            payload.put("model", params.model);
        }

        @Override
        public List<Map<String, Object>> chatHistory() {
            return params.chatHistory;
        }
    }

    private static class DefaultBaseUriResolver implements Function<Parameters, URI> {
        @Override
        public URI apply(Parameters parameters) {
            final var region = UrlPath.pathSafe(parameters.resource, "resource");
            return URI.create(DEFAULT_BASE_URL_TEMPLATE.formatted(region));
        }
    }
}

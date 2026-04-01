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
package org.neo4j.genai.ai.text.chat.provider.azure;

import static org.neo4j.genai.util.Parameters.parse;

import java.net.URI;
import java.util.Map;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.ai.provider.azure.AzureOpenAiRequestSupport;
import org.neo4j.genai.ai.text.chat.TextChat;
import org.neo4j.genai.ai.text.chat.provider.openai.OpenAiChatBase;
import org.neo4j.genai.util.HttpService;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class AzureOpenAi implements TextChat.Provider {
    private static final String DEFAULT_API_PATH = "/responses";

    public static class Parameters {
        public String token;
        public String resource;
        public String model;
        public Map<String, Object> vendorOptions = Map.of();
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
        final var uri = AzureOpenAiRequestSupport.endpoint(genAIConfig, params.resource, DEFAULT_API_PATH);
        return new Impl(uri, httpService, params, name());
    }

    private record Impl(
            @Override URI endpoint,
            @Override HttpService httpService,
            @Override Parameters params,
            @Override String name)
            implements OpenAiChatBase<Parameters> {
        @Override
        public String[] authHeader() {
            return AzureOpenAiRequestSupport.authHeader(params.token);
        }

        @Override
        public void extendPayload(MutableMap<String, Object> payload) {
            payload.putAll(params.vendorOptions); // Needs to be first to not override model
            payload.put("model", params.model);
        }
    }
}

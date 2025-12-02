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
package org.neo4j.genai.ai.text.completion.provider.openai;

import static org.neo4j.genai.util.Parameters.parse;

import java.net.URI;
import java.util.Map;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.ai.text.completion.TextCompletion;
import org.neo4j.genai.util.HttpService;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class OpenAi implements TextCompletion.Provider {
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_API_PATH = "/v1/responses";
    private final URI endpoint;

    public OpenAi() {
        this(DEFAULT_BASE_URL);
    }

    @VisibleForTesting
    public OpenAi(String baseUrl) {
        this.endpoint = URI.create(baseUrl + DEFAULT_API_PATH);
    }

    public static class Parameters {
        public String token;
        public String model;
        public Map<String, Object> vendorOptions = Map.of();
    }

    @Override
    public String name() {
        return "OpenAI";
    }

    @Override
    public Class<Parameters> paramType() {
        return Parameters.class;
    }

    @Override
    public TextCompletion.Provider.Implementation configure(
            HttpService httpService, MapValue conf, GenAIConfig genAIConfig) {
        // We create the URI already with the base url (or with a testing one which we don't want to override here).
        String baseUrl = genAIConfig == null ? null : genAIConfig.getStringProperty(GenAIConfig.GENAI_OPENAI_BASE_URL);
        var newEndpoint =
                baseUrl == null || baseUrl.equals(DEFAULT_BASE_URL) ? endpoint : URI.create(baseUrl + DEFAULT_API_PATH);

        return new Implementation(name(), newEndpoint, httpService, parse(Parameters.class, conf));
    }

    record Implementation(String name, URI endpoint, HttpService httpService, Parameters params)
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
    }
}

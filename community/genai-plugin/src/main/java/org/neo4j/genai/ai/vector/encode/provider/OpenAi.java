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
package org.neo4j.genai.ai.vector.encode.provider;

import static org.neo4j.genai.util.Parameters.parse;

import java.net.URI;
import java.util.OptionalLong;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.ai.vector.encode.VectorEncoding;
import org.neo4j.genai.util.HttpService;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class OpenAi implements VectorEncoding.Provider {
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_API_PATH = "/v1/embeddings";
    private final URI endpoint;

    // static final String DEFAULT_MODEL = "text-embedding-ada-002";

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
        public OptionalLong dimensions;
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
    public VectorEncoding.Provider.Implementation configure(HttpService httpService, MapValue conf) {
        return new Implementation(name(), endpoint, httpService, parse(Parameters.class, conf));
    }

    record Implementation(String name, URI endpoint, HttpService httpService, Parameters params)
            implements OpenAiBase<Parameters> {

        public URI endpoint() {
            return endpoint;
        }

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public OptionalLong dimensions() {
            return params.dimensions;
        }

        @Override
        public String[] authHeader() {
            return new String[] {"Authorization", "Bearer " + params.token};
        }

        @Override
        public void extendPayload(MutableMap<String, Object> payload) {
            payload.put("model", params.model);
        }
    }
}

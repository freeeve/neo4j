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
package org.neo4j.genai.ai.text.tokenCount.provider.openai;

import static com.knuddels.jtokkit.api.EncodingType.R50K_BASE;
import static org.neo4j.genai.util.Parameters.parse;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import java.net.URI;
import java.util.Optional;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.ai.text.tokenCount.TextTokenCount;
import org.neo4j.genai.util.HttpService;
import org.neo4j.values.virtual.MapValue;

@ServiceProvider
public class OpenAi implements TextTokenCount.Provider {
    @Override
    public String name() {
        return "OpenAI";
    }

    public static class Parameters {
        public String model = "";
    }

    @Override
    public Class<Parameters> paramType() {
        return Parameters.class;
    }

    @Override
    public Implementation configure(HttpService httpService, MapValue configuration, GenAIConfig genAIConfig) {
        final var params = parse(Parameters.class, configuration);
        return new Impl(name(), null, httpService, params);
    }

    // This one is actually done locally, but for consistency we keep the access to httpService etc
    private record Impl(String name, URI endpoint, HttpService httpService, Parameters params)
            implements TextTokenCount.Provider.Implementation {

        @Override
        public long tokenCount(String prompt) {
            EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
            if (params == null || params.model == null || params.model.isEmpty()) {
                Encoding enc = registry.getEncoding(R50K_BASE);
                return enc.countTokens(prompt);
            }
            Optional<Encoding> enc = registry.getEncodingForModel(params.model);

            if (enc.isEmpty()) {
                // FALLBACK: if model is not recognized by jtokkit, use a default encoding instead of throwing exception
                return registry.getEncoding(R50K_BASE).countTokens(prompt);
            }

            return enc.get().countTokens(prompt);
        }
    }
}

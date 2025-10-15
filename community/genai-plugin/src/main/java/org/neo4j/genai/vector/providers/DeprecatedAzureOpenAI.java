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

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.apache.commons.text.StringSubstitutor;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.genai.vector.DeprecatedVectorEncoding.Provider;
import org.neo4j.genai.vector.providers.openai.DeprecatedOpenAIBasedEncoder;

@ServiceProvider
public final class DeprecatedAzureOpenAI implements Provider<DeprecatedAzureOpenAI.Parameters> {
    public static final String NAME = "AzureOpenAI";
    public static final String ENDPOINT_TEMPLATE =
            "https://${resource}.openai.azure.com/openai/deployments/${deployment}/embeddings?api-version=2023-05-15";

    // 2-64 mixed-case alphanumerical + "-"; can't start or end on "-"
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("^\\p{Alnum}[\\p{Alnum}-]{0,62}\\p{Alnum}$");

    // 2-64 mixed-case alphanumerical + "-" + "_"; can't end on "-" or "_"
    private static final Pattern DEPLOYMENT_PATTERN = Pattern.compile("^[\\p{Alnum}_-]{1,63}\\p{Alnum}$");

    public static class Parameters {
        public String token;
        public String resource;
        public String deployment;
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
        final var endpoint = configureEndpoint(configuration);
        return new EncoderDeprecated(endpoint, configuration);
    }

    private static URI configureEndpoint(Parameters config) {
        validateResourceName(config.resource);
        validateDeploymentName(config.deployment);

        return URI.create(StringSubstitutor.replace(
                ENDPOINT_TEMPLATE, Map.of("resource", config.resource, "deployment", config.deployment)));
    }

    private static void validateResourceName(String name) {
        if (!RESOURCE_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(("Provided resource '%s' is invalid. "
                            + "It must consist of 2-64 alphanumerical characters or hyphens, and cannot start or end on a hyphen.")
                    .formatted(name));
        }
    }

    private static void validateDeploymentName(String name) {
        if (!DEPLOYMENT_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(("Provided deployment '%s' is invalid. "
                            + "It must consist of 2-64 alphanumerical characters, hyphens, or underscores, "
                            + "and cannot end on a hyphen or underscore.")
                    .formatted(name));
        }
    }

    static class EncoderDeprecated extends DeprecatedOpenAIBasedEncoder {
        private final Parameters configuration;

        EncoderDeprecated(URI endpoint, Parameters configuration) {
            super(NAME, endpoint, configuration.dimensions);
            this.configuration = configuration;
        }

        @Override
        protected HttpRequest.Builder customize(HttpRequest.Builder builder) {
            return builder.setHeader("api-key", configuration.token);
        }
    }
}

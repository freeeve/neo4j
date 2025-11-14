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
package org.neo4j.genai.util.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.genai.util.Parameters;
import org.neo4j.procedure.Description;

public record ProviderRow(
        @Description("Provider name.") String name,

        @Description("The signature of the required config map.")
        String requiredConfigType,

        @Description("The signature of the optional config map.")
        String optionalConfigType,

        @Description("The default values for the GenAI provider.")
        Map<String, Object> defaultConfig) {
    public static ProviderRow from(NamedProvider provider) {
        final var parameters = Parameters.getParameters(provider.paramType());

        return new ProviderRow(
                provider.name(),
                requiredConfigType(parameters),
                optionalConfigType(parameters),
                defaultConfig(parameters));
    }

    private static String requiredConfigType(List<Parameters.Parameter> parameters) {
        return cypherMapType(parameters.stream().filter(Parameters.Parameter::isRequired));
    }

    private static String optionalConfigType(List<Parameters.Parameter> parameters) {
        return cypherMapType(parameters.stream().filter(Parameters.Parameter::isOptional));
    }

    private static String cypherMapType(Stream<Parameters.Parameter> parameters) {
        return parameters
                .map(p -> "%s :: %s".formatted(p.name(), p.type().cypherName()))
                .collect(Collectors.joining(",\n  ", "{\n  ", "\n}"));
    }

    private static Map<String, Object> defaultConfig(List<Parameters.Parameter> parameters) {
        // This matches the previous behaviour of not listing defaults of Optional.empty().
        final MutableMap<String, Object> defaults = Maps.mutable.empty();
        for (var parameter : parameters) {
            final var defaultValue = parameter.defaultValue();
            if (defaultValue == null) {
                continue;
            }

            // Unwrap nullables if present; otherwise they are not included in the defaults map.
            // This is to avoid having Optional.empty() as a default value.
            if (defaultValue instanceof final Optional<?> optionalDefaultValue) {
                optionalDefaultValue.ifPresent(o -> defaults.put(parameter.name(), o));
            } else if (defaultValue instanceof final OptionalLong optionalDefaultValue) {
                optionalDefaultValue.ifPresent(o -> defaults.put(parameter.name(), o));
            } else if (defaultValue instanceof final OptionalDouble optionalDefaultValue) {
                optionalDefaultValue.ifPresent(o -> defaults.put(parameter.name(), o));
            } else {
                defaults.put(parameter.name(), defaultValue);
            }
        }
        return defaults.asUnmodifiable();
    }
}

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
package org.neo4j.genai.ai.provider.azure;

import java.net.URI;
import org.neo4j.genai.GenAIConfig;
import org.neo4j.genai.util.UrlPath;

public final class AzureOpenAiRequestSupport {
    public static final String DEFAULT_BASE_URL_TEMPLATE = "https://%s.openai.azure.com/openai/v1";

    private AzureOpenAiRequestSupport() {}

    public static URI endpoint(GenAIConfig genAIConfig, String resource, String apiPath) {
        final var baseUrlTemplate = genAIConfig == null
                ? DEFAULT_BASE_URL_TEMPLATE
                : genAIConfig.getStringProperty(GenAIConfig.GENAI_AZURE_OPENAI_BASE_URL);

        final var formatSpecifierCount = countFormatSpecifiers(baseUrlTemplate);
        if (formatSpecifierCount > 1) {
            throw new IllegalArgumentException(
                    "Azure OpenAI base URL template can only have 0 or 1 '%s' placeholders, but found "
                            + formatSpecifierCount);
        }

        final String baseUrl;
        if (formatSpecifierCount == 0) {
            baseUrl = baseUrlTemplate;
        } else {
            baseUrl = baseUrlTemplate.formatted(UrlPath.pathSafe(resource, "resource"));
        }
        return URI.create(baseUrl + apiPath);
    }

    private static int countFormatSpecifiers(String template) {
        int count = 0;
        int index = 0;
        while ((index = template.indexOf("%s", index)) != -1) {
            count++;
            index += 2;
        }
        return count;
    }

    public static String[] authHeader(String token) {
        return new String[] {"Authorization", "Bearer " + token};
    }
}

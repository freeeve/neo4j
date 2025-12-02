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
package org.neo4j.genai.ai;

/* Environmental variable names for API access in integration tests. */
public interface Tokens {
    /*
     * OpenAI access.
     *
     * See https://platform.openai.com/docs/api-reference/authentication.
     */
    interface OpenAi {
        /* Name of env variable containing OpenAI API token that can be used for integration testing. */
        String TOKEN_ENV = "OPENAI_TOKEN";
    }

    /*
     * Vertex access.
     *
     * Needs access to the following models:
     * - 'text-embedding-3-small' (predict)
     * - 'gemini-2.5-flash-lite' (generateContent)
     *
     * See https://cloud.google.com/vertex-ai/docs/authentication#rest.
     */
    interface Vertex {
        /* Name of env variable containing Vertex access token that can be used for integration testing. */
        String TOKEN_ENV = "VERTEX_TOKEN";
        /* Name of env variable containing Vertex project to use with the token above. */
        String PROJECT_ENV = "VERTEX_PROJECT";
        /* Name of env variable containing Vertex region to use with the project above.  */
        String REGION_ENV = "VERTEX_REGION";
        /* Name of env variable that informs the test if the token is an api key or access key.  */
        String IS_API_KEY = "VERTEX_IS_API_KEY";
    }

    /*
     * Bedrock access.
     *
     * Needs access to the following models:
     * - 'amazon.titan-embed-text-v1' (invokeModel)
     * - 'amazon.titan-embed-text-v2:0' (invokeModel)
     * - 'amazon.nova-micro-v1:0' (invokeModel)
     * - 'amazon.titan-text-lite-v1' (invokeModel)
     *
     * See https://docs.aws.amazon.com/bedrock/latest/userguide/getting-started-api.html.
     */
    interface Bedrock {
        /* Name of env variable containing Bedrock access key that can be used for integration testing. */
        String ACCESS_KEY_ENV = "BEDROCK_ACCESS_KEY";
        /* Name of env variable containing Bedrock secret access key that can be used for integration testing. */
        String SECRET_ACCESS_KEY_ENV = "BEDROCK_SECRET_ACCESS_KEY";
        /* Name of env variable containing Bedrock API key that can be used for integration testing. */
        String API_KEY_ENV = "BEDROCK_API_KEY";
        /* Name of env variable containing Bedrock region to use with the keys above. */
        String REGION_ENV = "BEDROCK_REGION";
    }

    /*
     * Azure access.
     *
     * Needs access to the following models:
     * - 'gpt-5-mini'
     *
     * See https://learn.microsoft.com/en-us/azure/ai-foundry/openai/latest.
     */
    interface AzureOpenAi {
        /* Name of env variable containing Azure OpenAI access token that can be used for integration testing. */
        String TOKEN_ENV = "AZURE_OPENAI_TOKEN";
        /* Name of env variable containing Azure OpenAI resource name. */
        String RESOURCE_ENV = "AZURE_OPENAI_RESOURCE";
    }
}

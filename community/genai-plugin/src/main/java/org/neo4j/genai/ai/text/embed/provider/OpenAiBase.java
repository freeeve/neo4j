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
package org.neo4j.genai.ai.text.embed.provider;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.genai.ai.text.embed.VectorEmbedding;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.storable.VectorValue;

public interface OpenAiBase<PARAMS> extends VectorEmbedding.Provider.Implementation {
    String ENCODING_FORMAT = "float";

    URI endpoint();

    String providerName();

    HttpService httpService();

    PARAMS params();

    String[] authHeader();

    void extendPayload(MutableMap<String, Object> payload);

    /**
     * Allows customisation of a {@link HttpRequest request} during its built-time.
     * @param builder the request builder to customise
     * @return the customised builder
     */
    private HttpRequest.Builder customize(HttpRequest.Builder builder) {
        return builder;
    }

    @Override
    default VectorValue encode(String resource) {
        var vectors = encode(List.of(resource), EMPTY_INT_ARRAY).toList();
        if (vectors.size() != 1) {
            throw new MalformedGenAIResponseException(
                    "Expected exactly one vector embedding, but found " + vectors.size());
        }
        return vectors.getFirst().vector();
    }

    @Override
    default Stream<VectorEmbedding.InternalBatchRow> encodeBatch(List<String> resources, int[] nullIndexes) {
        return encode(resources, nullIndexes);
    }

    private Stream<VectorEmbedding.InternalBatchRow> encode(List<String> resources, int[] nullIndexes) {
        return httpService()
                .request(
                        endpoint(),
                        builder -> customize(builder.headers(
                                                "Content-Type",
                                                "application/json; charset=" + StandardCharsets.UTF_8,
                                                "Accept",
                                                "application/json")
                                        .headers(authHeader())
                                        .POST(HttpService.pipe(
                                                outputStream -> writeRequestPayload(outputStream, resources))))
                                .build(),
                        inputStream -> parseResponse(resources, inputStream, nullIndexes));
    }

    private Stream<VectorEmbedding.InternalBatchRow> parseResponse(
            List<String> resources, InputStream inputStream, int[] nullIndexes) {
        return parseResponse(providerName(), resources, inputStream, nullIndexes);
    }

    /*
    relevant part of response:
        {
            "data": [
                (vector:List<Double>),
                (vector:List<Double>),
                ...,
                (vector:List<Double>)
            ]
        }
    */
    @VisibleForTesting
    public static Stream<VectorEmbedding.InternalBatchRow> parseResponse(
            String providerName, List<String> resources, InputStream inputStream, int[] nullIndexes)
            throws MalformedGenAIResponseException {
        final String[] properties = {"embedding"};
        return JsonUtils.parseResponse(providerName, "data", properties, resources, inputStream, nullIndexes);
    }

    /*
     payload:
        {
           "input": [
               (resource:String),
               (resource:String),
               ...,
               (resource:String)
           ],
           "encoding_format": "float",
           <provider specific fields>
       }
    */
    private Object buildPayload(List<String> resources) {
        final var payload = Maps.mutable.of(
                "input", resources,
                "encoding_format", ENCODING_FORMAT);
        extendPayload(payload);
        return payload;
    }

    private void writeRequestPayload(OutputStream out, List<String> resources) {
        try {
            JsonUtils.getObjectMapper().writeValue(out, buildPayload(resources));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

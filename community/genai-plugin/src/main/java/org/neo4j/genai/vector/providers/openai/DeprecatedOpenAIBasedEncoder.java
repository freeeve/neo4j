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
package org.neo4j.genai.vector.providers.openai;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_INT_ARRAY;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.genai.util.MalformedGenAIResponseException;
import org.neo4j.genai.vector.DeprecatedVectorEncoding.BatchRow;
import org.neo4j.genai.vector.DeprecatedVectorEncoding.Provider;
import org.neo4j.util.VisibleForTesting;

public abstract class DeprecatedOpenAIBasedEncoder implements Provider.Encoder {
    private static final String ENCODING_FORMAT = "float";

    private final URI endpoint;
    private final String providerName;
    private final OptionalLong dimensions;

    /**
     * Allows customisation of a {@link HttpRequest request} during its built-time.
     * @param builder the request builder to customise
     * @return the customised builder
     */
    protected HttpRequest.Builder customize(HttpRequest.Builder builder) {
        return builder;
    }

    /**
     * Extend the payload with provider-specific fields.
     * @param payload mutable base payload
     */
    protected void extendPayload(MutableMap<String, Object> payload) {}

    protected DeprecatedOpenAIBasedEncoder(String providerName, URI endpoint, OptionalLong dimensions) {
        // TODO: OptionalLong when it's supported by parameter thingy
        this.providerName = providerName;
        this.endpoint = endpoint;
        this.dimensions = dimensions;
    }

    @Override
    public float[] encode(HttpService httpService, String data) {
        return encode(httpService, List.of(data), EMPTY_INT_ARRAY)
                .findFirst()
                .orElseThrow()
                .vector();
    }

    @Override
    public Stream<BatchRow> encode(HttpService httpService, List<String> resources, int[] nullIndexes) {

        return httpService.request(
                endpoint,
                builder -> customize(builder.headers(
                                        "Content-Type",
                                        "application/json; charset=" + StandardCharsets.UTF_8,
                                        "Accept",
                                        "application/json")
                                .POST(HttpService.pipe(outputStream -> writeRequestPayload(outputStream, resources))))
                        .build(),
                inputStream -> parseResponse(resources, inputStream, nullIndexes));
    }

    private Stream<BatchRow> parseResponse(List<String> resources, InputStream inputStream, int[] nullIndexes) {
        return parseResponse(providerName, resources, inputStream, nullIndexes);
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
    public static Stream<BatchRow> parseResponse(
            String providerName, List<String> resources, InputStream inputStream, int[] nullIndexes)
            throws MalformedGenAIResponseException {
        final String[] properties = {"embedding"};
        return JsonUtils.deprecatedParseResponse(providerName, "data", properties, resources, inputStream, nullIndexes);
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
        dimensions.ifPresent(d -> payload.put("dimensions", d));
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

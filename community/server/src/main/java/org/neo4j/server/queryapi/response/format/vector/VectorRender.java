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
package org.neo4j.server.queryapi.response.format.vector;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.neo4j.server.queryapi.response.format.Fieldnames;
import org.neo4j.server.queryapi.types.CypherTypes;
import org.neo4j.server.queryapi.types.CypherVectorTypes;

/**
 * Renders Vectors
 * <p>
 * This class is constructed through @{@link VectorRenderFactory} since
 * it needs to initialized correctly.
 * <p>
 * All methods produce side-effects.
 */
public class VectorRender implements AutoCloseable {

    private final JsonGenerator jsonGenerator;
    private final CypherVectorTypes cypherVectorTypes;

    protected VectorRender(JsonGenerator jsonGenerator, CypherVectorTypes cypherVectorTypes) {
        this.jsonGenerator = jsonGenerator;
        this.cypherVectorTypes = cypherVectorTypes;
    }

    protected void init() throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypes.Vector.getValue());
        jsonGenerator.writeFieldName(Fieldnames.CYPHER_VALUE);
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("coordinatesType", cypherVectorTypes.name());
        jsonGenerator.writeFieldName("coordinates");
        jsonGenerator.writeStartArray();
    }

    public void renderCoordinate(byte coordinate) throws IOException {
        this.jsonGenerator.writeString(String.valueOf(coordinate));
    }

    public void renderCoordinate(short coordinate) throws IOException {
        this.jsonGenerator.writeString(String.valueOf(coordinate));
    }

    public void renderCoordinate(int coordinate) throws IOException {
        this.jsonGenerator.writeString(String.valueOf(coordinate));
    }

    public void renderCoordinate(long coordinate) throws IOException {
        this.jsonGenerator.writeString(String.valueOf(coordinate));
    }

    public void renderCoordinate(float coordinate) throws IOException {
        this.jsonGenerator.writeString(String.valueOf(coordinate));
    }

    public void renderCoordinate(double coordinate) throws IOException {
        this.jsonGenerator.writeString(String.valueOf(coordinate));
    }

    @Override
    public void close() throws IOException {
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
        jsonGenerator.writeEndObject();
    }
}

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
package org.neo4j.server.queryapi.request;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import org.neo4j.driver.internal.value.VectorValue;
import org.neo4j.server.queryapi.exception.UnsupportedTypeException;
import org.neo4j.server.queryapi.types.CypherVectorTypes;

public class VectorValueDeserializer extends StdDeserializer<VectorValue> {

    public VectorValueDeserializer() {
        super(VectorValue.class);
    }

    @Override
    public VectorValue deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var coordinatesTypeFieldName = p.nextFieldName();
        if (!coordinatesTypeFieldName.equals("coordinatesType")) {
            throw new JsonParseException(
                    p, "Expected property \'coordinatesType\' but found " + coordinatesTypeFieldName);
        }
        var coordinatesType = p.nextTextValue();
        var coordinatesFieldName = p.nextFieldName();
        if (!coordinatesFieldName.equals("coordinates")) {
            throw new JsonParseException(p, "Expected property \'coordinates\' but found " + coordinatesFieldName);
        }
        p.nextToken();
        var coordinates = p.readValueAs(String[].class);

        return CypherVectorTypes.safeValueOf(coordinatesType)
                .map(type -> type.read(coordinates))
                .orElseThrow(() -> new UnsupportedTypeException(
                        String.join(", ", coordinates), CypherVectorTypes.getTypeNames(), coordinatesType));
    }
}

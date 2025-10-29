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
import org.neo4j.server.queryapi.types.CypherVectorTypes;

/**
 * Holds the creation of Vectors on Query API
 */
public class VectorRenderFactory {

    private final JsonGenerator jsonGenerator;

    public VectorRenderFactory(JsonGenerator jsonGenerator) {
        this.jsonGenerator = jsonGenerator;
    }

    /**
     * Returns the vector render for a given type. The close tags are written when resource closes.
     */
    public VectorRender newVectorRender(CypherVectorTypes vectorType) throws IOException {
        var renderType = new VectorRender(jsonGenerator, vectorType);
        renderType.init();
        return renderType;
    }
}

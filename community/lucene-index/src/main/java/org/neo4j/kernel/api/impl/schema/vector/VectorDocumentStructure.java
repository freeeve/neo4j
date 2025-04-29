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
package org.neo4j.kernel.api.impl.schema.vector;

import org.apache.lucene.index.Term;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9Document;
import org.neo4j.kernel.api.impl.schema.vector.VectorSimilarityFunctions.LuceneVectorSimilarityFunction;
import org.neo4j.values.VectorCandidate;

public abstract class VectorDocumentStructure {
    static final String ENTITY_ID_KEY = "id";

    static Term newTermForChangeOrRemove(long id) {
        return new Term(ENTITY_ID_KEY, Long.toString(id));
    }

    abstract String vectorValueKeyFor(int dimensions);

    LuceneDocument createLuceneDocument(
            long id, VectorCandidate candidate, LuceneVectorSimilarityFunction similarityFunction) {
        final var vector = similarityFunction.maybeToValidVector(candidate);
        if (vector == null) {
            return null;
        }

        LuceneDocument document = new Lucene9Document();
        document.addStringField(ENTITY_ID_KEY, Long.toString(id), false);
        document.addNumericField(ENTITY_ID_KEY, id);
        document.addKnnFloatVectorField(vectorValueKeyFor(vector.length), vector, similarityFunction);
        return document;
    }
}

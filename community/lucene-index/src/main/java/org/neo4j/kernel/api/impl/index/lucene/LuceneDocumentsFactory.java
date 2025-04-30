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
package org.neo4j.kernel.api.impl.index.lucene;

import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9DocumentsFactory;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.kernel.api.impl.schema.vector.VectorSimilarityFunctions;
import org.neo4j.values.VectorCandidate;
import org.neo4j.values.storable.Value;

public interface LuceneDocumentsFactory {
    LuceneDocumentsFactory CURRENT = Lucene9DocumentsFactory.INSTANCE;

    String TRIGRAM_ENTITY_ID_KEY = "id";
    String TRIGRAM_VALUE_KEY = "0";

    String VECTOR_ENTITY_ID_KEY = "id";

    LuceneDocument reusableTextDocument(long id, Value... values);

    LuceneDocument reusableFulltextDocument(long id, String[] propertyNames, Value[] values);

    LuceneDocument createTrigramDocument(long id, Value value);

    LuceneDocument createVectorDocument(
            VectorDocumentStructure vectorDocumentStructure,
            long id,
            VectorCandidate candidate,
            VectorSimilarityFunctions.LuceneVectorSimilarityFunction similarityFunction);

    LuceneDocument newDocument();
}

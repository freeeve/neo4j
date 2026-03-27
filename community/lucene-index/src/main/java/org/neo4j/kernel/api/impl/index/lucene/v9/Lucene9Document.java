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
package org.neo4j.kernel.api.impl.index.lucene.v9;

import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction;
import org.neo4j.shaded.lucene9.document.Document;
import org.neo4j.shaded.lucene9.document.Field;
import org.neo4j.shaded.lucene9.document.KnnFloatVectorField;
import org.neo4j.shaded.lucene9.document.NumericDocValuesField;
import org.neo4j.shaded.lucene9.document.StringField;
import org.neo4j.shaded.lucene9.document.TextField;
import org.neo4j.shaded.lucene9.index.VectorSimilarityFunction;

class Lucene9Document implements LuceneDocument {
    final Document document;

    Lucene9Document() {
        this(new Document());
    }

    Lucene9Document(Document document) {
        this.document = document;
    }

    @Override
    public void addNumericDocValuesField(String key, long value) {
        document.add(new NumericDocValuesField(key, value));
    }

    @Override
    public void addStringField(String key, String string, boolean store) {
        document.add(new StringField(key, string, store ? Field.Store.YES : Field.Store.NO));
    }

    @Override
    public void addTextField(String key, String textValue, boolean store) {
        document.add(new TextField(key, textValue, store ? Field.Store.YES : Field.Store.NO));
    }

    @Override
    public void addKnnFloatVectorField(String key, float[] vector, Neo4jVectorSimilarityFunction similarityFunction) {
        document.add(new KnnFloatVectorField(key, vector, toLucene(similarityFunction)));
    }

    @Override
    public String get(String key) {
        return document.get(key);
    }

    private static VectorSimilarityFunction toLucene(Neo4jVectorSimilarityFunction similarityFunction) {
        return switch (similarityFunction) {
            case EUCLIDEAN -> VectorSimilarityFunction.EUCLIDEAN;
            case SIMPLE_COSINE -> VectorSimilarityFunction.COSINE;
            case L2_NORM_COSINE -> VectorSimilarityFunction.DOT_PRODUCT;
        };
    }
}

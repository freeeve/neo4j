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
package org.neo4j.kernel.api.impl.index.lucene.v10;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.util.Preconditions;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;

public class Lucene10DocumentsFactory implements LuceneDocumentsFactory {
    public static final LuceneDocumentsFactory INSTANCE = new Lucene10DocumentsFactory();

    @Override
    public LuceneDocument newDocument() {
        return new Lucene10Document();
    }

    @Override
    public LuceneDocument reusableTextDocument(long id, Value... values) {
        return Lucene10ReusableDocWithId.reusableTextDocument(id, values);
    }

    @Override
    public LuceneDocument reusableFulltextDocument(long id, String[] propertyNames, Value[] values) {
        return Lucene10ReusableDocWithId.reusableFulltextDocument(id, propertyNames, values);
    }

    @Override
    public LuceneDocument createTrigramDocument(long id, Value value) {
        var document = new Lucene10Document();
        document.addStringField(ENTITY_ID_KEY, Long.toString(id), false);
        document.addNumericField(ENTITY_ID_KEY, id);
        if (value.valueGroup() == ValueGroup.TEXT) {
            var tokenStream = new Lucene10TrigramTokenStream(value.asObject().toString());
            var valueField = new TrigramField(TRIGRAM_VALUE_KEY, tokenStream);
            document.document.add(valueField);
        }

        return document;
    }

    @Override
    public LuceneDocument createVectorDocument(
            VectorDocumentStructure vectorDocumentStructure,
            long id,
            Neo4jVectorSimilarityFunction similarityFunction,
            Value[] values) {
        Preconditions.checkArgument(
                values != null && values.length == 1,
                "%s vector documents can only receive a single value",
                getClass().getSimpleName());
        float[] vector = LuceneDocumentsFactory.maybeVectorFromValues(values, similarityFunction);
        if (vector == null) {
            return null;
        }

        LuceneDocument document = new Lucene10Document();
        document.addStringField(ENTITY_ID_KEY, Long.toString(id), false);
        document.addNumericField(ENTITY_ID_KEY, id);
        document.addKnnFloatVectorField(
                vectorDocumentStructure.vectorValueKeyFor(vector.length), vector, similarityFunction);
        return document;
    }

    private static class TrigramField extends Field {
        private static final FieldType TYPE = new FieldType();

        static {
            TYPE.setOmitNorms(true);
            TYPE.setIndexOptions(IndexOptions.DOCS);
            TYPE.setTokenized(true);
            TYPE.setStored(false);
            TYPE.freeze();
        }

        private TrigramField(String name, TokenStream tokenStream) {
            super(name, tokenStream, TYPE);
        }
    }
}

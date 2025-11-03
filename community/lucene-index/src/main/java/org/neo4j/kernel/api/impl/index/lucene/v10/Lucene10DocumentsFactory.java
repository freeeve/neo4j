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

import java.time.temporal.ChronoField;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.BooleanField;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.SingleDoubleField;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.SingleLongField;
import org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.util.Preconditions;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.ByteValue;
import org.neo4j.values.storable.DoubleValue;
import org.neo4j.values.storable.FloatValue;
import org.neo4j.values.storable.IntValue;
import org.neo4j.values.storable.LongValue;
import org.neo4j.values.storable.ShortValue;
import org.neo4j.values.storable.TemporalValue;
import org.neo4j.values.storable.TextValue;
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
                values != null && values.length >= 1,
                "%s vector document has no values",
                getClass().getSimpleName());
        float[] vector = LuceneDocumentsFactory.maybeVectorFromValues(values, similarityFunction);
        if (vector == null) {
            return null;
        }

        Lucene10Document document = new Lucene10Document();
        document.addStringField(ENTITY_ID_KEY, Long.toString(id), false);
        document.addNumericField(ENTITY_ID_KEY, id);
        document.addKnnFloatVectorField(
                vectorDocumentStructure.vectorValueKeyFor(vector.length), vector, similarityFunction);

        for (int i = 1; i < values.length; i++) {
            Value value = values[i];
            IndexableField field = indexableField(vectorDocumentStructure, i, value);
            if (field == null) {
                // value type not supported for metadata filter
                continue;
            }
            document.document.add(field);
        }

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

    /**
     *
     * @param vectorDocumentStructure used to infer the name of the index field
     * @param index {@code i}-th field in the index filter parameters
     * @param value to index the document with
     * @return a Lucene indexable field, or null if the input {@code value} is null
     */
    static IndexableField indexableField(VectorDocumentStructure vectorDocumentStructure, int index, Value value) {
        return switch (value) {
            case BooleanValue bv ->
                new BooleanField(vectorDocumentStructure.booleanValueKeyFor(index), bv.booleanValue());
            case ByteValue bv -> new SingleLongField(vectorDocumentStructure.integralValueKeyFor(index), bv.intValue());
            case ShortValue sv ->
                new SingleLongField(vectorDocumentStructure.integralValueKeyFor(index), sv.intValue());
            case IntValue iv -> new SingleLongField(vectorDocumentStructure.integralValueKeyFor(index), iv.value());
            case LongValue lv -> new SingleLongField(vectorDocumentStructure.integralValueKeyFor(index), lv.value());
            case FloatValue fv -> new SingleDoubleField(vectorDocumentStructure.floatingValueKeyFor(index), fv.value());
            case DoubleValue dv ->
                new SingleDoubleField(vectorDocumentStructure.floatingValueKeyFor(index), dv.value());
            case TextValue tv ->
                new StringField(vectorDocumentStructure.textValueKeyFor(index), tv.stringValue(), Store.NO);
            case TemporalValue<?, ?> tv ->
                new LongPoint(
                        vectorDocumentStructure.temporalValueKeyFor(index),
                        tv.getLong(ChronoField.EPOCH_DAY),
                        tv.getLong(ChronoField.NANO_OF_DAY),
                        tv.getLong(ChronoField.OFFSET_SECONDS));
            case null -> null;
            default ->
                throw new IllegalArgumentException(String.format(
                        "Unsupported value type: %s for vector index field %d", value.getTypeName(), index));
        };
    }
}

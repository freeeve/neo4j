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
package org.neo4j.kernel.api.impl.schema.trigram;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9Document;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;

public class TrigramDocumentStructure {
    static final String ENTITY_ID_KEY = "id";
    public static final String TRIGRAM_VALUE_KEY = "0";

    static LuceneDocument createLuceneDocument(long id, Value value) {
        var document = new Lucene9Document();
        document.addStringField(ENTITY_ID_KEY, Long.toString(id), false);
        document.addNumericField(ENTITY_ID_KEY, id);
        if (value.valueGroup() == ValueGroup.TEXT) {
            var tokenStream = new TrigramTokenStream(value.asObject().toString());
            var valueField = new TrigramField(TRIGRAM_VALUE_KEY, tokenStream);
            document.add(valueField);
        }

        return document;
    }

    public static class TrigramField extends Field {
        private static final FieldType TYPE = new FieldType();

        static {
            TYPE.setOmitNorms(true);
            TYPE.setIndexOptions(IndexOptions.DOCS);
            TYPE.setTokenized(true);
            TYPE.setStored(false);
            TYPE.freeze();
        }

        public TrigramField(String name, TokenStream tokenStream) {
            super(name, tokenStream, TYPE);
        }
    }
}

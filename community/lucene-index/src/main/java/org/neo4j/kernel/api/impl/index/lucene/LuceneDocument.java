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

import java.util.Iterator;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.neo4j.kernel.api.impl.schema.trigram.TrigramDocumentStructure;
import org.neo4j.kernel.api.impl.schema.vector.VectorSimilarityFunctions;

public interface LuceneDocument {

    void addNumericField(String key, long value);

    void addStringField(String key, String string, boolean store);

    void addKnnFloatVectorField(
            String key, float[] vector, VectorSimilarityFunctions.LuceneVectorSimilarityFunction lucene);

    void add(TrigramDocumentStructure.TrigramField valueField);

    String get(String key);

    void addTextField(String key, String textValue, boolean store);

    Document toLuceneDocument();

    /**
     * Temporary class to lazily cast LuceneDocument to Document until we have an abstract IndexWriter
     * that accepts LuceneDocuments directly.
     */
    class LazyDocumentCastingIterable implements Iterable<Iterable<? extends IndexableField>> {
        private final Iterator<LuceneDocument> iterator;

        public LazyDocumentCastingIterable(Iterable<LuceneDocument> document) {
            iterator = document.iterator();
        }

        @Override
        public Iterator<Iterable<? extends IndexableField>> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Iterable<? extends IndexableField> next() {
                    return iterator.next().toLuceneDocument();
                }
            };
        }
    }
}

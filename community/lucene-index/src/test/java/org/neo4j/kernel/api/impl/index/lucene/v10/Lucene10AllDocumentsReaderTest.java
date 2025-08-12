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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StandardDirectoryReader;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.kernel.api.impl.index.LucenePartitionsAllDocumentsReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneAllDocumentsReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.index.partition.PartitionSearcher;

class Lucene10AllDocumentsReaderTest {
    private final PartitionSearcher partitionSearcher1 = createPartitionSearcher(1, 0, 2);
    private final PartitionSearcher partitionSearcher2 = createPartitionSearcher(2, 1, 2);

    Lucene10AllDocumentsReaderTest() throws IOException {}

    @Test
    void allDocumentsMaxCount() {
        LucenePartitionsAllDocumentsReader allDocumentsReader = createAllDocumentsReader();
        assertEquals(3, allDocumentsReader.maxCount());
    }

    @Test
    void readAllDocuments() {
        LucenePartitionsAllDocumentsReader allDocumentsReader = createAllDocumentsReader();
        List<LuceneDocument> documents = Iterators.asList(allDocumentsReader.iterator());

        assertEquals(3, documents.size(), "Should have 1 document from first partition and 2 from second one.");
        assertEquals("1", documents.get(0).get("value"));
        assertEquals("3", documents.get(1).get("value"));
        assertEquals("4", documents.get(2).get("value"));
    }

    private LucenePartitionsAllDocumentsReader createAllDocumentsReader() {
        return new LucenePartitionsAllDocumentsReader(createPartitionReaders(), Collections.emptyList());
    }

    private List<LuceneAllDocumentsReader> createPartitionReaders() {
        LuceneAllDocumentsReader reader1 =
                new Lucene10AllDocumentsReader((Lucene10IndexSearcher) partitionSearcher1.getIndexSearcher());
        LuceneAllDocumentsReader reader2 =
                new Lucene10AllDocumentsReader((Lucene10IndexSearcher) partitionSearcher2.getIndexSearcher());
        return Arrays.asList(reader1, reader2);
    }

    private static PartitionSearcher createPartitionSearcher(int maxDoc, int partition, int maxSize)
            throws IOException {
        PartitionSearcher partitionSearcher = mock(PartitionSearcher.class);
        Lucene10IndexSearcher indexSearcher = mock(Lucene10IndexSearcher.class);
        IndexReader indexReader = mock(StandardDirectoryReader.class);

        when(partitionSearcher.getIndexSearcher()).thenReturn(indexSearcher);
        when(indexSearcher.getIndexReader()).thenReturn(indexReader);
        when(indexReader.maxDoc()).thenReturn(maxDoc);

        when(indexSearcher.doc(0)).thenReturn(createDocument(uniqueDocValue(1, partition, maxSize)));
        when(indexSearcher.doc(1)).thenReturn(createDocument(uniqueDocValue(2, partition, maxSize)));
        when(indexSearcher.doc(2)).thenReturn(createDocument(uniqueDocValue(3, partition, maxSize)));

        return partitionSearcher;
    }

    private static String uniqueDocValue(int value, int partition, int maxSize) {
        return String.valueOf(value + (partition * maxSize));
    }

    private static LuceneDocument createDocument(String value) {
        LuceneDocument document = LuceneDocumentsFactory.CURRENT.newDocument();
        document.addStringField("value", value, true);
        return document;
    }
}

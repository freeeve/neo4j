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
package org.neo4j.kernel.api.impl.schema.writer;

import java.io.IOException;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriter;

/**
 * A thin wrapper around {@link LuceneIndexWriter} that exposes only some part of it's
 * functionality that it really needed and hides a fact that index is partitioned.
 */
public interface LucenePartitionIndexWriter {
    LuceneDocumentsFactory documentsFactory();

    void addDocument(LuceneDocument document) throws IOException;

    void addDocuments(int numDocs, Iterable<LuceneDocument> document) throws IOException;

    void updateDocument(String idField, long id, LuceneDocument document) throws IOException;

    void deleteDocuments(String idField, long id) throws IOException;

    /**
     * Adds the contents of a whole {@link LuceneDirectory} to this index.
     * @param count number of documents in the given directory.
     * @param directory the directory to add to this index.
     * @throws IOException on I/O error.
     */
    void addDirectory(int count, LuceneDirectory directory) throws IOException;

    /**
     * addDocument variant that can handle adds where the document to add has become empty
     * (this can happen if properties doesn't have a value type we support).
     */
    default void nullableAddDocument(LuceneDocument document) throws IOException {
        if (document != null) {
            addDocument(document);
        }
    }

    /**
     * updateDocument variant that handle updates where the document to update with has become empty and should be removed
     * (this can happen if properties doesn't have a value type we support).
     *
     * @param document The updated document or null if any existing version of it should be removed.
     */
    default void updateOrDeleteDocument(String idField, long id, LuceneDocument document) throws IOException {
        if (document != null) {
            updateDocument(idField, id, document);
        } else {
            deleteDocuments(idField, id);
        }
    }
}

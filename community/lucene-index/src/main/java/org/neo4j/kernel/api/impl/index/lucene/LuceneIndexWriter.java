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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.neo4j.graphdb.ResourceIterator;

public interface LuceneIndexWriter extends Closeable {
    int MAX_DOCS = Integer.MAX_VALUE - 128;
    int MAX_TERM_LENGTH = IndexWriter.MAX_TERM_LENGTH;
    String WRITE_LOCK_NAME = IndexWriter.WRITE_LOCK_NAME;

    String KEY_STATUS = "status";
    String ONLINE = "online";
    Set<Map.Entry<String, String>> ONLINE_COMMIT_USER_DATA = Set.of(Map.entry(KEY_STATUS, ONLINE));

    LuceneDocument newDocument();

    void updateDocument(Term term, LuceneDocument document) throws IOException;

    void rollback() throws IOException;

    DirectoryReader directoryReader() throws IOException;

    void commit() throws IOException;

    boolean hasCommits() throws IOException;

    void forceMerge(int i) throws IOException;

    void markAsOnline();

    void addDocuments(Iterable<LuceneDocument> documents) throws IOException;

    DocStats getDocStats();

    void deleteDocuments(Term term) throws IOException;

    void addIndexes(LuceneDirectory directory) throws IOException;

    void deleteDocuments(Query query) throws IOException;

    ResourceIterator<Path> snapshot(Path indexFolder) throws IOException;

    final class DocStats {
        /**
         * The total number of docs in this index, counting docs not yet flushed (still in the RAM
         * buffer), and also counting deleted docs. <b>NOTE:</b> buffered deletions are not counted. If
         * you really need these to be counted you should call {@link LuceneIndexWriter#commit()} first.
         */
        public final int maxDoc;

        /**
         * The total number of docs in this index, counting docs not yet flushed (still in the RAM
         * buffer), but not counting deleted docs.
         */
        public final int numDocs;

        public DocStats(int maxDoc, int numDocs) {
            this.maxDoc = maxDoc;
            this.numDocs = numDocs;
        }
    }

    void addDocument(LuceneDocument document) throws IOException;
}

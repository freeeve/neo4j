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

import java.io.IOException;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectoryReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneSearcherManager;
import org.neo4j.shaded.lucene9.index.DirectoryReader;
import org.neo4j.shaded.lucene9.index.IndexReader;
import org.neo4j.shaded.lucene9.search.IndexSearcher;
import org.neo4j.shaded.lucene9.search.SearcherFactory;
import org.neo4j.shaded.lucene9.search.SearcherManager;

public class Lucene9DirectoryReader implements LuceneDirectoryReader {
    private final DirectoryReader reader;

    public Lucene9DirectoryReader(DirectoryReader reader) {
        this.reader = reader;
    }

    @Override
    public boolean isOnline() throws IOException {
        return ONLINE.equals(reader.getIndexCommit().getUserData().get(KEY_STATUS));
    }

    @Override
    public LuceneSearcherManager newSearcherManager() throws IOException {
        SearcherManager searcherManager = new SearcherManager(reader, SEARCHER_FACTORY);
        return new Lucene9SearcherManager(searcherManager);
    }

    @Override
    public LuceneIndexSearcher newDirectSearcher() {
        return new Lucene9IndexSearcher(new Lucene9Neo4jIndexSearcher(reader));
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private static final SearcherFactory SEARCHER_FACTORY = new SearcherFactory() {
        @Override
        public IndexSearcher newSearcher(IndexReader reader, IndexReader previousReader) {
            return new Lucene9Neo4jIndexSearcher(reader);
        }
    };
}

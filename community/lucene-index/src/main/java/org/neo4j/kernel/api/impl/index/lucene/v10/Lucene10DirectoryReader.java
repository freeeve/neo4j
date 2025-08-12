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

import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectoryReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneSearcherManager;

public class Lucene10DirectoryReader implements LuceneDirectoryReader {
    private static final SearcherFactory SEARCHER_FACTORY = new SearcherFactory();

    private final DirectoryReader reader;

    public Lucene10DirectoryReader(DirectoryReader reader) {
        this.reader = reader;
    }

    @Override
    public boolean isOnline() throws IOException {
        return ONLINE.equals(reader.getIndexCommit().getUserData().get(KEY_STATUS));
    }

    @Override
    public LuceneSearcherManager newSearcherManager() throws IOException {
        SearcherManager searcherManager = new SearcherManager(reader, SEARCHER_FACTORY);
        return new Lucene10SearcherManager(searcherManager);
    }

    @Override
    public LuceneIndexSearcher newDirectSearcher() {
        return new Lucene10IndexSearcher(new IndexSearcher(reader));
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}

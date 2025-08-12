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
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneSearcherManager;
import org.neo4j.shaded.lucene9.search.SearcherManager;

class Lucene9SearcherManager implements LuceneSearcherManager {
    final SearcherManager searcherManager;

    Lucene9SearcherManager(SearcherManager searcherManager) {
        this.searcherManager = searcherManager;
    }

    @Override
    public LuceneIndexSearcher acquire() throws IOException {
        return new Lucene9IndexSearcher(this);
    }

    @Override
    public void maybeRefreshBlocking() throws IOException {
        searcherManager.maybeRefreshBlocking();
    }

    @Override
    public void close() throws IOException {
        searcherManager.close();
    }
}

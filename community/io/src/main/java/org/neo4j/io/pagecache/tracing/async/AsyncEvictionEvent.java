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
package org.neo4j.io.pagecache.tracing.async;

import org.neo4j.io.pagecache.impl.muninn.swapper.PageSwapper;
import org.neo4j.io.pagecache.tracing.AutoCloseablePageCacheTracerEvent;
import org.neo4j.io.pagecache.tracing.PageReferenceTranslator;

public interface AsyncEvictionEvent extends AutoCloseablePageCacheTracerEvent {

    AsyncEvictionEvent NULL = new AsyncEvictionEvent() {
        @Override
        public void setFilePageId(long filePageId) {}

        @Override
        public void setSwapper(PageSwapper swapper) {}

        @Override
        public void setException(Exception exception) {}

        @Override
        public SubmitEvent beginAsyncSubmit(
                long pageRef, PageSwapper swapper, PageReferenceTranslator pageReferenceTranslator) {
            return SubmitEvent.NULL;
        }

        @Override
        public void evicted() {}

        @Override
        public void close() {}
    };

    /**
     * The file page id the evicted page was bound to.
     */
    void setFilePageId(long filePageId);

    /**
     * The swapper the evicted page was bound to.
     */
    void setSwapper(PageSwapper swapper);

    /**
     * Indicates that the eviction caused an exception to be thrown.
     * This can happen if some kind of IO error occurs.
     */
    void setException(Exception exception);

    /**
     * Submit a page for asyncronious eviction.
     */
    SubmitEvent beginAsyncSubmit(long pageRef, PageSwapper swapper, PageReferenceTranslator pageReferenceTranslator);

    /**
     * Mark page evicted without any flushing
     */
    void evicted();
}

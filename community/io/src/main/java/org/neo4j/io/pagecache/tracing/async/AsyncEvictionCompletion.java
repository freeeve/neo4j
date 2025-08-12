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

import org.neo4j.io.pagecache.tracing.AutoCloseablePageCacheTracerEvent;
import org.neo4j.io.pagecache.tracing.FreeListSizeTrackerEvent;
import org.neo4j.io.pagecache.tracing.PageFileSwapperTracer;

public interface AsyncEvictionCompletion extends AutoCloseablePageCacheTracerEvent, FreeListSizeTrackerEvent {
    AsyncEvictionCompletion NULL = new AsyncEvictionCompletion() {
        @Override
        public void freeListSize(int size) {}

        @Override
        public void addBytesWritten(int bytes, PageFileSwapperTracer swapperTracer) {}

        @Override
        public void addPagesCompleted(int pageCount, PageFileSwapperTracer swapperTracer) {}

        @Override
        public void close() {}
    };

    void addBytesWritten(int bytes, PageFileSwapperTracer swapperTracer);

    void addPagesCompleted(int pageCount, PageFileSwapperTracer swapperTracer);
}

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
package org.neo4j.io.pagecache.impl.muninn;

import org.neo4j.io.async.AsyncBlockAccessor;
import org.neo4j.io.async.AsyncCompletionHandler;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.tracing.DatabaseFlushEvent;

public class AsyncCheckpointCompletionHandler implements AsyncCompletionHandler {

    private final DatabaseFlushEvent flushEvent;

    public AsyncCheckpointCompletionHandler(DatabaseFlushEvent flushEvent) {
        this.flushEvent = flushEvent;
    }

    @Override
    public void handleCompletion(AsyncBlockAccessor accessor, long data, int result) {
        try (var completionEvent = flushEvent.asyncFlushCompletion()) {
            var asyncVectorIO = accessor.asyncVectorIOData(data);
            long[] ioPages = asyncVectorIO.pages();
            long[] ioFlushStamps = asyncVectorIO.flushStamps();
            for (int i = 0; i < ioPages.length; i++) {
                PageMetadata.unlockFlush(ioPages[i], ioFlushStamps[i], true);
            }
            completionEvent.addBytesWritten(ioPages.length * PageCache.PAGE_SIZE);
            completionEvent.addPagesCompleted(ioPages.length);
            completionEvent.reportIO(asyncVectorIO.numberOfBuffers());
        }
    }
}

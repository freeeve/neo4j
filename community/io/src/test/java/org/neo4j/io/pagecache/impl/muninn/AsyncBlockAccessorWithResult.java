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
import org.neo4j.io.async.AsyncVectorIOData;

class AsyncBlockAccessorWithResult implements AsyncBlockAccessor {

    private final AsyncVectorIOData asyncVectorIOData;

    AsyncBlockAccessorWithResult(AsyncVectorIOData asyncVectorIOData) {
        this.asyncVectorIOData = asyncVectorIOData;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void asyncWrite(int fileDescriptor, long pageRef, long offset, long bufferAddress, int bufferSize) {}

    @Override
    public void asyncRead(int fileDescriptor, long pageRef, long offset, long bufferAddress, int bufferSize) {}

    @Override
    public void asyncVectorWrite(
            int fileDescriptor,
            long offset,
            long[] bufferAddresses,
            int[] bufferSizes,
            int length,
            long[] pagesRefs,
            long[] flushStamps,
            int pagesToFlush) {}

    @Override
    public void asyncVectorRead(int fileDescriptor, long offset, long[] bufferAddress, int[] bufferSize) {}

    @Override
    public void completeSubmitted() {}

    @Override
    public AsyncVectorIOData asyncVectorIOData(long key) {
        return asyncVectorIOData;
    }

    @Override
    public void close() {}
}

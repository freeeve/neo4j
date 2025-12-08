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
package org.neo4j.index.internal.gbptree;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.CursorContext;

public interface FreeListIdProvider extends IdProvider {

    void initialize(FreelistMetaData freelistMetaData) throws IOException;

    void initializeAfterCreation(CursorCreator cursorCreator, long lastId) throws IOException;

    void flush(long stableGeneration, long unstableGeneration, CursorCreator cursorCreator, CursorContext cursorContext)
            throws IOException;

    FreelistMetaData metaData();

    interface Monitor {
        Monitor NO_MONITOR = new Monitor() { // Empty
                };

        /**
         * Called when a page id was acquired for storing released ids into.
         *
         * @param freelistPageId page id of the acquired page.
         */
        default void acquiredFreelistPageId(long freelistPageId) { // Empty by default
        }

        /**
         * Called when a free-list page was released due to all its ids being acquired.
         * A released free-list page ends up in the free-list itself.
         *
         * @param freelistPageId page if of the released page.
         */
        default void releasedFreelistPageId(long freelistPageId) { // Empty by default
        }
    }

    record FreelistMetaData(
            boolean multiVersioned,
            long lastId,
            FreelistPositions genFreelistPos,
            FreelistPositions versionFreelistPos) {
        static int VERSIONED_SIZE = Long.BYTES + FreelistPositions.SIZE * 2;
        static int NON_VERSIONED_SIZE = Long.BYTES + FreelistPositions.SIZE;

        static FreelistMetaData nonVersioned(long lastId, FreelistPositions genFreelistPos) {
            return new FreelistMetaData(false, lastId, genFreelistPos, null);
        }

        static FreelistMetaData versioned(
                long lastId, FreelistPositions genFreelistPos, FreelistPositions versionFreelistPos) {
            return new FreelistMetaData(true, lastId, genFreelistPos, versionFreelistPos);
        }

        static FreelistMetaData read(ByteBuffer buffer, boolean multiVersioned) {
            long lastId = buffer.getLong();
            FreelistPositions genFreelistPos = FreelistPositions.read(buffer);
            FreelistPositions versionFreelistPos = null;
            if (multiVersioned) {
                versionFreelistPos = FreelistPositions.read(buffer);
            }
            return new FreelistMetaData(multiVersioned, lastId, genFreelistPos, versionFreelistPos);
        }

        void write(PageCursor cursor) {
            cursor.putLong(lastId);
            genFreelistPos.write(cursor);
            if (multiVersioned) {
                versionFreelistPos.write(cursor);
            }
        }

        boolean isEmpty() {
            return lastId == 0L && genFreelistPos.isEmpty() && (!multiVersioned || versionFreelistPos.isEmpty());
        }
    }

    record FreelistPositions(long writePageId, long readPageId, int writePos, int readPos) {
        static int SIZE = Long.BYTES * 2 + Integer.BYTES * 2;

        static FreelistPositions read(ByteBuffer buffer) {
            long writePageId = buffer.getLong();
            long readPageId = buffer.getLong();
            int writePos = buffer.getInt();
            int readPos = buffer.getInt();
            return new FreelistPositions(writePageId, readPageId, writePos, readPos);
        }

        void write(PageCursor cursor) {
            cursor.putLong(writePageId);
            cursor.putLong(readPageId);
            cursor.putInt(writePos);
            cursor.putInt(readPos);
        }

        boolean isEmpty() {
            return writePageId == 0L && readPageId == 0L && writePos == 0 && readPos == 0;
        }
    }
}

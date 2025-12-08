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

import static org.neo4j.index.internal.gbptree.PointerChecking.checkOutOfBounds;
import static org.neo4j.io.pagecache.PageCursorUtil.get6BLong;
import static org.neo4j.io.pagecache.PageCursorUtil.getUnsignedInt;
import static org.neo4j.io.pagecache.PageCursorUtil.goTo;
import static org.neo4j.io.pagecache.PageCursorUtil.put6BLong;

import java.io.IOException;
import org.neo4j.index.internal.gbptree.FreeListIdProvider.FreelistPositions;
import org.neo4j.index.internal.gbptree.IdProvider.IdProviderVisitor;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.CursorContext;

class VersionSupportingFreelist {
    static final long NO_PAGE_ID = TreeNodeUtil.NO_NODE_FLAG;

    private final VersionSupportingFreelistStorage freelistStorage;
    private final FreelistIdSupplier freelistIdSupplier;
    private final FreelistIdReleaser freelistIdReleaser;

    private long writePageId;
    private int writePos;
    private long readPageId;
    private int readPos;

    VersionSupportingFreelist(
            int payloadSize,
            boolean versioned,
            FreelistIdSupplier freelistIdSupplier,
            FreelistIdReleaser freelistIdReleaser) {
        this.freelistStorage = new VersionSupportingFreelistStorage(payloadSize, versioned);
        this.freelistIdSupplier = freelistIdSupplier;
        this.freelistIdReleaser = freelistIdReleaser;
    }

    void initialize(FreelistPositions positions) {
        this.writePageId = positions.writePageId();
        this.writePos = positions.writePos();
        this.readPageId = positions.readPageId();
        this.readPos = positions.readPos();
    }

    void initializeAfterCreation(CursorCreator cursorCreator, long startPageId) throws IOException {
        initialize(new FreelistPositions(startPageId, startPageId, 0, 0));
        try (var cursor = cursorCreator.create()) {
            goTo(cursor, "free-list", startPageId);
            VersionSupportingFreelistStorage.initialize(cursor);
            checkOutOfBounds(cursor);
        }
    }

    /**
     * Writes id with unstable generation and release version at the tail of the current write page {@link VersionSupportingFreelist#writePageId}. If the current
     * page is full, it acquires a new page and starts appending at the first position.
     *
     * @param stableGeneration   current stable generation
     * @param unstableGeneration current unstable generation
     * @param releaseVersion     current committing transaction id
     * @param id                 page id to be released
     */
    void writeNextId(
            PageCursor cursor,
            long stableGeneration,
            long unstableGeneration,
            long releaseVersion,
            long id,
            CursorContext cursorContext)
            throws IOException {
        goTo(cursor, "Free-list write page ", writePageId);

        if (endOfPage(writePos)) {
            long newWritePageId =
                    freelistIdSupplier.acquire(stableGeneration, CursorCreator.bind(cursor), cursorContext);
            VersionSupportingFreelistStorage.setNext(cursor, newWritePageId);
            goTo(cursor, "Free-list write page ", newWritePageId);
            VersionSupportingFreelistStorage.initialize(cursor);
            writePos = 0;
            writePageId = newWritePageId;
        }

        freelistStorage.writeId(cursor, unstableGeneration, releaseVersion, id, writePos++);
    }

    /**
     * Reads the id at the current read position {@link VersionSupportingFreelist#readPos} in the current read page id {@link VersionSupportingFreelist#readPageId}.
     *
     * @return {@link FreelistEntry} at the current read position. If there's nothing to read, returns null.
     */
    FreelistEntry readId(PageCursor cursor) throws IOException {
        if (!moreToRead()) {
            return null;
        }

        if (endOfPage(readPos)) {
            goToNextReadPageId(cursor);
        }

        return readIdAt(cursor, readPageId, readPos);
    }

    /**
     * Moves to the next read position. If the end position of the current page is reached and there's more to read, it
     * jumps to the next page and places the readPos at the first position.
     */
    void goToNextId(PageCursor cursor) throws IOException {
        readPos++;
        if (endOfPage(readPos) && moreToRead()) {
            goToNextReadPageId(cursor);
        }
    }

    /**
     * @return true if there's more to read, i.e. the reads are in a previous page id and the read position is
     * before the write position.
     */
    boolean moreToRead() {
        return moreToRead(writePageId, readPageId, writePos, readPos);
    }

    /**
     * @return current write/read pageIds and positions
     */
    FreelistPositions positions() {
        return new FreelistPositions(writePageId, readPageId, writePos, readPos);
    }

    void visitFreelist(IdProviderVisitor visitor, CursorCreator cursorCreator) throws IOException {
        long currentReadPageId = readPageId;
        long currentWritePageId = writePageId;
        int currentReadPos = readPos;
        int currentWritePos = writePos;

        try (var cursor = cursorCreator.create()) {
            visitor.beginFreelistPage(currentReadPageId);
            while (moreToRead(currentWritePageId, currentReadPageId, currentWritePos, currentReadPos)) {
                FreelistEntry entry;
                do {
                    entry = readIdAt(cursor, currentReadPageId, currentReadPos);
                } while (cursor.shouldRetry());

                visitor.freelistEntry(entry.pageId, entry.generation, entry.releaseVersion, currentReadPos);
                if (endOfPage(++currentReadPos)) {
                    visitor.endFreelistPage(currentReadPageId);
                    do {
                        currentReadPageId = VersionSupportingFreelistStorage.next(cursor);
                    } while (cursor.shouldRetry());
                    currentReadPos = 0;
                    visitor.beginFreelistPage(currentReadPageId);
                }
            }
            visitor.endFreelistPage(currentReadPageId);
        }
    }

    private FreelistEntry readIdAt(PageCursor cursor, long readPageId, int readPos) throws IOException {
        goTo(cursor, "Free-list read page ", readPageId);
        return freelistStorage.readId(cursor, readPos);
    }

    private boolean moreToRead(long writePageId, long readPageId, int writePos, int readPos) {
        return readPageId != writePageId || readPos < writePos;
    }

    private void goToNextReadPageId(PageCursor cursor) throws IOException {
        goTo(cursor, "Free-list read page ", readPageId);
        long previousPageId = readPageId;
        readPageId = VersionSupportingFreelistStorage.next(cursor);
        readPos = 0;
        freelistIdReleaser.release(previousPageId);
    }

    private boolean endOfPage(int pos) {
        return pos >= freelistStorage.maxEntries;
    }

    interface FreelistIdReleaser {
        void release(long freelistId) throws IOException;
    }

    interface FreelistIdSupplier {
        long acquire(long stableGeneration, CursorCreator cursorCreator, CursorContext cursorContext)
                throws IOException;
    }

    record FreelistEntry(long pageId, long generation, long releaseVersion) {}

    static class VersionSupportingFreelistStorage {
        private static final int PAGE_ID_SIZE = GenerationSafePointer.POINTER_SIZE;
        private static final int BYTE_POS_NEXT = TreeNodeUtil.BYTE_POS_NODE_TYPE + Byte.BYTES;
        static final int ENTRY_SIZE = GenerationSafePointer.GENERATION_SIZE + PAGE_ID_SIZE;
        static final int VERSIONED_ENTRY_SIZE = ENTRY_SIZE + Long.BYTES;
        private static final int HEADER_LENGTH = BYTE_POS_NEXT + PAGE_ID_SIZE;

        private final int maxEntries;
        private final int entrySize;
        private final boolean versioned;

        VersionSupportingFreelistStorage(int payloadSize, boolean versioned) {
            this.entrySize = versioned ? VERSIONED_ENTRY_SIZE : ENTRY_SIZE;
            this.maxEntries = maxEntries(payloadSize, entrySize);
            this.versioned = versioned;
        }

        static void initialize(PageCursor cursor) {
            cursor.putByte(TreeNodeUtil.BYTE_POS_NODE_TYPE, TreeNodeUtil.NODE_TYPE_FREE_LIST_NODE);
        }

        void writeId(PageCursor cursor, long unstableGeneration, long releaseVersion, long pageId, int pos) {
            if (pageId == NO_PAGE_ID) {
                throw new IllegalArgumentException("Tried to write pageId " + pageId + " which means null");
            }
            assertPos(pos);
            GenerationSafePointer.assertGenerationOnWrite(unstableGeneration);
            cursor.setOffset(entryOffset(pos));
            cursor.putInt((int) unstableGeneration);
            put6BLong(cursor, pageId);
            if (versioned) {
                cursor.putLong(releaseVersion);
            }
        }

        FreelistEntry readId(PageCursor cursor, int pos) {
            assertPos(pos);
            cursor.setOffset(entryOffset(pos));
            long generation = getUnsignedInt(cursor);
            long pageId = get6BLong(cursor);
            long releaseVersion = versioned ? cursor.getLong() : Long.MIN_VALUE;
            return new FreelistEntry(pageId, generation, releaseVersion);
        }

        static void setNext(PageCursor cursor, long nextFreelistPage) {
            cursor.setOffset(BYTE_POS_NEXT);
            put6BLong(cursor, nextFreelistPage);
        }

        static long next(PageCursor cursor) {
            cursor.setOffset(BYTE_POS_NEXT);
            return get6BLong(cursor);
        }

        static int maxEntries(int payloadSize, int entrySize) {
            return (payloadSize - HEADER_LENGTH) / entrySize;
        }

        private int entryOffset(int pos) {
            return HEADER_LENGTH + pos * entrySize;
        }

        private void assertPos(int pos) {
            if (pos >= maxEntries) {
                throw new IllegalArgumentException("Pos " + pos + " too big, max entries " + maxEntries);
            }
            if (pos < 0) {
                throw new IllegalArgumentException("Negative pos " + pos);
            }
        }
    }
}

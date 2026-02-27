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

import static org.neo4j.io.pagecache.PageCursorUtil.goTo;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.PagedFile;
import org.neo4j.io.pagecache.context.CursorContext;

class VersionedFreelistIdProvider implements FreeListIdProvider {
    private static final int CACHE_SIZE = 30;
    private final VersionSupportingFreelist generationFreelist;
    private final VersionSupportingFreelist versionFreelist;

    private final ConcurrentLinkedDeque<Long> acquireCache = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<CachedId> generationReleaseCache = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<CachedId> versionReleaseCache = new ConcurrentLinkedDeque<>();

    private final AtomicLong lastId = new AtomicLong();
    private final PagedFile pagedFile;
    private final Monitor monitor;

    VersionedFreelistIdProvider(PagedFile pagedFile, Monitor monitor) {
        this.pagedFile = pagedFile;
        this.monitor = monitor;
        this.generationFreelist = new VersionSupportingFreelist(
                pagedFile.payloadSize(), false, this::acquireFreelistId, this::releaseFreelistId);
        this.versionFreelist = new VersionSupportingFreelist(
                pagedFile.payloadSize(), true, this::acquireFreelistId, this::releaseFreelistId);
    }

    VersionedFreelistIdProvider(PagedFile pagedFile) {
        this(pagedFile, Monitor.NO_MONITOR);
    }

    @Override
    public void initialize(FreelistMetaData freelistMetaData) {
        assert freelistMetaData.multiVersioned()
                : "Can't initialize a versioned freelist id provider with a non-versioned freelist metadata.";
        this.lastId.set(freelistMetaData.lastId());
        generationFreelist.initialize(freelistMetaData.genFreelistPos());
        versionFreelist.initialize(freelistMetaData.versionFreelistPos());
    }

    @Override
    public void initializeAfterCreation(CursorCreator cursorCreator, long lastId) throws IOException {
        generationFreelist.initializeAfterCreation(cursorCreator, lastId++);
        versionFreelist.initializeAfterCreation(cursorCreator, lastId);
        this.lastId.set(lastId);
    }

    @Override
    public long acquireNewId(long stableGeneration, CursorCreator cursorCreator, CursorContext cursorContext)
            throws IOException {
        long oldestVisibleVersion = cursorContext.getVersionContext().oldestVisibilityHorizon();
        long acquiredId = acquireNewIdFromFreelistsOrEnd(stableGeneration, oldestVisibleVersion, cursorCreator);
        try (var cursor =
                pagedFile.io(acquiredId, PagedFile.PF_SHARED_WRITE_LOCK | PagedFile.PF_NO_LOAD, cursorContext)) {
            zapPage(acquiredId, cursor);
        }
        return acquiredId;
    }

    @Override
    public void releaseId(
            long stableGeneration,
            long unstableGeneration,
            long id,
            CursorCreator cursorCreator,
            CursorContext cursorContext)
            throws IOException {
        generationReleaseCache.addLast(new IdWithoutVersion(id));
        if (generationReleaseCache.size() >= CACHE_SIZE) {
            flushReleaseCache(
                    generationReleaseCache, stableGeneration, unstableGeneration, cursorCreator, cursorContext);
        }
    }

    @Override
    public void releaseIdWithVersion(
            long stableGeneration,
            long unstableGeneration,
            long id,
            CursorCreator cursorCreator,
            CursorContext cursorContext)
            throws IOException {
        long releaseVersion = cursorContext.getVersionContext().committingTransactionId();
        versionReleaseCache.addLast(new IdWithVersion(id, releaseVersion));
        if (versionReleaseCache.size() >= CACHE_SIZE) {
            flushReleaseCache(versionReleaseCache, stableGeneration, unstableGeneration, cursorCreator, cursorContext);
        }
    }

    @Override
    public void flush(
            long stableGeneration, long unstableGeneration, CursorCreator cursorCreator, CursorContext cursorContext)
            throws IOException {
        // flush both release caches
        flushReleaseCache(generationReleaseCache, stableGeneration, unstableGeneration, cursorCreator, cursorContext);
        flushReleaseCache(versionReleaseCache, stableGeneration, unstableGeneration, cursorCreator, cursorContext);
    }

    @Override
    public void visitFreelist(IdProviderVisitor visitor, CursorCreator cursorCreator) throws IOException {
        generationFreelist.visitFreelist(visitor, cursorCreator);
        versionFreelist.visitFreelist(visitor, cursorCreator);
        acquireCache.forEach(id -> visitor.freelistEntry(id, Long.MIN_VALUE, Long.MIN_VALUE, -1));
        generationReleaseCache.forEach(id -> visitor.freelistEntryFromReleaseCache(id.id()));
        versionReleaseCache.forEach(id -> visitor.freelistEntryFromReleaseCache(id.id()));
    }

    @Override
    public long lastId() {
        return lastId.get();
    }

    @Override
    public FreelistMetaData metaData() {
        return FreelistMetaData.versioned(lastId.get(), generationFreelist.positions(), versionFreelist.positions());
    }

    private long acquireNewIdFromFreelistsOrEnd(
            long stableGeneration, long oldestVisibleVersion, CursorCreator cursorCreator) throws IOException {
        do {
            var entry = acquireCache.poll();
            if (entry != null) {
                return entry;
            }
            fillAcquireCache(stableGeneration, oldestVisibleVersion, cursorCreator);
        } while (!acquireCache.isEmpty());
        return nextId();
    }

    private synchronized void fillAcquireCache(
            long stableGeneration, long oldestVisibleVersion, CursorCreator cursorCreator) throws IOException {
        try (var cursor = cursorCreator.create()) {
            loadFromFreelist(versionFreelist, stableGeneration, oldestVisibleVersion, cursor);
            if (acquireCache.size() < CACHE_SIZE) {
                loadFromFreelist(generationFreelist, stableGeneration, oldestVisibleVersion, cursor);
            }
        }
    }

    private void loadFromFreelist(
            VersionSupportingFreelist freelist, long stableGeneration, long oldestVisibleVersion, PageCursor cursor)
            throws IOException {
        var entry = freelist.readId(cursor);
        while (entry != null && acquireCache.size() < CACHE_SIZE) {
            if (entry.generation() > stableGeneration || entry.releaseVersion() > oldestVisibleVersion) {
                // Still in use, may not be acquired
                break;
            }

            acquireCache.addLast(entry.pageId());
            freelist.goToNextId(cursor);
            entry = freelist.readId(cursor);
        }
    }

    private synchronized void flushReleaseCache(
            ConcurrentLinkedDeque<CachedId> releaseCache,
            long stableGeneration,
            long unstableGeneration,
            CursorCreator cursorCreator,
            CursorContext cursorContext)
            throws IOException {
        if (releaseCache.isEmpty()) {
            return;
        }

        try (var cursor = cursorCreator.create()) {
            CachedId cachedId;
            while ((cachedId = releaseCache.poll()) != null) {
                if (cachedId instanceof IdWithVersion(long id, long releaseVersion)) {
                    versionFreelist.writeNextId(
                            cursor, stableGeneration, unstableGeneration, releaseVersion, id, cursorContext);
                } else {
                    generationFreelist.writeNextId(
                            cursor, stableGeneration, unstableGeneration, Long.MIN_VALUE, cachedId.id(), cursorContext);
                }
            }
        }
    }

    private static void zapPage(long acquiredId, PageCursor cursor) throws IOException {
        // Zap the page, i.e. set all bytes to zero
        goTo(cursor, "newly allocated free-list page", acquiredId);
        cursor.zapPage();
    }

    private long nextId() {
        return lastId.incrementAndGet();
    }

    private void releaseFreelistId(long releasedId) {
        generationReleaseCache.addLast(new IdWithoutVersion(releasedId));
        monitor.releasedFreelistPageId(releasedId);
    }

    private long acquireFreelistId(long stableGeneration, CursorCreator cursorCreator, CursorContext cursorContext)
            throws IOException {
        long acquiredId = acquireNewId(stableGeneration, cursorCreator, cursorContext);
        monitor.acquiredFreelistPageId(acquiredId);
        return acquiredId;
    }

    private sealed interface CachedId permits IdWithoutVersion, IdWithVersion {
        long id();
    }

    private record IdWithVersion(long id, long releaseVersion) implements CachedId {}

    private record IdWithoutVersion(long id) implements CachedId {}
}

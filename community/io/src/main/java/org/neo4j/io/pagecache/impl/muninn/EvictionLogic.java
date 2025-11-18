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

import static org.neo4j.io.pagecache.impl.muninn.AsyncEvictionStatus.EVICTED;
import static org.neo4j.io.pagecache.impl.muninn.AsyncEvictionStatus.NOT_EVICTED;
import static org.neo4j.io.pagecache.impl.muninn.AsyncEvictionStatus.SUBMITTED;

import java.io.IOException;
import org.neo4j.io.async.AsyncBlockAccessor;
import org.neo4j.io.pagecache.impl.muninn.swapper.PageSwapper;
import org.neo4j.io.pagecache.tracing.EvictionEvent;
import org.neo4j.io.pagecache.tracing.EvictionEventOpportunity;
import org.neo4j.io.pagecache.tracing.PageFileSwapperTracer;
import org.neo4j.io.pagecache.tracing.PageReferenceTranslator;
import org.neo4j.io.pagecache.tracing.async.AsyncEvictionCompletion;
import org.neo4j.io.pagecache.tracing.async.AsyncEvictionEvent;

public class EvictionLogic {

    static boolean tryEvict(
            long pageRef,
            EvictionEventOpportunity evictionOpportunity,
            SwapperSet swapperSet,
            PageReferenceTranslator referenceTranslator)
            throws IOException {
        if (PageList.tryExclusiveLock(pageRef)) {
            if (PageList.isLoaded(pageRef)) {
                try (var evictionEvent = evictionOpportunity.beginEviction(referenceTranslator.toId(pageRef))) {
                    evict(pageRef, evictionEvent, swapperSet, referenceTranslator);
                    return true;
                }
            }
            PageList.unlockExclusive(pageRef);
        }
        return false;
    }

    private static void evict(
            long pageRef,
            EvictionEvent evictionEvent,
            SwapperSet swapperSet,
            PageReferenceTranslator referenceTranslator)
            throws IOException {
        long filePageId = PageList.getFilePageId(pageRef);
        evictionEvent.setFilePageId(filePageId);
        int swapperId = PageList.getSwapperId(pageRef);
        if (swapperId != 0) {
            // If the swapper id is non-zero, then the page was not only loaded, but also bound, and possibly modified.
            SwapperSet.SwapperMapping swapperMapping = swapperSet.getAllocation(swapperId);
            if (swapperMapping != null) {
                // The allocation can be null if the file has been unmapped, but there are still pages
                // lingering in the cache that were bound to file page in that file.
                PageSwapper swapper = swapperMapping.swapper;
                evictionEvent.setSwapper(swapper);

                if (PageList.isModified(pageRef)) {
                    if (swapper.isPageFlushable(pageRef)) {
                        flushModifiedPage(pageRef, evictionEvent, filePageId, swapper, referenceTranslator);
                    } else {
                        PageList.explicitlyMarkPageUnmodifiedUnderExclusiveLock(pageRef);
                    }
                }
                swapper.evicted(pageRef, filePageId);
            }
        }
        PageList.clearBinding(pageRef);
    }

    private static void flushModifiedPage(
            long pageRef,
            EvictionEvent evictionEvent,
            long filePageId,
            PageSwapper swapper,
            PageReferenceTranslator pageReferenceTranslator)
            throws IOException {
        try (var flushEvent = evictionEvent.beginFlush(pageRef, swapper, pageReferenceTranslator)) {
            try {
                long address = PageList.getAddress(pageRef);
                long bytesWritten = swapper.write(filePageId, address);
                PageList.explicitlyMarkPageUnmodifiedUnderExclusiveLock(pageRef);
                flushEvent.addBytesWritten(bytesWritten);
                flushEvent.addEvictionFlushedPages(1);
            } catch (IOException e) {
                PageList.unlockExclusive(pageRef);
                flushEvent.setException(e);
                evictionEvent.setException(e);
                throw e;
            }
        }
    }

    static AsyncEvictionStatus tryEvictAsync(
            AsyncBlockAccessor blockAccessor,
            long pageRef,
            EvictionEventOpportunity evictionOpportunity,
            SwapperSet swapperSet,
            PageReferenceTranslator referenceTranslator)
            throws IOException {
        if (PageList.tryExclusiveLock(pageRef)) {
            if (PageList.isLoaded(pageRef)) {
                try (var evictionEvent = evictionOpportunity.beginAsyncEviction(referenceTranslator.toId(pageRef))) {
                    return evictAsync(blockAccessor, pageRef, evictionEvent, swapperSet, referenceTranslator);
                }
            }
            PageList.unlockExclusive(pageRef);
        }
        return NOT_EVICTED;
    }

    private static AsyncEvictionStatus evictAsync(
            AsyncBlockAccessor blockAccessor,
            long pageRef,
            AsyncEvictionEvent asyncEvictionEvent,
            SwapperSet swapperSet,
            PageReferenceTranslator referenceTranslator)
            throws IOException {
        long filePageId = PageList.getFilePageId(pageRef);
        asyncEvictionEvent.setFilePageId(filePageId);
        int swapperId = PageList.getSwapperId(pageRef);
        if (swapperId != 0) {
            // If the swapper id is non-zero, then the page was not only loaded, but also bound, and possibly modified.
            SwapperSet.SwapperMapping swapperMapping = swapperSet.getAllocation(swapperId);
            if (swapperMapping != null) {
                // The allocation can be null if the file has been unmapped, but there are still pages
                // lingering in the cache that were bound to file page in that file.
                PageSwapper swapper = swapperMapping.swapper;
                asyncEvictionEvent.setSwapper(swapper);

                if (PageList.isModified(pageRef)) {
                    if (swapper.isPageFlushable(pageRef)) {
                        // flush the modified page
                        flushModifiedPageAsync(
                                blockAccessor, pageRef, asyncEvictionEvent, filePageId, swapper, referenceTranslator);
                        return SUBMITTED;
                    } else {
                        PageList.explicitlyMarkPageUnmodifiedUnderExclusiveLock(pageRef);
                    }
                }
                swapper.evicted(pageRef, filePageId);
            }
        }
        asyncEvictionEvent.evicted();
        PageList.clearBinding(pageRef);
        return EVICTED;
    }

    private static void flushModifiedPageAsync(
            AsyncBlockAccessor blockAccessor,
            long pageRef,
            AsyncEvictionEvent evictionEvent,
            long filePageId,
            PageSwapper swapper,
            PageReferenceTranslator pageReferenceTranslator)
            throws IOException {
        try (var asyncSubmit = evictionEvent.beginAsyncSubmit(pageRef, swapper, pageReferenceTranslator)) {
            try {
                long address = PageList.getAddress(pageRef);
                swapper.asyncWrite(blockAccessor, pageRef, filePageId, address);
                asyncSubmit.addSubmittedPages(1);
            } catch (Exception e) {
                PageList.unlockExclusive(pageRef);
                asyncSubmit.setException(e);
                evictionEvent.setException(e);
                throw e;
            }
        }
    }

    public static void onPageEvictionCompletion(
            long pageRef, long writtenBytes, AsyncEvictionCompletion evictionCompletion, SwapperSet swapperSet) {
        int swapperId = PageList.getSwapperId(pageRef);
        long filePageId = PageList.getFilePageId(pageRef);

        PageList.explicitlyMarkPageUnmodifiedUnderExclusiveLock(pageRef);
        PageFileSwapperTracer swapperTracer = PageFileSwapperTracer.NULL;
        if (swapperId != 0) {
            SwapperSet.SwapperMapping swapperMapping = swapperSet.getAllocation(swapperId);
            if (swapperMapping != null) {
                var swapper = swapperMapping.swapper;
                swapper.evicted(pageRef, filePageId);
                swapperTracer = swapper.fileSwapperTracer();
            }
        }
        evictionCompletion.addBytesWritten((int) writtenBytes, swapperTracer);
        evictionCompletion.addPagesCompleted(1, swapperTracer);

        PageList.clearBinding(pageRef);
    }

    public static void onPageEvictionFailure(long pageRef) {
        PageList.unlockExclusive(pageRef);
    }
}

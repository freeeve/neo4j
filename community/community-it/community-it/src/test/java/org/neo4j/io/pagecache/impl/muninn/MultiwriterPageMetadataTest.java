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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.util.concurrent.Futures;

class MultiwriterPageMetadataTest extends AbstractPageMetadataTest {

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void writeLocksMustNotBlockOtherWriteLocks(int pageId) {
        init(pageId);

        assertTimeoutPreemptively(TIMEOUT, () -> {
            PageMetadata.unlockExclusive(pageRef);
            assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
            assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
        });
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void writeLocksMustNotBlockOtherWriteLocksInOtherThreads(int pageId) {
        init(pageId);

        assertTimeoutPreemptively(TIMEOUT, () -> {
            PageMetadata.unlockExclusive(pageRef);
            int threads = 10;
            CountDownLatch end = new CountDownLatch(threads);
            Runnable runnable = () -> {
                assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
                end.countDown();
            };
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(runnable));
            }
            end.await();
            Futures.getAll(futures);
        });
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void writeLockCountOverflowMustThrow(int pageId) {
        init(pageId);
        assertThrows(
                IllegalMonitorStateException.class,
                () -> assertTimeoutPreemptively(TIMEOUT, () -> {
                    PageMetadata.unlockExclusive(pageRef);
                    //noinspection InfiniteLoopStatement
                    for (; ; ) {
                        assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
                    }
                }));
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void concurrentWriteLocksMustFailExclusiveLocks(int pageId) {
        init(pageId);

        PageMetadata.unlockExclusive(pageRef);
        PageMetadata.tryWriteLock(pageRef, singleWriter);
        PageMetadata.tryWriteLock(pageRef, singleWriter);
        PageMetadata.unlockWrite(pageRef);
        assertFalse(PageMetadata.tryExclusiveLock(pageRef));
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void unlockExclusiveAndTakeWriteLockMustAllowConcurrentWriteLocks(int pageId) {
        init(pageId);

        assertTimeoutPreemptively(TIMEOUT, () -> {
            // exclusive lock implied by constructor
            PageMetadata.unlockExclusiveAndTakeWriteLock(pageRef);
            assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
        });
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void unlockWriteAndTryTakeFlushLockWithOverlappingWriterAndThenUnlockFlushMustNotLowerModifiedFlag(int pageId) {
        init(pageId);

        PageMetadata.unlockExclusiveAndTakeWriteLock(pageRef);
        assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter)); // two write locks, now
        long stamp = PageMetadata.unlockWriteAndTryTakeFlushLock(pageRef); // one flush, one write lock
        assertThat(stamp).isNotEqualTo(0L);
        PageMetadata.unlockWrite(pageRef); // one flush, zero write locks
        assertTrue(PageMetadata.isModified(pageRef));
        PageMetadata.unlockFlush(pageRef, stamp, true); // flush is successful, but had one overlapping writer
        assertTrue(PageMetadata.isModified(pageRef)); // so it's still modified
    }
}

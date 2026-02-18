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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.util.concurrent.Futures;

class MultiversionedPageMetadataTest extends AbstractPageMetadataTest {

    @BeforeEach
    void setUp() {
        singleWriter = true;
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void onlySingleWriteLockIsSupportedForMultiVersionedPage(int pageId) {
        init(pageId);

        PageMetadata.unlockExclusive(pageRef);
        assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
        assertFalse(PageMetadata.tryWriteLock(pageRef, singleWriter));
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void writeLocksMustBlockPreventWriteLocksInOtherThreads(int pageId) {
        init(pageId);

        assertTimeoutPreemptively(TIMEOUT, () -> {
            PageMetadata.unlockExclusive(pageRef);
            assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));

            int threads = 10;
            CountDownLatch end = new CountDownLatch(threads);
            Runnable runnable = () -> {
                assertFalse(PageMetadata.tryWriteLock(pageRef, singleWriter));
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
    void concurrentWriteFailedLocksDoNotFailExclusiveLocks(int pageId) {
        init(pageId);

        PageMetadata.unlockExclusive(pageRef);
        assertTrue(PageMetadata.tryWriteLock(pageRef, singleWriter));
        assertFalse(PageMetadata.tryWriteLock(pageRef, singleWriter));
        PageMetadata.unlockWrite(pageRef);
        assertTrue(PageMetadata.tryExclusiveLock(pageRef));
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void unlockExclusiveAndTakeWriteLockMustNotAllowConcurrentWriteLocks(int pageId) {
        init(pageId);

        assertTimeoutPreemptively(TIMEOUT, () -> {
            // exclusive lock implied by constructor
            PageMetadata.unlockExclusiveAndTakeWriteLock(pageRef);
            assertFalse(PageMetadata.tryWriteLock(pageRef, singleWriter));
        });
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void impossibleToTakeAnotherWriteLockWhenTransitionedFromExclusiveToWriteLock(int pageId) {
        init(pageId);

        PageMetadata.unlockExclusiveAndTakeWriteLock(pageRef);
        assertFalse(PageMetadata.tryWriteLock(pageRef, singleWriter));
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void concurrentWriteShouldBeBlocked(int pageId) throws InterruptedException, ExecutionException {
        init(pageId);

        PageMetadata.tryWriteLock(pageRef, singleWriter);

        int threads = 10;
        AtomicLong locksSneaked = new AtomicLong();
        AtomicBoolean execute = new AtomicBoolean(true);

        Runnable runnable = () -> {
            while (execute.get() && !PageMetadata.tryWriteLock(pageRef, singleWriter)) {
                // loop
            }
            locksSneaked.getAndIncrement();
        };

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(runnable));
        }
        SECONDS.sleep(ThreadLocalRandom.current().nextInt(5));
        assertEquals(0, locksSneaked.get());

        execute.set(false);

        Futures.getAll(futures);
    }

    @ParameterizedTest(name = "pageRef = {0}")
    @MethodSource("argumentsProvider")
    void singleWriteIsBlockedByMultiWrites(int pageId) {
        init(pageId);

        PageMetadata.unlockExclusive(pageRef);
        int iterations = ThreadLocalRandom.current().nextInt(1000);
        for (int i = 0; i < iterations; i++) {
            assertTrue(PageMetadata.tryWriteLock(pageRef, false), "Iteration " + i);
            assertFalse(PageMetadata.tryWriteLock(pageRef, true), "Iteration " + i);
        }
    }
}

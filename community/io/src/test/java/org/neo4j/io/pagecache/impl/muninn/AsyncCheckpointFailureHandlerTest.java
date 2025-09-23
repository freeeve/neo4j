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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.io.ByteUnit.MebiByte;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import org.junit.jupiter.api.Test;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.async.AsyncVectorIOData;
import org.neo4j.io.mem.MemoryAllocator;
import org.neo4j.memory.EmptyMemoryTracker;

class AsyncCheckpointFailureHandlerTest {

    @Test
    void unlockSinglePageFlushLockOnFailure() {
        int pageSize = (int) ByteUnit.kibiBytes(8);
        int pages = 10;

        try (MemoryAllocator mman = MemoryAllocator.createAllocator(MebiByte.toBytes(2), EmptyMemoryTracker.INSTANCE)) {
            SwapperSet swappers = new SwapperSet();
            long victimPage = VictimPageReference.getVictimPage(pageSize, INSTANCE);

            PageList pageList = new PageList(pages, pageSize, mman, swappers, victimPage, Long.BYTES);

            long pageRef = pageList.deref(0);
            PageList.unlockExclusive(pageRef);

            // page is modified
            assertTrue(PageList.tryWriteLock(pageRef, false));
            PageList.unlockWrite(pageRef);
            assertTrue(PageList.isModified(pageRef));

            long flushLock = PageList.tryFlushLock(pageRef);
            assertThat(flushLock).isNotZero();

            var failureHandler = new AsyncCheckpointFailureHandler();
            failureHandler.handleFailure(
                    new AsyncBlockAccessorWithResult(new AsyncVectorIOData(pageRef, flushLock)),
                    4,
                    0,
                    "bad news everyone");
            // page is still modified since flush lock was releases with false as success
            assertTrue(PageList.isModified(pageRef));

            // can flush lock again
            long flushLockAfterHandle = PageList.tryFlushLock(pageRef);
            assertThat(flushLockAfterHandle).isNotZero();
        }
    }

    @Test
    void unlockSeveralPageFlushLockOnFailure() {
        int pageSize = (int) ByteUnit.kibiBytes(8);
        int pages = 10;

        try (MemoryAllocator mman = MemoryAllocator.createAllocator(MebiByte.toBytes(2), EmptyMemoryTracker.INSTANCE)) {
            SwapperSet swappers = new SwapperSet();
            long victimPage = VictimPageReference.getVictimPage(pageSize, INSTANCE);

            PageList pageList = new PageList(pages, pageSize, mman, swappers, victimPage, Long.BYTES);

            long pageRef1 = pageList.deref(0);
            long pageRef2 = pageList.deref(1);
            long pageRef3 = pageList.deref(2);

            PageList.unlockExclusive(pageRef1);
            PageList.unlockExclusive(pageRef2);
            PageList.unlockExclusive(pageRef3);

            // modify pages
            assertTrue(PageList.tryWriteLock(pageRef1, false));
            PageList.unlockWrite(pageRef1);
            assertTrue(PageList.isModified(pageRef1));

            assertTrue(PageList.tryWriteLock(pageRef2, false));
            PageList.unlockWrite(pageRef2);
            assertTrue(PageList.isModified(pageRef2));

            assertTrue(PageList.tryWriteLock(pageRef3, false));
            PageList.unlockWrite(pageRef3);
            assertTrue(PageList.isModified(pageRef3));

            // flush locks
            long flushLock1 = PageList.tryFlushLock(pageRef1);
            long flushLock2 = PageList.tryFlushLock(pageRef2);
            long flushLock3 = PageList.tryFlushLock(pageRef3);

            assertThat(flushLock1).isNotZero();
            assertThat(flushLock2).isNotZero();
            assertThat(flushLock3).isNotZero();

            var failureHandler = new AsyncCheckpointFailureHandler();
            failureHandler.handleFailure(
                    new AsyncBlockAccessorWithResult(new AsyncVectorIOData(
                            new long[] {pageRef1, pageRef2, pageRef3},
                            new long[] {flushLock1, flushLock2, flushLock3})),
                    4,
                    0,
                    "bad news everyone");

            // pages are still modified anymore since flush lock was releases with false as success flag
            assertTrue(PageList.isModified(pageRef1));
            assertTrue(PageList.isModified(pageRef2));
            assertTrue(PageList.isModified(pageRef3));

            // can flush lock again
            long flushLockAfterHandle1 = PageList.tryFlushLock(pageRef1);
            assertThat(flushLockAfterHandle1).isNotZero();
            long flushLockAfterHandle2 = PageList.tryFlushLock(pageRef2);
            assertThat(flushLockAfterHandle2).isNotZero();
            long flushLockAfterHandle3 = PageList.tryFlushLock(pageRef3);
            assertThat(flushLockAfterHandle3).isNotZero();
        }
    }
}

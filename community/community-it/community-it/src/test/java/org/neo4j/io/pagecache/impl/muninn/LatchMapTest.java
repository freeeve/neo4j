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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.test.ThreadTestUtils;
import org.neo4j.util.concurrent.BinaryLatch;
import org.neo4j.util.concurrent.Futures;

class LatchMapTest {
    @ValueSource(ints = {LatchMap.DEFAULT_FAULT_LOCK_STRIPING, 1 << 10, 1 << 11})
    @ParameterizedTest
    void takeOrAwaitLatchMustReturnLatchIfAvailable(int size) {
        LatchMap latches = new LatchMap(size);
        BinaryLatch latch = latches.takeOrAwaitLatch(0);
        assertThat(latch).isNotNull();
        latch.release();
    }

    @ValueSource(ints = {LatchMap.DEFAULT_FAULT_LOCK_STRIPING, 1 << 10, 1 << 11})
    @ParameterizedTest
    void takeOrAwaitLatchMustAwaitExistingLatchAndReturnNull(int size) throws Exception {
        LatchMap latches = new LatchMap(size);
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        BinaryLatch latch = latches.takeOrAwaitLatch(42);
        assertThat(latch).isNotNull();
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<BinaryLatch> future = executor.submit(() -> {
                threadRef.set(Thread.currentThread());
                return latches.takeOrAwaitLatch(42);
            });
            Thread th;
            do {
                th = threadRef.get();
            } while (th == null);
            ThreadTestUtils.awaitThreadState(th, 10_000, Thread.State.WAITING);
            latch.release();
            assertThat(future.get(1, TimeUnit.SECONDS)).isNull();
        }
    }

    @ValueSource(ints = {LatchMap.DEFAULT_FAULT_LOCK_STRIPING, 1 << 10, 1 << 11})
    @ParameterizedTest
    void takeOrAwaitLatchMustNotLetUnrelatedLatchesConflictTooMuch(int size) throws Exception {
        LatchMap latches = new LatchMap(size);
        BinaryLatch latch = latches.takeOrAwaitLatch(42);
        assertThat(latch).isNotNull();
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<BinaryLatch> future = executor.submit(() -> latches.takeOrAwaitLatch(33));
            assertThat(future.get(30, TimeUnit.SECONDS)).isNotNull();
            latch.release();
        }
    }

    @ValueSource(ints = {LatchMap.DEFAULT_FAULT_LOCK_STRIPING, 1 << 10, 1 << 11})
    @ParameterizedTest
    void latchMustBeAvailableAfterRelease(int size) {
        LatchMap latches = new LatchMap(size);
        latches.takeOrAwaitLatch(42).release();
        latches.takeOrAwaitLatch(42).release();
    }

    @Test
    void shouldFailOnSizeNotPowerOfTwo() {
        assertThatThrownBy(() -> new LatchMap(123)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void largerLatchMapShouldAllowMoreLatches() {
        // given
        LatchMap latches = new LatchMap(512);

        // then
        List<LatchMap.Latch> latchList = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            latchList.add(latches.takeOrAwaitLatch(i)); // should not contend
        }
        latchList.forEach(LatchMap.Latch::release);
    }

    @Test
    void maxContinuousLatchesShouldRespectCapacity() {
        int size = 1024;
        LatchMap latches = new LatchMap(size);

        assertThat(latches.maxContinuousLatches(0, 100)).isEqualTo(100);
        assertThat(latches.maxContinuousLatches(0, 2000)).isEqualTo(size);
        assertThat(latches.maxContinuousLatches(512, 100)).isEqualTo(100);
        assertThat(latches.maxContinuousLatches(512, 1000)).isEqualTo(512);
        assertThat(latches.maxContinuousLatches(size - 1, 10)).isEqualTo(1);
        assertThat(latches.maxContinuousLatches(size, 100)).isEqualTo(100);
    }

    @Test
    void closePreventNewLatchesFromBeingTaken() {
        LatchMap latches = new LatchMap(8);
        latches.close();
        assertThat(latches.takeOrAwaitLatch(0)).isNull();
        assertThat(latches.takeOrAwaitLatch(3)).isNull();
        assertThat(latches.takeOrAwaitLatch(7)).isNull();
    }

    @Test
    void closeMustWaitForHeldLatchToBeReleasedBeforeCompleting() {
        LatchMap latches = new LatchMap(8);
        LatchMap.Latch heldLatch = latches.takeOrAwaitLatch(0);
        assertThat(heldLatch).isNotNull();

        BinaryLatch closeStarted = new BinaryLatch();
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> closeFuture = executor.submit(() -> {
                closeStarted.release();
                latches.close();
            });
            closeStarted.await();

            assertThat(closeFuture.isDone()).isFalse();
            heldLatch.release();

            assertThatCode(closeFuture::get).doesNotThrowAnyException();
        }
    }

    @Test
    void closedLatchMapNotAllowsAnyMoreLatches() {
        LatchMap latches = new LatchMap(8);
        LatchMap.Latch heldLatch = latches.takeOrAwaitLatch(0);
        assertThat(heldLatch).isNotNull();

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> closeFuture = executor.submit(latches::close);

            heldLatch.release();
            assertThatCode(closeFuture::get).doesNotThrowAnyException();
        }

        assertNull(latches.takeOrAwaitLatch(5));
    }

    @Test
    void concurrentReadersAndCloseMustNotLock() throws Exception {
        int numReaders = 8;
        LatchMap latches = new LatchMap(16);
        CountDownLatch allStarted = new CountDownLatch(numReaders);
        try (var executor = Executors.newFixedThreadPool(numReaders)) {
            List<Future<?>> readers = new ArrayList<>();
            for (int i = 0; i < numReaders; i++) {
                int latchId = i;
                readers.add(executor.submit(() -> {
                    allStarted.countDown();
                    // Keep taking and releasing the latch until the map is closed
                    while (true) {
                        LatchMap.Latch latch = latches.takeOrAwaitLatch(latchId);
                        if (latch == null) {
                            return;
                        }
                        latch.release();
                    }
                }));
            }

            allStarted.await();
            latches.close();

            Futures.getAll(readers);
        }
    }
}

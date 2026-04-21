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
package org.neo4j.internal.id.indexed;

import static java.util.Arrays.stream;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.LongStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.internal.id.IdGenerator.NO_ID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.neo4j.test.Race;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.scheduler.DaemonThreadFactory;

@RandomSupportExtension
class SeqMpmcLongQueueTest {
    @Inject
    private RandomSupport random;

    @Test
    void fillAndDrain() {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(4);
        assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(NO_ID);
        for (int i = 0; i < 4; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
        assertThat(queue.offer(100)).isFalse();
        for (int i = 0; i < 4; i++) {
            assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(i);
        }
        assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(NO_ID);
    }

    @Test
    void wrapAround() {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(16);
        for (int chunk = 1; chunk < 16; chunk++) {
            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < chunk; j++) {
                    assertThat(queue.offer(chunk * 1000 + i * 10 + j)).isTrue();
                }
                for (int j = 0; j < chunk; j++) {
                    assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(chunk * 1000 + i * 10 + j);
                }
            }
        }
        assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(NO_ID);
    }

    @Test
    void nonPowerOfTwoCapacityThrows() {
        assertThatThrownBy(() -> new SeqMpmcLongQueue(3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeqMpmcLongQueue(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeAndAvailableSpace() {
        int capacity = 8;
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(capacity);
        assertThat(queue.size()).isZero();
        assertThat(queue.availableSpace()).isEqualTo(capacity);

        for (int i = 1; i <= capacity; i++) {
            queue.offer(i);
            assertThat(queue.size()).isEqualTo(i);
            assertThat(queue.availableSpace()).isEqualTo(capacity - i);
        }

        for (int remaining = capacity; remaining > 0; remaining--) {
            queue.takeOrDefault(NO_ID);
            assertThat(queue.size()).isEqualTo(remaining - 1);
            assertThat(queue.availableSpace()).isEqualTo(capacity - remaining + 1);
        }
    }

    @Test
    void sizeAndAvailableSpaceAfterWrapAround() {
        int capacity = 4;
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(capacity);
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int i = 0; i < capacity; i++) {
                queue.offer(i);
            }
            assertThat(queue.size()).isEqualTo(capacity);
            assertThat(queue.availableSpace()).isZero();
            for (int i = 0; i < capacity; i++) {
                queue.takeOrDefault(NO_ID);
            }
            assertThat(queue.size()).isZero();
            assertThat(queue.availableSpace()).isEqualTo(capacity);
        }
    }

    @Test
    void canStoreNegativeOne() {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(4);
        assertThat(queue.offer(-1L)).isTrue();
        assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(-1L);
    }

    @Test
    void takeInRangeDoesNotConsumeOutOfRangeHead() {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(4);
        queue.offer(10L);

        // when head is out of range, takeInRange must leave it in the queue
        assertThat(queue.takeInRange(20, 30)).isEqualTo(Long.MAX_VALUE);
        assertThat(queue.size()).isOne();

        // item is still retrievable
        assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(10L);
    }

    @Test
    void takeInRangeBoundaries() {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(4);
        queue.offer(5L);

        // minBoundary is inclusive: 5 is inside [5, 10)
        assertThat(queue.takeInRange(5, 10)).isEqualTo(5L);

        queue.offer(10L);
        // maxBoundary is exclusive: 10 is outside [5, 10)
        assertThat(queue.takeInRange(5, 10)).isEqualTo(Long.MAX_VALUE);
        assertThat(queue.takeOrDefault(NO_ID)).isEqualTo(10L);
    }

    @Test
    void concurrentOffers() throws Throwable {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(128);
        Race race = new Race();
        race.addContestant(
                () -> {
                    for (int i = 0; i < 32; i++) {
                        assertThat(queue.offer(1L)).isTrue();
                    }
                },
                4);

        race.go();

        assertThat(queue.availableSpace()).isZero();
    }

    @Test
    void concurrentTake() throws Throwable {
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(128);
        for (int i = 0; i < 128; i++) {
            queue.offer(i);
        }

        Race race = new Race();
        race.addContestant(
                () -> {
                    for (int i = 0; i < 32; i++) {
                        assertThat(queue.takeOrDefault(NO_ID)).isNotEqualTo(NO_ID);
                    }
                },
                4);
        race.go();

        assertThat(queue.availableSpace()).isEqualTo(128);
    }

    @Test
    void randomizedConcurrent() throws Exception {
        // given
        int producers = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        int consumers = producers;
        int itemsPerConsumer = 10_000;
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(256);
        long[][] inputs = new long[producers][];
        int maxValue = producers * itemsPerConsumer;
        for (int i = 0; i < producers; i++) {
            long[] input =
                    range(i * itemsPerConsumer, (i + 1) * itemsPerConsumer).toArray();
            ArrayUtils.shuffle(input, random.random());
            inputs[i] = input;
        }
        long[][] outputs = new long[consumers][itemsPerConsumer];
        AtomicInteger numTakeInRange = new AtomicInteger();

        // when
        Collection<Callable<Void>> workers = new ArrayList<>();
        for (int producerId = 0; producerId < producers; producerId++) {
            workers.add(createProducer(queue, inputs[producerId]));
        }
        for (int consumerId = 0; consumerId < consumers; consumerId++) {
            workers.add(createConsumer(queue, outputs[consumerId], maxValue, numTakeInRange));
        }

        ExecutorService executor = newCachedThreadPool(new DaemonThreadFactory());
        try {
            List<Future<Void>> futures = executor.invokeAll(workers);
            for (Future<Void> future : futures) {
                future.get();
            }

            long[] expected =
                    stream(inputs).flatMapToLong(LongStream::of).sorted().toArray();
            long[] actual =
                    stream(outputs).flatMapToLong(LongStream::of).sorted().toArray();

            assertThat(actual).isEqualTo(expected);
            assertThat(numTakeInRange.longValue()).isGreaterThan(0);
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    @Test
    void concurrentStress() throws Exception {
        // Uses all available processors and a queue much smaller than the thread count
        // to maximize contention at the full/empty boundaries, which are the points where
        // memory ordering violations are most likely to surface on relaxed architectures.
        int threads = Runtime.getRuntime().availableProcessors();
        int itemsPerThread = 100_000;
        SeqMpmcLongQueue queue = new SeqMpmcLongQueue(64);

        long[][] produced = new long[threads][];
        for (int i = 0; i < threads; i++) {
            produced[i] = range((long) i * itemsPerThread, (long) (i + 1) * itemsPerThread)
                    .toArray();
            ArrayUtils.shuffle(produced[i], random.random());
        }
        long[][] consumed = new long[threads][itemsPerThread];

        Collection<Callable<Void>> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            workers.add(createProducer(queue, produced[i]));
        }
        for (int i = 0; i < threads; i++) {
            long[] output = consumed[i];
            workers.add(() -> {
                for (int j = 0; j < output.length; j++) {
                    output[j] = timeoutAware(() -> queue.takeOrDefault(NO_ID), v -> v != NO_ID);
                }
                return null;
            });
        }

        ExecutorService executor = newCachedThreadPool(new DaemonThreadFactory());
        try {
            for (Future<Void> future : executor.invokeAll(workers)) {
                future.get();
            }
            long[] expected =
                    stream(produced).flatMapToLong(LongStream::of).sorted().toArray();
            long[] actual =
                    stream(consumed).flatMapToLong(LongStream::of).sorted().toArray();
            assertThat(actual).isEqualTo(expected);
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    private static Callable<Void> createConsumer(
            SeqMpmcLongQueue queue, long[] output, int maxValue, AtomicInteger numTakeInRange) {
        return () -> {
            var rng = ThreadLocalRandom.current();
            for (int j = 0; j < output.length; j++) {
                output[j] = timeoutAware(() -> queue.takeOrDefault(NO_ID), v -> v != NO_ID);
                if (j < output.length - 1) {
                    int min = rng.nextInt(0, maxValue - 10);
                    int max = rng.nextInt(min, maxValue);
                    long takeInRange = queue.takeInRange(min, max);
                    if (takeInRange != Long.MAX_VALUE) {
                        output[++j] = takeInRange;
                        numTakeInRange.incrementAndGet();
                    }
                }
            }
            return null;
        };
    }

    private static Callable<Void> createProducer(SeqMpmcLongQueue queue, long[] input) {
        return () -> {
            for (long value : input) {
                timeoutAware(() -> queue.offer(value), result -> result);
            }
            return null;
        };
    }

    private static <T> T timeoutAware(Supplier<T> operation, Predicate<T> okValue) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + SECONDS.toMillis(5);
        while (System.currentTimeMillis() < endTime) {
            T value = operation.get();
            if (okValue.test(value)) {
                return value;
            }
        }
        throw new IllegalStateException("Operation didn't complete in reasonable time");
    }
}

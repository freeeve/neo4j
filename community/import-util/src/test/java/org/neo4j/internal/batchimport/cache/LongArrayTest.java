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
package org.neo4j.internal.batchimport.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.util.Arrays;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.neo4j.io.ByteUnit;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.test.Race;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@ExtendWith(RandomExtension.class)
class LongArrayTest {
    @Inject
    private TestDirectory testDirectory;

    @Inject
    private RandomSupport random;

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void largeIndex(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
        long length = 1_000_000_000;
        // Buffer allocation is lazy so unless we touch the whole array we do not run out of memory
        try (LongArray longArray = numberArrayFactory.newLongArray(length, 0, EmptyMemoryTracker.INSTANCE)) {
            for (long i = length - 80_000; i < length; i += 10_000) {
                longArray.set(i, i);
            }
            assertThatCode(() -> longArray.get(length)).isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void largeDynamicIndex(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
        long length = 1_000_000_000;
        int chunkSize = random.nextInt(32, (int) ByteUnit.mebiBytes(1));
        try (LongArray longArray = numberArrayFactory.newDynamicLongArray(chunkSize, 0, EmptyMemoryTracker.INSTANCE)) {
            for (long i = length - 1000; i < length; i++) {
                longArray.set(i, i);
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void setAndGet(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
        try (LongArray longArray = numberArrayFactory.newLongArray(10, 0, EmptyMemoryTracker.INSTANCE)) {
            longArray.set(0, 42);
            assertThat(longArray.get(0)).isEqualTo(42);

            assertThatCode(() -> longArray.set(Integer.MAX_VALUE, 42))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);

            assertThat(longArray.get(1)).isEqualTo(0); // Default value
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void concurrentSetAndGet(NumberArraysArgumentProvider.Factory factory) throws Throwable {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
        int threads = 40;
        int writesPerThread = 50;
        try (LongArray longArray = numberArrayFactory.newDynamicLongArray(128, 0, EmptyMemoryTracker.INSTANCE)) {
            Race race = new Race();
            for (int i = 0; i < threads; i++) {
                int threadId = i;
                race.addContestant(() -> {
                    for (int j = 0; j < writesPerThread; j++) {
                        longArray.set((threadId * writesPerThread + j), j);
                    }
                });
            }
            race.go();

            for (int i = 0; i < threads * writesPerThread; i++) {
                assertThat(longArray.get(i)).isEqualTo(i % writesPerThread);
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldHandleSomeRandomSetAndGet(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
        int length = random.nextInt(100_000) + 100;
        long defaultValue = random.nextInt(2) - 1; // 0 or -1
        try (LongArray array = numberArrayFactory.newLongArray(length, defaultValue, INSTANCE)) {
            long[] expected = new long[length];
            Arrays.fill(expected, defaultValue);

            // WHEN
            int operations = random.nextInt(1_000) + 10;
            for (int i = 0; i < operations; i++) {
                // THEN
                int index = random.nextInt(length);
                long value = random.nextLong();
                switch (random.nextInt(3)) {
                    case 0: // set
                        array.set(index, value);
                        expected[index] = value;
                        break;
                    case 1: // get
                        assertEquals(expected[index], array.get(index));
                        break;
                    default: // swap
                        int toIndex = random.nextInt(length);
                        array.swap(index, toIndex);
                        swap(expected, index, toIndex);
                        break;
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldHandleMultipleCallsToClose(NumberArraysArgumentProvider.Factory factory) {
        LongArray array = getNumberArrayFactory(factory).newLongArray(10, -1, INSTANCE);

        // WHEN
        array.close();

        // THEN should also work
        array.close();
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldWorkOnSingleChunk(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);

        // GIVEN
        long defaultValue = 0;
        try (LongArray array = numberArrayFactory.newDynamicLongArray(10, defaultValue, INSTANCE)) {
            array.set(4, 5);

            // WHEN
            assertEquals(5L, array.get(4));
            assertEquals(defaultValue, array.get(12));
            array.set(7, 1324);
            assertEquals(1324L, array.get(7));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldAddChunksAsNeeded(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
        // GIVEN
        try (LongArray array = numberArrayFactory.newDynamicLongArray(10, 0, INSTANCE)) {
            // WHEN
            long index = 243;
            long value = 5485748;
            array.set(index, value);

            // THEN
            assertEquals(value, array.get(index));
        }
    }

    private NumberArrayFactory getNumberArrayFactory(NumberArraysArgumentProvider.Factory factory) {
        return factory.create(testDirectory.getFileSystem(), testDirectory.homePath());
    }

    private static void swap(long[] expected, int fromIndex, int toIndex) {
        long fromValue = expected[fromIndex];
        expected[fromIndex] = expected[toIndex];
        expected[toIndex] = fromValue;
    }
}

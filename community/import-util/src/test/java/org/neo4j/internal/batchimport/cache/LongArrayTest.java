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
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.neo4j.io.ByteUnit;
import org.neo4j.test.Race;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@RandomSupportExtension
class LongArrayTest {
    @Inject
    private TestDirectory testDirectory;

    @Inject
    private RandomSupport random;

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void largeIndex(NumberArraysArgumentProvider.Factory factory) {
        long length = 1_000_000_000;
        // Buffer allocation is lazy so unless we touch the whole array we do not run out of memory
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray longArray = numberArrayFactory.newLongArray(length, 0, INSTANCE)) {
            for (long i = length - 80_000; i < length; i += 10_000) {
                longArray.set(i, i);
            }
            assertThatCode(() -> longArray.get(length)).isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void largeDynamicIndex(NumberArraysArgumentProvider.Factory factory) {
        long length = 1_000_000_000;
        int chunkSize = random.nextInt(32, (int) ByteUnit.mebiBytes(1));
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray longArray = numberArrayFactory.newDynamicLongArray(chunkSize, 0, INSTANCE)) {
            for (long i = length - 1000; i < length; i++) {
                longArray.set(i, i);
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void setAndGet(NumberArraysArgumentProvider.Factory factory) {
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray longArray = numberArrayFactory.newLongArray(10, 0, INSTANCE)) {
            longArray.set(0, 42);
            assertThat(longArray.get(0)).isEqualTo(42);

            assertThatCode(() -> longArray.set(Integer.MAX_VALUE, 42))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);

            assertThat(longArray.get(1)).isZero(); // Default value
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void concurrentSetAndGet(NumberArraysArgumentProvider.Factory factory) throws Throwable {
        int threads = 40;
        int writesPerThread = 50;
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray longArray = numberArrayFactory.newDynamicLongArray(128, 0, INSTANCE)) {
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
        int length = random.nextInt(100_000) + 100;
        long defaultValue = random.nextInt(2) - 1; // 0 or -1
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray array = numberArrayFactory.newLongArray(length, defaultValue, INSTANCE)) {
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
                        assertThat(array.get(index)).isEqualTo(expected[index]);
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
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory)) {
            LongArray array = numberArrayFactory.newLongArray(10, -1, INSTANCE);

            // WHEN
            array.close();

            // THEN should also work
            array.close();
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldWorkOnSingleChunk(NumberArraysArgumentProvider.Factory factory) {
        long defaultValue = 0;
        // GIVEN
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray array = numberArrayFactory.newDynamicLongArray(10, defaultValue, INSTANCE)) {
            array.set(4, 5);

            // WHEN
            assertThat(array.get(4)).isEqualTo(5L);
            assertThat(array.get(12)).isEqualTo(defaultValue);
            array.set(7, 1324);
            assertThat(array.get(7)).isEqualTo(1324L);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldAddChunksAsNeeded(NumberArraysArgumentProvider.Factory factory) {
        // GIVEN
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                LongArray array = numberArrayFactory.newDynamicLongArray(10, 0, INSTANCE)) {
            // WHEN
            long index = 243;
            long value = 5485748;
            array.set(index, value);

            // THEN
            assertThat(array.get(index)).isEqualTo(value);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldBeAbleToGetSetFromArrayOfBuffersSizeOne(NumberArraysArgumentProvider.Factory factory) {
        final var arrayLength = 134217728L; // the magic number came from an import that failed using the Cuckoo mapper
        final var defaultValue = 0;
        try (var numberArrayFactory = getNumberArrayFactory(factory);
                var array = numberArrayFactory.newLongArray(arrayLength, defaultValue, INSTANCE)) {
            // WHEN
            final var index = arrayLength / 2;
            final var value = 42;

            assertThat(array.get(index)).isEqualTo(defaultValue); // any index value would trigger previous error

            array.set(index, value);
            assertThat(array.get(index)).isEqualTo(value);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldBeAbleToGetSetPositionInLastInternalBuffer(NumberArraysArgumentProvider.Factory factory) {
        final var arrayLength = 536870912L; // the magic number came from an import that failed using the Cuckoo mapper
        final var index = 404975176L; // the magic number is the capacity of the internal buffers
        final var defaultValue = 0;
        try (var numberArrayFactory = getNumberArrayFactory(factory);
                var array = numberArrayFactory.newLongArray(arrayLength, defaultValue, INSTANCE)) {
            // WHEN
            final var value = 42;

            assertThat(array.get(index)).isEqualTo(defaultValue); // any index value would trigger previous error

            array.set(index, value);
            assertThat(array.get(index)).isEqualTo(value);
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

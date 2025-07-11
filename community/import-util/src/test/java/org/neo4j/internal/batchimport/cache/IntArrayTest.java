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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.neo4j.memory.LocalMemoryTracker;
import org.neo4j.memory.MemoryPools;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@ExtendWith(RandomExtension.class)
class IntArrayTest {
    @Inject
    private TestDirectory testDirectory;

    @Inject
    private RandomSupport random;

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldHandleSomeRandomSetAndGet(NumberArraysArgumentProvider.Factory factory) {
        // GIVEN
        int length = random.nextInt(100_000) + 100;
        int defaultValue = random.nextInt(2) - 1; // 0 or -1
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                IntArray array = numberArrayFactory.newIntArray(length, defaultValue, INSTANCE)) {
            int[] expected = new int[length];
            Arrays.fill(expected, defaultValue);

            // WHEN
            int operations = random.nextInt(1_000) + 10;
            for (int i = 0; i < operations; i++) {
                // THEN
                int index = random.nextInt(length);
                int value = random.nextInt();
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
        // GIVEN
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory)) {
            NumberArray array = numberArrayFactory.newIntArray(10, -1, INSTANCE);

            // WHEN
            array.close();

            // THEN should also work
            array.close();
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldWorkOnSingleChunk(NumberArraysArgumentProvider.Factory factory) {
        // GIVEN
        int defaultValue = 0;
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                IntArray array = numberArrayFactory.newDynamicIntArray(10, defaultValue, INSTANCE)) {
            array.set(4, 5);

            // WHEN
            assertEquals(5, array.get(4));
            assertEquals(defaultValue, array.get(12));
            array.set(7, 1324);
            assertEquals(1324, array.get(7));
        }
    }

    @Test
    void trackNativeMemoryOnArrayAllocations() {
        var memoryTracker = new LocalMemoryTracker(MemoryPools.NO_TRACKING, 300, 0, null);
        try (var longArray = NumberArrayFactories.OFF_HEAP.newDynamicLongArray(10, 0, memoryTracker)) {

            assertEquals(0, memoryTracker.estimatedHeapMemory());
            assertEquals(0, memoryTracker.usedNativeMemory());

            longArray.set(0, 5);

            assertEquals(0, memoryTracker.estimatedHeapMemory());
            assertEquals(64, memoryTracker.usedNativeMemory());
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void shouldAddChunksAsNeeded(NumberArraysArgumentProvider.Factory factory) {
        // GIVEN
        try (NumberArrayFactory numberArrayFactory = getNumberArrayFactory(factory);
                IntArray array = numberArrayFactory.newDynamicIntArray(10, 0, INSTANCE)) {

            // WHEN
            long index = 243;
            int value = 5485748;
            array.set(index, value);

            // THEN
            assertEquals(value, array.get(index));
        }
    }

    private NumberArrayFactory getNumberArrayFactory(NumberArraysArgumentProvider.Factory factory) {
        return factory.create(testDirectory.getFileSystem(), testDirectory.homePath());
    }

    private static void swap(int[] expected, int fromIndex, int toIndex) {
        int fromValue = expected[fromIndex];
        expected[fromIndex] = expected[toIndex];
        expected[toIndex] = fromValue;
    }
}

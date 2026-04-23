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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.internal.batchimport.cache.BufferFactories.fileBacked;
import static org.neo4j.internal.batchimport.cache.NumberArrayFactories.OFF_HEAP;
import static org.neo4j.internal.batchimport.cache.NumberArrayFactories.fromBufferFactory;
import static org.neo4j.logging.LogAssertions.assertThat;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.internal.unsafe.NativeMemoryAllocationRefusedError;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.NullLog;
import org.neo4j.memory.LocalMemoryTracker;
import org.neo4j.memory.MemoryPools;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class NumberArrayFactoryTest {
    private static final int KILO = 1024;

    @Inject
    private TestDirectory testDirectory;

    @Test
    void trackNativeMemoryAllocations() {
        var memoryTracker = new LocalMemoryTracker(MemoryPools.NO_TRACKING, 300, 0, null);
        try (ByteArray byteArray = OFF_HEAP.newByteArray(10, new byte[] {0}, memoryTracker)) {
            byteArray.setByte(0, 0, (byte) 1);
            assertThat(memoryTracker.estimatedHeapMemory()).isZero();
            assertThat(memoryTracker.usedNativeMemory()).isEqualTo(10);
        }
        assertThat(memoryTracker.usedNativeMemory()).isZero();
        assertThat(memoryTracker.estimatedHeapMemory()).isZero();

        try (LongArray longArray = OFF_HEAP.newLongArray(10, 0, memoryTracker)) {
            longArray.set(0, 1);
            assertThat(memoryTracker.estimatedHeapMemory()).isZero();
            assertThat(memoryTracker.usedNativeMemory()).isEqualTo(80);
        }
        assertThat(memoryTracker.usedNativeMemory()).isZero();
        assertThat(memoryTracker.estimatedHeapMemory()).isZero();

        try (IntArray intArray = OFF_HEAP.newIntArray(10, 0, memoryTracker)) {
            intArray.set(0, 1);
            assertThat(memoryTracker.estimatedHeapMemory()).isZero();
            assertThat(memoryTracker.usedNativeMemory()).isEqualTo(40);
        }
        assertThat(memoryTracker.usedNativeMemory()).isZero();
        assertThat(memoryTracker.estimatedHeapMemory()).isZero();
    }

    @Test
    void shouldPickFirstAvailableCandidateLongArray() {
        // WHEN
        try (LongArray array = OFF_HEAP.newLongArray(KILO, -1, INSTANCE)) {
            array.set(KILO - 10, 12345);

            // THEN
            assertThat(array.get(KILO - 10)).isEqualTo(12345);
        }
    }

    @Test
    void shouldPickFirstAvailableCandidateLongArrayWhenSomeDontHaveEnoughMemory() {
        // GIVEN
        BufferFactory lowMemoryFactory = mock(BufferFactory.class);
        doThrow(OutOfMemoryError.class).when(lowMemoryFactory).allocate(anyInt(), any());
        try (NumberArrayFactory factory = new NumberArrayFactories.NumberArrayFactoryImpl(
                new NumberArrayFactories.Auto(NullLog.getInstance(), lowMemoryFactory, BufferFactories.OFF_HEAP))) {

            // WHEN
            try (LongArray array = factory.newLongArray(KILO, -1, INSTANCE)) {
                array.set(KILO - 10, 12345);

                // THEN
                assertThat(array.get(KILO - 10)).isEqualTo(12345);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {OutOfMemoryError.class, NativeMemoryAllocationRefusedError.class})
    void shouldPickFirstAvailableCandidateIntArrayWhenSomeThrowOutOfMemoryError(Class<Exception> exception) {
        // GIVEN
        BufferFactory lowMemoryFactory = mock(BufferFactory.class);

        when(lowMemoryFactory.allocate(anyInt(), any())).thenThrow(exception);

        // WHEN
        try (NumberArrayFactory factory = NumberArrayFactories.fromBufferFactory(new NumberArrayFactories.Auto(
                        NullLog.getInstance(), lowMemoryFactory, BufferFactories.OFF_HEAP));
                IntArray array = factory.newIntArray(KILO, -1, INSTANCE)) {
            array.set(KILO - 10, 12345);

            // THEN
            verify(lowMemoryFactory).allocate(eq(KILO * Integer.BYTES), any(MemoryTracker.class));
            assertThat(array.get(KILO - 10)).isEqualTo(12345);
        }
    }

    @Test
    void logWhenStartingToSwap() {
        BufferFactory lowMemoryFactory = mock(BufferFactory.class);
        when(lowMemoryFactory.allocate(anyInt(), any())).thenThrow(OutOfMemoryError.class);

        BufferFactory swap = BufferFactories.fileBacked(testDirectory.getFileSystem(), testDirectory.homePath());
        AssertableLogProvider logProvider = new AssertableLogProvider();

        try (NumberArrayFactory factory = NumberArrayFactories.fromBufferFactory(
                new NumberArrayFactories.Auto(logProvider.getLog("test"), lowMemoryFactory, swap))) {

            try (IntArray array = factory.newIntArray(KILO, -1, INSTANCE)) {
                array.set(KILO - 10, 12345);
            }
        }

        assertThat(logProvider)
                .forLevel(AssertableLogProvider.Level.WARN)
                .containsMessages("Running low on memory and will start swapping to hard drive");
    }

    /**
     * Since the backing file is shared for different implementations of arrays we need to make sure that
     * all allocations are aligned to the largest alignment requirement.
     */
    @Test
    void alignedAccessRestrictions() {
        try (NumberArrayFactory numberArrayFactory =
                fromBufferFactory(fileBacked(testDirectory.getFileSystem(), testDirectory.homePath()))) {
            ByteArray byteArray = numberArrayFactory.newByteArray(5, new byte[1], INSTANCE);
            byteArray.set(4, new byte[] {1});

            LongArray longArray = numberArrayFactory.newLongArray(2, 0, INSTANCE);
            assertThatCode(() -> longArray.compareAndSet(1, 0, 1)).doesNotThrowAnyException();
        }
    }
}

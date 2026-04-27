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
package org.neo4j.io.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.neo4j.io.memory.ByteBuffers.allocate;
import static org.neo4j.io.memory.ByteBuffers.allocateDirect;
import static org.neo4j.io.memory.ByteBuffers.directBufferContainsNonZeroData;
import static org.neo4j.io.memory.ByteBuffers.releaseBuffer;
import static org.neo4j.memory.MemoryPools.NO_TRACKING;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.io.ByteUnit;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.memory.LocalMemoryTracker;

class ByteBuffersTest {
    @Test
    void trackMemoryAllocationsForNativeByteBuffers() {
        var memoryTracker = new LocalMemoryTracker(NO_TRACKING, 100, 0, null);
        var byteBuffer = allocateDirect(30, ByteOrder.LITTLE_ENDIAN, memoryTracker);
        try {
            assertThat(memoryTracker.estimatedHeapMemory()).isZero();
            assertThat(memoryTracker.usedNativeMemory()).isEqualTo(30);
        } finally {
            releaseBuffer(byteBuffer, memoryTracker);
        }

        assertThat(memoryTracker.estimatedHeapMemory()).isZero();
        assertThat(memoryTracker.usedNativeMemory()).isZero();
    }

    @Test
    void trackMemoryAllocationsForHeapByteBuffers() {
        var memoryTracker = new LocalMemoryTracker(NO_TRACKING, 100, 0, null);
        var byteBuffer = allocate(30, ByteOrder.LITTLE_ENDIAN, memoryTracker);
        try {
            assertThat(memoryTracker.estimatedHeapMemory()).isEqualTo(30);
            assertThat(memoryTracker.usedNativeMemory()).isZero();
        } finally {
            releaseBuffer(byteBuffer, memoryTracker);
        }

        assertThat(memoryTracker.estimatedHeapMemory()).isZero();
        assertThat(memoryTracker.usedNativeMemory()).isZero();
    }

    @Test
    void byteBufferMustThrowOutOfBoundsAfterRelease() {
        var tracker = new LocalMemoryTracker();
        ByteBuffer buffer = allocateDirect(Long.BYTES, ByteOrder.LITTLE_ENDIAN, tracker);
        buffer.get(0);
        ByteBuffers.releaseBuffer(buffer, tracker);
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> buffer.get(0));
    }

    @Test
    void heapByteBufferMustThrowOutOfBoundsAfterRelease() {
        var tracker = new LocalMemoryTracker();
        ByteBuffer buffer = allocateDirect(Long.BYTES, ByteOrder.LITTLE_ENDIAN, tracker);
        buffer.get(0);
        ByteBuffers.releaseBuffer(buffer, tracker);
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> buffer.get(0));
    }

    @Test
    void doubleFreeOfByteBufferIsOkay() {
        var tracker = new LocalMemoryTracker();
        ByteBuffer buffer = allocate(Long.BYTES, ByteOrder.LITTLE_ENDIAN, tracker);
        ByteBuffers.releaseBuffer(buffer, tracker);
        ByteBuffers.releaseBuffer(buffer, tracker); // This must not throw.
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> buffer.get(0)); // And this still throws.
    }

    @Test
    void doubleFreeOfHeapByteBufferIsOkay() {
        var tracker = new LocalMemoryTracker();
        ByteBuffer buffer = allocate(Long.BYTES, ByteOrder.LITTLE_ENDIAN, tracker);
        ByteBuffers.releaseBuffer(buffer, tracker);
        ByteBuffers.releaseBuffer(buffer, tracker); // This must not throw.
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> buffer.get(0)); // And this still throws.
    }

    @Test
    void directBufferAllZeros() {
        ByteBuffer buffer = ByteBuffers.allocateDirect(10, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            assertThat(directBufferContainsNonZeroData(buffer)).isFalse();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @Test
    void directBufferWithNonZeroData() {
        ByteBuffer buffer = ByteBuffers.allocateDirect(10, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            buffer.put(5, (byte) 42);
            buffer.rewind();
            assertThat(directBufferContainsNonZeroData(buffer)).isTrue();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @Test
    void directBufferNonZeroAtEnd() {
        ByteBuffer buffer = ByteBuffers.allocateDirect(10, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            buffer.put(9, (byte) -42);
            buffer.rewind();
            assertThat(directBufferContainsNonZeroData(buffer)).isTrue();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @Test
    void emptyDirectBufferDoesNotContainNonZeroData() {
        ByteBuffer buffer = ByteBuffers.allocateDirect(10, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            assertThat(directBufferContainsNonZeroData(buffer)).isFalse();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @Test
    void bigDirectBufferAllZeros() {
        ByteBuffer buffer = ByteBuffers.allocateDirect(
                (int) ByteUnit.kibiBytes(16), ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            assertThat(directBufferContainsNonZeroData(buffer)).isFalse();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @Test
    void bigDirectBufferNonZeroAtEnd() {
        ByteBuffer buffer = ByteBuffers.allocateDirect(
                (int) ByteUnit.kibiBytes(16) + 17, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            buffer.put(buffer.limit() - 4, (byte) -42);
            buffer.rewind();
            assertThat(directBufferContainsNonZeroData(buffer)).isTrue();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 255, 256, 257, 1023, 1024,
                1025, 2056, 12288, 12289, 16384, 16386
            })
    void variousBufferSizesNonZeroAtEnd(int size) {
        ByteBuffer buffer = ByteBuffers.allocateDirect(size, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            buffer.put(size - 1, (byte) 1);
            assertThat(directBufferContainsNonZeroData(buffer)).isTrue();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 255, 256, 257, 1023, 1024,
                1025, 2056, 12288, 12289, 16384, 16386
            })
    void variousBufferSizesAllZero(int size) {
        ByteBuffer buffer = ByteBuffers.allocateDirect(size, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE);
        try {
            assertThat(directBufferContainsNonZeroData(buffer)).isFalse();
        } finally {
            ByteBuffers.releaseBuffer(buffer, EmptyMemoryTracker.INSTANCE);
        }
    }
}

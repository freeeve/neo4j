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
package org.neo4j.internal.unsafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.neo4j.internal.unsafe.UnsafeUtil.allocateMemory;
import static org.neo4j.internal.unsafe.UnsafeUtil.arrayBaseOffset;
import static org.neo4j.internal.unsafe.UnsafeUtil.arrayIndexScale;
import static org.neo4j.internal.unsafe.UnsafeUtil.arrayOffset;
import static org.neo4j.internal.unsafe.UnsafeUtil.assertHasUnsafe;
import static org.neo4j.internal.unsafe.UnsafeUtil.checkAccess;
import static org.neo4j.internal.unsafe.UnsafeUtil.compareAndSwapLong;
import static org.neo4j.internal.unsafe.UnsafeUtil.free;
import static org.neo4j.internal.unsafe.UnsafeUtil.getAndSetLong;
import static org.neo4j.internal.unsafe.UnsafeUtil.getByte;
import static org.neo4j.internal.unsafe.UnsafeUtil.getFieldOffset;
import static org.neo4j.internal.unsafe.UnsafeUtil.getInt;
import static org.neo4j.internal.unsafe.UnsafeUtil.getLong;
import static org.neo4j.internal.unsafe.UnsafeUtil.getLongVolatile;
import static org.neo4j.internal.unsafe.UnsafeUtil.getShort;
import static org.neo4j.internal.unsafe.UnsafeUtil.newDirectByteBuffer;
import static org.neo4j.internal.unsafe.UnsafeUtil.pageSize;
import static org.neo4j.internal.unsafe.UnsafeUtil.putByte;
import static org.neo4j.internal.unsafe.UnsafeUtil.putInt;
import static org.neo4j.internal.unsafe.UnsafeUtil.putLong;
import static org.neo4j.internal.unsafe.UnsafeUtil.putLongVolatile;
import static org.neo4j.internal.unsafe.UnsafeUtil.putShort;
import static org.neo4j.internal.unsafe.UnsafeUtil.setMemory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.neo4j.memory.LocalMemoryTracker;

class UnsafeUtilTest {
    static class Obj {
        boolean aBoolean;
        byte aByte;
        short aShort;
        float aFloat;
        char aChar;
        int anInt;
        long aLong;
        double aDouble;
        Object object;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Obj obj = (Obj) o;
            return aBoolean == obj.aBoolean
                    && aByte == obj.aByte
                    && aShort == obj.aShort
                    && Float.compare(obj.aFloat, aFloat) == 0
                    && aChar == obj.aChar
                    && anInt == obj.anInt
                    && aLong == obj.aLong
                    && Double.compare(obj.aDouble, aDouble) == 0
                    && Objects.equals(object, obj.object);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aBoolean, aByte, aShort, aFloat, aChar, anInt, aLong, aDouble, object);
        }
    }

    @Test
    void mustHaveUnsafe() {
        assertHasUnsafe();
    }

    @Test
    void pageSizeIsPowerOfTwo() {
        assertThat(pageSize())
                .isIn(
                        1,
                        2,
                        4,
                        8,
                        16,
                        32,
                        64,
                        128,
                        256,
                        512,
                        1024,
                        2048,
                        4096,
                        8192,
                        16384,
                        32768,
                        65536,
                        131072,
                        262144,
                        524288,
                        1048576,
                        2097152,
                        4194304,
                        8388608,
                        16777216,
                        33554432,
                        67108864,
                        134217728,
                        268435456,
                        536870912,
                        1073741824);
    }

    @Test
    void mustSupportReadingFromAndWritingToFields() {
        Obj obj;

        long aByteOffset = getFieldOffset(Obj.class, "aByte");
        obj = new Obj();
        putByte(obj, aByteOffset, (byte) 1);
        assertThat(obj.aByte).isOne();
        assertThat(getByte(obj, aByteOffset)).isOne();
        obj.aByte = 0;
        assertThat(obj).isEqualTo(new Obj());
    }

    @Test
    void mustSupportReadingAndWritingOfPrimitivesToMemory() {
        int sizeInBytes = 8;
        var tracker = new LocalMemoryTracker();
        long address = allocateMemory(sizeInBytes, tracker);
        try {
            putByte(address, (byte) 1);
            assertThat(getByte(address)).isOne();
            setMemory(address, sizeInBytes, (byte) 0);
            assertThat(getByte(address)).isZero();

            putShort(address, (short) 1);
            assertThat(getShort(address)).isOne();
            setMemory(address, sizeInBytes, (byte) 0);
            assertThat(getShort(address)).isZero();

            putInt(address, 1);
            assertThat(getInt(address)).isOne();
            setMemory(address, sizeInBytes, (byte) 0);
            assertThat(getInt(address)).isZero();

            putLong(address, 1);
            assertThat(getLong(address)).isOne();
            setMemory(address, sizeInBytes, (byte) 0);
            assertThat(getLong(address)).isZero();

            putLongVolatile(address, 1);
            assertThat(getLongVolatile(address)).isOne();
            setMemory(address, sizeInBytes, (byte) 0);
            assertThat(getLongVolatile(address)).isZero();
        } finally {
            free(address, sizeInBytes, tracker);
        }
    }

    @Test
    void compareAndSwapLongField() {
        Obj obj = new Obj();
        long aLongOffset = getFieldOffset(Obj.class, "aLong");
        assertThat(compareAndSwapLong(obj, aLongOffset, 0, 5)).isTrue();
        assertThat(compareAndSwapLong(obj, aLongOffset, 0, 5)).isFalse();
        assertThat(compareAndSwapLong(obj, aLongOffset, 5, 0)).isTrue();
        assertThat(obj).isEqualTo(new Obj());
    }

    @Test
    void getAndSetLongField() {
        Obj obj = new Obj();
        long offset = getFieldOffset(Obj.class, "aLong");
        assertThat(getAndSetLong(obj, offset, 42L)).isZero();
        assertThat(getAndSetLong(obj, offset, -1)).isEqualTo(42L);
    }

    @Test
    void unsafeArrayElementAccess() {
        int len = 3;
        int scale;
        int base;

        byte[] bytes = new byte[len];
        scale = arrayIndexScale(bytes.getClass());
        base = arrayBaseOffset(bytes.getClass());
        putByte(bytes, arrayOffset(1, base, scale), (byte) -1);
        assertThat(bytes[0]).isZero();
        assertThat(bytes[1]).isEqualTo((byte) -1);
        assertThat(bytes[2]).isZero();
    }

    @Test
    void shouldPutAndGetByteWiseLittleEndianShort() {
        // GIVEN
        int sizeInBytes = 2;
        var tracker = new LocalMemoryTracker();
        long p = allocateMemory(sizeInBytes, tracker);
        short value = (short) 0b11001100_10101010;

        // WHEN
        UnsafeUtil.putShortByteWiseLittleEndian(p, value);
        short readValue = UnsafeUtil.getShortByteWiseLittleEndian(p);

        // THEN
        free(p, sizeInBytes, tracker);
        assertThat(readValue).isEqualTo(value);
    }

    @Test
    void shouldPutAndGetByteWiseLittleEndianInt() {
        // GIVEN
        int sizeInBytes = 4;
        var tracker = new LocalMemoryTracker();
        long p = allocateMemory(sizeInBytes, tracker);
        int value = 0b11001100_10101010_10011001_01100110;

        // WHEN
        UnsafeUtil.putIntByteWiseLittleEndian(p, value);
        int readValue = UnsafeUtil.getIntByteWiseLittleEndian(p);

        // THEN
        free(p, sizeInBytes, tracker);
        assertThat(readValue).isEqualTo(value);
    }

    @Test
    void shouldPutAndGetByteWiseLittleEndianLong() {
        // GIVEN
        int sizeInBytes = 8;
        var tracker = new LocalMemoryTracker();
        long p = allocateMemory(sizeInBytes, tracker);
        long value = 0b11001100_10101010_10011001_01100110__10001000_01000100_00100010_00010001L;

        // WHEN
        UnsafeUtil.putLongByteWiseLittleEndian(p, value);
        long readValue = UnsafeUtil.getLongByteWiseLittleEndian(p);

        // THEN
        free(p, sizeInBytes, tracker);
        assertThat(readValue).isEqualTo(value);
    }

    @Test
    void directByteBufferCreationAndInitialisation() throws Throwable {
        int sizeInBytes = 313;
        var tracker = new LocalMemoryTracker();
        long address = allocateMemory(sizeInBytes, tracker);
        try {
            setMemory(address, sizeInBytes, (byte) 0);
            ByteBuffer a = newDirectByteBuffer(address, sizeInBytes);
            assertThat(a).isNotSameAs(newDirectByteBuffer(address, sizeInBytes));
            assertThat(a.hasArray()).isFalse();
            assertThat(a.isDirect()).isTrue();
            assertThat(a.capacity()).isEqualTo(sizeInBytes);
            assertThat(a.limit()).isEqualTo(sizeInBytes);
            assertThat(a.position()).isZero();
            assertThat(a.remaining()).isEqualTo(sizeInBytes);
            assertThat(getByte(address)).isZero();
            a.put((byte) -1);
            assertThat(getByte(address)).isEqualTo((byte) -1);

            a.position(101);
            a.mark();
            a.limit(202);
        } finally {
            free(address, sizeInBytes, tracker);
        }
    }

    @Test
    void getAddressOfDirectByteBuffer() {
        ByteBuffer buf = ByteBuffer.allocateDirect(8);
        long address = UnsafeUtil.getDirectByteBufferAddress(buf);
        long expected = ThreadLocalRandom.current().nextLong();
        // Disable native access checking, because UnsafeUtil doesn't know about the memory allocation in the
        // ByteBuffer.allocateDirect( … ) call.
        boolean nativeAccessCheckEnabled = UnsafeUtil.exchangeNativeAccessCheckEnabled(false);
        try {
            UnsafeUtil.putLong(address, expected);
            long actual = buf.getLong();
            assertThat(actual).isIn(expected, Long.reverseBytes(expected));
        } finally {
            UnsafeUtil.exchangeNativeAccessCheckEnabled(nativeAccessCheckEnabled);
        }
    }

    @Test
    void closeNativeByteBufferWithUnsafe() {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        assertThatCode(() -> UnsafeUtil.invokeCleaner(directBuffer)).doesNotThrowAnyException();
    }

    @Test
    void detectBadMemoryAccess() {
        UnsafeUtil.addAllocatedPointer(0x7fc7f84a1000L, 220976);
        try {
            assertThatCode(() -> checkAccess(0x7fc7f84a1000L, 220976)).doesNotThrowAnyException();
            assertThatCode(() -> checkAccess(0x7fc7f84a0000L, 220976))
                    .hasMessageContaining("Bad access to address 0x7fc7f84a0000");
        } finally {
            UnsafeUtil.removeAllocatedPointer(0x7fc7f84a1000L);
        }
    }

    @Test
    void allowAdjacentAllocationAccesses() {
        allocateCheckAndAssertThat(() -> checkAccess(64, 128), 128, 0, 128).doesNotThrowAnyException();
        allocateCheckAndAssertThat(() -> checkAccess(20, 100), 32, 0, 32, 64, 96, 128)
                .doesNotThrowAnyException();
        allocateCheckAndAssertThat(() -> checkAccess(64, 40), 32, 0, 32, 64, 96, 128)
                .doesNotThrowAnyException();

        allocateCheckAndAssertThat(() -> checkAccess(20, 100), 32, 0, 32, 96, 128)
                .hasMessageContaining("Bad access to address 0x14 with size 100");
        allocateCheckAndAssertThat(() -> checkAccess(64, 40), 32, 0, 32, 96, 128)
                .hasMessageContaining("Bad access to address 0x40 with size 40");
        allocateCheckAndAssertThat(() -> checkAccess(32, 65), 32, 32, 64)
                .hasMessageContaining("'[0x20 - <access start(0x20)>  0x40][0x40 - 0x60] <access end(0x61)> '");
    }

    @Test
    void accessMap() {
        long[] pointers = new long[] {32, 96, 128};

        allocateCheckAndAssertThat(() -> checkAccess(10, 10), 32, pointers)
                .hasMessageContaining("' <access start(0xa)>  <access end(0x14)> [0x20 - 0x40]'");
        allocateCheckAndAssertThat(() -> checkAccess(10, 32), 32, pointers)
                .hasMessageContaining("' <access start(0xa)> [0x20 - <access end(0x2a)>  0x40]'");
        allocateCheckAndAssertThat(() -> checkAccess(10, 64), 32, pointers)
                .hasMessageContaining("' <access start(0xa)> [0x20 - 0x40] <access end(0x4a)> '");
        allocateCheckAndAssertThat(() -> checkAccess(10, 128), 32, pointers)
                .hasMessageContaining(
                        "' <access start(0xa)> [0x20 - 0x40] GAP! [0x60 - 0x80][0x80 - <access end(0x8a)>  0xa0]'");
        allocateCheckAndAssertThat(() -> checkAccess(32, 70), 32, pointers)
                .hasMessageContaining("'[0x20 - <access start(0x20)>  0x40] GAP! [0x60 - <access end(0x66)>  0x80]'");
        allocateCheckAndAssertThat(() -> checkAccess(128, 40), 32, pointers)
                .hasMessageContaining("'[0x80 - <access start(0x80)>  0xa0] <access end(0xa8)> '");
        allocateCheckAndAssertThat(() -> checkAccess(161, 32), 32, pointers)
                .hasMessageContaining("'[0x80 - 0xa0] <access start(0xa1)>  <access end(0xc1)> '");
    }

    private static AbstractThrowableAssert<?, ? extends Throwable> allocateCheckAndAssertThat(
            ThrowingCallable check, int size, long... pointers) {
        for (long pointer : pointers) {
            UnsafeUtil.addAllocatedPointer(pointer, size);
        }
        try {
            return assertThatCode(check);
        } finally {
            for (long pointer : pointers) {
                UnsafeUtil.removeAllocatedPointer(pointer);
            }
        }
    }
}

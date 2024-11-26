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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@ExtendWith(RandomExtension.class)
public class ByteArrayTest {
    private static final byte[] DEFAULT = new byte[50];
    private static final int LENGTH = 1_000;

    @Inject
    private TestDirectory testDirectory;

    @Inject
    private RandomSupport random;

    private ByteArray array;

    private static Stream<ByteArrayCreator> argumentsProvider() {
        int chunkSize = LENGTH / 10;
        return Stream.of(
                (fs, workDirectory) -> NumberArrayFactories.HEAP.newByteArray(LENGTH, DEFAULT, INSTANCE),
                (fs, workDirectory) -> NumberArrayFactories.HEAP.newDynamicByteArray(chunkSize, DEFAULT, INSTANCE),
                (fs, workDirectory) -> NumberArrayFactories.OFF_HEAP.newByteArray(LENGTH, DEFAULT, INSTANCE),
                (fs, workDirectory) -> NumberArrayFactories.OFF_HEAP.newDynamicByteArray(chunkSize, DEFAULT, INSTANCE),
                (fs, workDirectory) -> NumberArrayFactories.AUTO_WITHOUT_SWAP.newByteArray(LENGTH, DEFAULT, INSTANCE),
                (fs, workDirectory) ->
                        NumberArrayFactories.AUTO_WITHOUT_SWAP.newDynamicByteArray(chunkSize, DEFAULT, INSTANCE),
                (fs, workDirectory) -> NumberArrayFactories.fromBufferFactory(
                                new NumberArraysArgumentProvider.RandomBufferFactory(
                                        BufferFactories.HEAP,
                                        BufferFactories.OFF_HEAP,
                                        BufferFactories.fileBacked(fs, workDirectory)))
                        .newByteArray(LENGTH, DEFAULT, INSTANCE),
                (fs, workDirectory) -> NumberArrayFactories.fromBufferFactory(
                                new NumberArraysArgumentProvider.RandomBufferFactory(
                                        BufferFactories.HEAP,
                                        BufferFactories.OFF_HEAP,
                                        BufferFactories.fileBacked(fs, workDirectory)))
                        .newDynamicByteArray(chunkSize, DEFAULT, INSTANCE));
    }

    public interface ByteArrayCreator {
        ByteArray createByteArray(FileSystemAbstraction fs, Path workDirectory);
    }

    @AfterEach
    public void after() {
        array.close();
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldSetAndGetBasicTypes(ByteArrayCreator creator) {
        this.array = creator.createByteArray(testDirectory.getFileSystem(), testDirectory.homePath());

        byte[] actualBytes = new byte[DEFAULT.length];
        byte[] expectedBytes = new byte[actualBytes.length];
        random.nextBytes(actualBytes);

        int len = LENGTH - 1; // subtract one because we access TWO elements.
        for (int i = 0; i < len; i++) {
            try {
                // WHEN
                setSimpleValues(i);
                setArrayElement(i + 1, actualBytes);

                // THEN
                verifySimpleValues(i);
                verifyArrayElement(i + 1, actualBytes, expectedBytes);
            } catch (Throwable throwable) {
                throw new AssertionError("Failure at index " + i, throwable);
            }
        }
    }

    private void setSimpleValues(int index) {
        array.setByte(index, 0, (byte) 123);
        array.setShort(index, 1, (short) 1234);
        array.setInt(index, 5, 12345);
        array.setLong(index, 9, Long.MAX_VALUE - 100);
        array.set3ByteInt(index, 17, 0b01010101_01010101_01010101);
        array.set5ByteLong(index, 20, 0b01010101_01010101_01010101_01010101_01010101L);
        array.set6ByteLong(index, 25, 0b01010101_01010101_01010101_01010101_01010101_01010101L);

        array.set3ByteInt(index, 31, -2);
        array.set5ByteLong(index, 34, -3);
        array.set6ByteLong(index, 39, -4);
    }

    private void verifySimpleValues(int index) {
        assertEquals((byte) 123, array.getByte(index, 0));
        assertEquals((short) 1234, array.getShort(index, 1));
        assertEquals(12345, array.getInt(index, 5));
        assertEquals(Long.MAX_VALUE - 100, array.getLong(index, 9));
        assertEquals(0b01010101_01010101_01010101, array.get3ByteInt(index, 17));
        assertEquals(0b01010101_01010101_01010101_01010101_01010101L, array.get5ByteLong(index, 20));
        assertEquals(0b01010101_01010101_01010101_01010101_01010101_01010101L, array.get6ByteLong(index, 25));

        assertEquals(-2, array.get3ByteInt(index, 31));
        assertEquals(-3, array.get5ByteLong(index, 34));
        assertEquals(-4, array.get5ByteLong(index, 39));
    }

    private void setArrayElement(int index, byte[] bytes) {
        array.setElement(index, bytes);
    }

    private void verifyArrayElement(int index, byte[] actualBytes, byte[] scratchBuffer) {
        array.getElement(index, scratchBuffer);
        assertArrayEquals(actualBytes, scratchBuffer);
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldDetectMinusOneFor3ByteInts(ByteArrayCreator creator) {
        this.array = creator.createByteArray(testDirectory.getFileSystem(), testDirectory.homePath());

        // WHEN
        array.set3ByteInt(10, 2, -1);
        array.set3ByteInt(10, 5, -1);

        // THEN
        assertEquals(-1L, array.get3ByteInt(10, 2));
        assertEquals(-1L, array.get3ByteInt(10, 5));
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldDetectMinusOneFor5ByteLongs(ByteArrayCreator creator) {
        this.array = creator.createByteArray(testDirectory.getFileSystem(), testDirectory.homePath());

        // WHEN
        array.set5ByteLong(10, 2, -1);
        array.set5ByteLong(10, 7, -1);

        // THEN
        assertEquals(-1L, array.get5ByteLong(10, 2));
        assertEquals(-1L, array.get5ByteLong(10, 7));
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldDetectMinusOneFor6ByteLongs(ByteArrayCreator creator) {
        this.array = creator.createByteArray(testDirectory.getFileSystem(), testDirectory.homePath());

        // WHEN
        array.set6ByteLong(10, 2, -1);
        array.set6ByteLong(10, 8, -1);

        // THEN
        assertEquals(-1L, array.get6ByteLong(10, 2));
        assertEquals(-1L, array.get6ByteLong(10, 8));
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldHandleMultipleCallsToClose(ByteArrayCreator creator) {
        this.array = creator.createByteArray(testDirectory.getFileSystem(), testDirectory.homePath());

        // WHEN
        array.close();

        // THEN should also work
        array.close();
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void bulkSetAndGetLarge(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = factory.create(testDirectory.getFileSystem(), testDirectory.homePath());
        int chunkSize = random.nextInt(32, 1024);
        array = numberArrayFactory.newDynamicByteArray(chunkSize, new byte[1], INSTANCE);

        HashMap<Integer, String> map = new HashMap<>();
        int offset = 0;
        for (int i = 0; i < 10; i++) {
            String string = random.nextAlphaNumericString(8, 256);
            byte[] data = string.getBytes(StandardCharsets.UTF_8);
            array.set(offset, data);
            map.put(offset, string);
            offset += data.length;
        }

        map.forEach((o, s) -> {
            byte[] data = s.getBytes(StandardCharsets.UTF_8);
            byte[] into = new byte[data.length];
            array.get(o, into);
            assertArrayEquals(into, data);
        });
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void bulkSetAndGetSmall(NumberArraysArgumentProvider.Factory factory) {
        NumberArrayFactory numberArrayFactory = factory.create(testDirectory.getFileSystem(), testDirectory.homePath());
        int chunkSize = random.nextInt(4, 8);
        array = numberArrayFactory.newDynamicByteArray(chunkSize, new byte[1], INSTANCE);

        HashMap<Integer, String> map = new HashMap<>();
        int offset = 0;
        for (int i = 0; i < 256; i++) {
            String string = random.nextAlphaNumericString(2, 12);
            byte[] data = string.getBytes(StandardCharsets.UTF_8);
            array.set(offset, data);
            map.put(offset, string);
            offset += data.length;
        }

        map.forEach((o, s) -> {
            byte[] data = s.getBytes(StandardCharsets.UTF_8);
            byte[] into = new byte[data.length];
            array.get(o, into);
            assertArrayEquals(into, data);
        });
    }
}

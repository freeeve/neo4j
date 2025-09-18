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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.neo4j.internal.batchimport.cache.ArrayPermutationTesting.AUTO_NO_SWAP;
import static org.neo4j.internal.batchimport.cache.ArrayPermutationTesting.FILE_BACKED;
import static org.neo4j.internal.batchimport.cache.ArrayPermutationTesting.OFF_HEAP;
import static org.neo4j.internal.batchimport.cache.ArrayPermutationTesting.ON_HEAP;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.internal.batchimport.cache.ArrayPermutationTesting.ByteArrayCreator;
import org.neo4j.internal.batchimport.cache.ArrayPermutationTesting.NumberArrayFactoryCreator;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@RandomSupportExtension
public class ByteArrayTest {
    private static final byte[] DEFAULT = new byte[50];
    private static final int LENGTH = 1_000;
    private static final int CHUNK_SIZE = LENGTH / 10;

    private static final ByteArrayCreator FIXED_LENGTH = n -> n.newByteArray(LENGTH, DEFAULT, INSTANCE);
    private static final ByteArrayCreator DYNAMIC_LENGTH = n -> n.newDynamicByteArray(CHUNK_SIZE, DEFAULT, INSTANCE);

    @Inject
    private TestDirectory testDirectory;

    @Inject
    private RandomSupport random;

    private static Stream<Arguments> argumentsProvider() {
        return Stream.of(
                argumentSet("HEAP fixed length", ON_HEAP, FIXED_LENGTH),
                argumentSet("HEAP dynamic length", ON_HEAP, DYNAMIC_LENGTH),
                argumentSet("OFF_HEAP fixed length", OFF_HEAP, FIXED_LENGTH),
                argumentSet("OFF_HEAP dynamic length", OFF_HEAP, DYNAMIC_LENGTH),
                argumentSet("AUTO_NO_SWAP fixed length", AUTO_NO_SWAP, FIXED_LENGTH),
                argumentSet("AUTO_NO_SWAP dynamic length", AUTO_NO_SWAP, DYNAMIC_LENGTH),
                argumentSet("FILE_BACKED fixed length", FILE_BACKED, FIXED_LENGTH),
                argumentSet("FILE_BACKED dynamic length", FILE_BACKED, DYNAMIC_LENGTH));
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldSetAndGetBasicTypes(NumberArrayFactoryCreator factoryCreator, ByteArrayCreator arrayCreator) {
        try (NumberArrayFactory arrayFactory = getArrayFactory(factoryCreator);
                ByteArray array = arrayCreator.createByteArray(arrayFactory)) {

            byte[] actualBytes = new byte[DEFAULT.length];
            byte[] expectedBytes = new byte[actualBytes.length];
            random.nextBytes(actualBytes);

            int len = LENGTH - 1; // subtract one because we access TWO elements.
            for (int i = 0; i < len; i++) {
                try {
                    // WHEN
                    setSimpleValues(array, i);
                    setArrayElement(array, i + 1, actualBytes);

                    // THEN
                    verifySimpleValues(array, i);
                    verifyArrayElement(array, i + 1, actualBytes, expectedBytes);
                } catch (Throwable throwable) {
                    throw new AssertionError("Failure at index " + i, throwable);
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldDetectMinusOneFor3ByteInts(
            NumberArrayFactoryCreator factoryCreator, ByteArrayCreator arrayCreator) {
        try (NumberArrayFactory arrayFactory = getArrayFactory(factoryCreator);
                ByteArray array = arrayCreator.createByteArray(arrayFactory)) {

            // WHEN
            array.set3ByteInt(10, 2, -1);
            array.set3ByteInt(10, 5, -1);

            // THEN
            assertEquals(-1L, array.get3ByteInt(10, 2));
            assertEquals(-1L, array.get3ByteInt(10, 5));
        }
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldDetectMinusOneFor5ByteLongs(
            NumberArrayFactoryCreator factoryCreator, ByteArrayCreator arrayCreator) {
        try (NumberArrayFactory arrayFactory = getArrayFactory(factoryCreator);
                ByteArray array = arrayCreator.createByteArray(arrayFactory)) {

            // WHEN
            array.set5ByteLong(10, 2, -1);
            array.set5ByteLong(10, 7, -1);

            // THEN
            assertEquals(-1L, array.get5ByteLong(10, 2));
            assertEquals(-1L, array.get5ByteLong(10, 7));
        }
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldDetectMinusOneFor6ByteLongs(
            NumberArrayFactoryCreator factoryCreator, ByteArrayCreator arrayCreator) {
        try (NumberArrayFactory arrayFactory = getArrayFactory(factoryCreator);
                ByteArray array = arrayCreator.createByteArray(arrayFactory)) {

            // WHEN
            array.set6ByteLong(10, 2, -1);
            array.set6ByteLong(10, 8, -1);

            // THEN
            assertEquals(-1L, array.get6ByteLong(10, 2));
            assertEquals(-1L, array.get6ByteLong(10, 8));
        }
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void shouldHandleMultipleCallsToClose(
            NumberArrayFactoryCreator factoryCreator, ByteArrayCreator arrayCreator) {
        try (NumberArrayFactory arrayFactory = getArrayFactory(factoryCreator)) {
            ByteArray array = arrayCreator.createByteArray(arrayFactory);

            // WHEN
            array.close();

            // THEN should also work
            array.close();
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void bulkSetAndGetLarge(NumberArraysArgumentProvider.Factory factory) {
        int chunkSize = random.nextInt(32, 1024);
        try (NumberArrayFactory arrayFactory = factory.create(testDirectory.getFileSystem(), testDirectory.homePath());
                ByteArray array = arrayFactory.newDynamicByteArray(chunkSize, new byte[1], INSTANCE)) {

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
    }

    @ParameterizedTest
    @ArgumentsSource(NumberArraysArgumentProvider.class)
    void bulkSetAndGetSmall(NumberArraysArgumentProvider.Factory factory) {
        int chunkSize = random.nextInt(4, 8);
        try (NumberArrayFactory arrayFactory = factory.create(testDirectory.getFileSystem(), testDirectory.homePath());
                ByteArray array = arrayFactory.newDynamicByteArray(chunkSize, new byte[1], INSTANCE)) {

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

    @Test
    void capChunkSize() {
        try (NumberArrayFactory factory = NumberArrayFactories.OFF_HEAP) {
            assertThatCode(() -> factory.newDynamicByteArray(Integer.MAX_VALUE, new byte[2], INSTANCE))
                    .doesNotThrowAnyException();
        }
    }

    private NumberArrayFactory getArrayFactory(NumberArrayFactoryCreator creator) {
        return creator.createNumberArrayFactory(testDirectory.getFileSystem(), testDirectory.homePath());
    }

    private static void setSimpleValues(ByteArray array, int index) {
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

    private static void verifySimpleValues(ByteArray array, int index) {
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

    private static void setArrayElement(ByteArray array, int index, byte[] bytes) {
        array.setElement(index, bytes);
    }

    private static void verifyArrayElement(ByteArray array, int index, byte[] actualBytes, byte[] scratchBuffer) {
        array.getElement(index, scratchBuffer);
        assertArrayEquals(actualBytes, scratchBuffer);
    }
}

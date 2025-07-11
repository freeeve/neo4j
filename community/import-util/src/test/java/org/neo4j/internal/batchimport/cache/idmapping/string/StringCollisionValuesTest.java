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
package org.neo4j.internal.batchimport.cache.idmapping.string;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.internal.batchimport.cache.BufferFactories.fileBacked;
import static org.neo4j.io.pagecache.PageCache.PAGE_SIZE;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.internal.batchimport.cache.NumberArrayFactories;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;
import org.neo4j.values.storable.RandomValues;

@TestDirectoryExtension
@ExtendWith(RandomExtension.class)
class StringCollisionValuesTest {
    @Inject
    private RandomSupport random;

    @Inject
    private TestDirectory testDirectory;

    @BeforeEach
    void before() {
        random.withConfiguration(RandomValues.newConfigurationBuilder()
                        .stringMaxLength(RandomValues.MAX_BMP_CODE_POINT)
                        .build())
                .reset();
    }

    private static Stream<BiFunction<FileSystemAbstraction, Path, NumberArrayFactory>> data() {
        return Stream.of(
                (FileSystemAbstraction fs, Path homePath) -> NumberArrayFactories.OFF_HEAP,
                (FileSystemAbstraction fs, Path homePath) -> NumberArrayFactories.OFF_HEAP,
                (FileSystemAbstraction fs, Path homePath) -> NumberArrayFactories.AUTO_WITHOUT_SWAP,
                (FileSystemAbstraction fs, Path homePath) ->
                        NumberArrayFactories.fromBufferFactory(fileBacked(fs, homePath)));
    }

    @ParameterizedTest
    @MethodSource("data")
    void shouldStoreAndLoadStrings(BiFunction<FileSystemAbstraction, Path, NumberArrayFactory> factory) {
        // given
        try (NumberArrayFactory arrayFactory = factory.apply(testDirectory.getFileSystem(), testDirectory.homePath());
                StringCollisionValues values = new StringCollisionValues(arrayFactory, 10_000, INSTANCE)) {
            // when
            long[] offsets = new long[100];
            String[] strings = new String[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
                String string = random.nextAlphaNumericString();
                offsets[i] = values.add(string);
                strings[i] = string;
            }

            // then
            for (int i = 0; i < offsets.length; i++) {
                assertEquals(strings[i], values.get(offsets[i]));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    void shouldMoveOverToNextChunkOnNearEnd(BiFunction<FileSystemAbstraction, Path, NumberArrayFactory> factory) {
        // given
        try (NumberArrayFactory arrayFactory = factory.apply(testDirectory.getFileSystem(), testDirectory.homePath());
                StringCollisionValues values = new StringCollisionValues(arrayFactory, 10_000, INSTANCE)) {
            char[] chars = new char[PAGE_SIZE - 3];
            Arrays.fill(chars, 'a');

            // when
            String string = String.valueOf(chars);
            long offset = values.add(string);
            String secondString = "abcdef";
            long secondOffset = values.add(secondString);

            // then
            String readString = (String) values.get(offset);
            assertEquals(string, readString);
            String readSecondString = (String) values.get(secondOffset);
            assertEquals(secondString, readSecondString);
        }
    }
}

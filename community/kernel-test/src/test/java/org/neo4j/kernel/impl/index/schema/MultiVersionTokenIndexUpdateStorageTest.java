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
package org.neo4j.kernel.impl.index.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import io.netty.util.internal.EmptyArrays;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@org.neo4j.test.extension.RandomSupportExtension
class MultiVersionTokenIndexUpdateStorageTest {

    @Inject
    private TestDirectory directory;

    @Inject
    private RandomSupport random;

    private final MemoryTracker memoryTracker = INSTANCE;

    @Test
    void shouldHandleZeroEntries() throws IOException {
        FileSystemAbstraction fs = directory.getFileSystem();
        var file = directory.file("token-updates");
        try (var storage = new MultiVersionTokenIndexUpdateStorage(fs, file, memoryTracker)) {
            // no updates written
            try (var reader = storage.reader()) {
                assertFalse(reader.next());
            }
        }
    }

    @Test
    void shouldWriteAndReadFewEntries() throws IOException {
        FileSystemAbstraction fs = directory.getFileSystem();
        var file = directory.file("token-updates-few");
        var expected = generateUpdates(5, 3);
        try (var storage = new MultiVersionTokenIndexUpdateStorage(fs, file, memoryTracker)) {
            storeAll(storage, expected);
            try (var reader = storage.reader()) {
                verify(expected, reader);
            }
        }
    }

    @Test
    void shouldWriteAndReadManyEntries() throws IOException {
        FileSystemAbstraction fs = directory.getFileSystem();
        var file = directory.file("token-updates-many");
        var expected = generateUpdates(1000, 50);
        try (var storage = new MultiVersionTokenIndexUpdateStorage(fs, file, memoryTracker)) {
            storeAll(storage, expected);
            try (var reader = storage.reader()) {
                verify(expected, reader);
            }
        }
    }

    @Test
    void shouldWriteMegaEntries() throws IOException {
        FileSystemAbstraction fs = directory.getFileSystem();
        var file = directory.file("token-updates-mega");
        var updates = new ArrayList<TokenUpdate>(50);
        for (int i = 0; i < 50; i++) {
            long version = random.nextLong(Long.MAX_VALUE);
            long entityId = random.nextLong(Long.MAX_VALUE);
            int[] addedArray = new int[50_000];
            int[] removed = EmptyArrays.EMPTY_INTS;
            for (int added = 0; added < addedArray.length; added++) {
                addedArray[added] = random.nextInt(Integer.MAX_VALUE);
            }
            updates.add(new TokenUpdate(entityId, addedArray, removed, version));
        }
        var expected = (List<TokenUpdate>) updates;
        try (var storage = new MultiVersionTokenIndexUpdateStorage(fs, file, memoryTracker)) {
            storeAll(storage, expected);
            try (var reader = storage.reader()) {
                verify(expected, reader);
            }
        }
    }

    private static void storeAll(MultiVersionTokenIndexUpdateStorage storage, List<TokenUpdate> updates)
            throws IOException {
        for (var u : updates) {
            storage.add(u.entityId, u.added, u.removed, u.version);
        }
    }

    private static void verify(List<TokenUpdate> expected, MultiVersionTokenIndexUpdateStorage.Reader reader)
            throws IOException {
        for (var u : expected) {
            assertTrue(reader.next());
            Assertions.assertThat(reader.version).isEqualTo(u.version);
            Assertions.assertThat(reader.entityId).isEqualTo(u.entityId);
            Assertions.assertThat(reader.added).containsExactly(u.added);
            Assertions.assertThat(reader.removed).containsExactly(u.removed);
        }
        assertFalse(reader.next());
    }

    private List<TokenUpdate> generateUpdates(int count, int maxArraySize) {
        var updates = new ArrayList<TokenUpdate>(count);
        for (int i = 0; i < count; i++) {
            long version = random.nextLong(Long.MAX_VALUE);
            long entityId = random.nextLong(Long.MAX_VALUE);
            int[] added = randomIntArray(maxArraySize);
            int[] removed = randomIntArray(maxArraySize);
            updates.add(new TokenUpdate(entityId, added, removed, version));
        }
        return updates;
    }

    private int[] randomIntArray(int maxArraySize) {
        int size = random.nextInt(maxArraySize + 1);
        int[] a = new int[size];
        for (int i = 0; i < size; i++) {
            a[i] = random.nextInt(Integer.MAX_VALUE);
        }
        return a;
    }

    private record TokenUpdate(long entityId, int[] added, int[] removed, long version) {}
}

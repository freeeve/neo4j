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

import static org.junit.jupiter.api.Named.named;
import static org.neo4j.internal.unsafe.UnsafeUtil.getDirectByteBufferAddress;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.neo4j.internal.unsafe.UnsafeUtil;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.memory.MemoryTracker;

public class NumberArraysArgumentProvider implements ArgumentsProvider {

    @FunctionalInterface
    public interface Factory {
        NumberArrayFactory create(FileSystemAbstraction fs, Path workingDirectory);
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(
                        named("OFF_HEAP", (Factory) (fs, p) -> NumberArrayFactories.OFF_HEAP),
                        named("SWAP", (Factory) NumberArraysArgumentProvider::swap),
                        named("RANDOM", (Factory) NumberArraysArgumentProvider::random))
                .map(Arguments::of);
    }

    private static NumberArrayFactory swap(FileSystemAbstraction fs, Path workingDirectory) {
        return NumberArrayFactories.fromBufferFactory(BufferFactories.fileBacked(fs, workingDirectory));
    }

    private static NumberArrayFactory random(FileSystemAbstraction fs, Path workingDirectory) {
        RandomBufferFactory randomBufferFactory =
                new RandomBufferFactory(BufferFactories.OFF_HEAP, BufferFactories.fileBacked(fs, workingDirectory));
        return NumberArrayFactories.fromBufferFactory(randomBufferFactory);
    }

    static class RandomBufferFactory implements BufferFactory {
        private final BufferFactory[] bufferFactories;

        RandomBufferFactory(BufferFactory... bufferFactories) {
            this.bufferFactories = bufferFactories;
        }

        @Override
        public AllocatedBuffer allocate(int size, MemoryTracker memoryTracker) {
            int i = ThreadLocalRandom.current().nextInt(bufferFactories.length);
            return bufferFactories[i].allocate(size, memoryTracker);
        }

        @Override
        public void clear(ByteBuffer buffer, byte defaultValue) {
            if (buffer.hasArray()) {
                Arrays.fill(buffer.array(), defaultValue);
            } else {
                UnsafeUtil.setMemory(getDirectByteBufferAddress(buffer), buffer.capacity(), defaultValue);
            }
        }

        @Override
        public void close() throws Exception {
            IOUtils.closeAllUnchecked(bufferFactories);
        }
    }
}

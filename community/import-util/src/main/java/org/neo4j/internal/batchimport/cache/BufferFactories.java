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

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.neo4j.internal.unsafe.UnsafeUtil.getDirectByteBufferAddress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.neo4j.internal.unsafe.UnsafeUtil;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.memory.NativeScopedBuffer;
import org.neo4j.memory.MemoryTracker;

public final class BufferFactories {
    public static final BufferFactory OFF_HEAP = new BufferFactory() {
        @Override
        public AllocatedBuffer allocate(int size, MemoryTracker memoryTracker) {
            NativeScopedBuffer nativeScopedBuffer = new NativeScopedBuffer(size, LITTLE_ENDIAN, memoryTracker);
            return new AllocatedBuffer(nativeScopedBuffer.getBuffer(), nativeScopedBuffer);
        }

        @Override
        public void clear(ByteBuffer buffer, byte defaultValue) {
            UnsafeUtil.setMemory(getDirectByteBufferAddress(buffer), buffer.capacity(), defaultValue);
        }

        @Override
        public void close() {}

        @Override
        public String toString() {
            return "OffHeapBufferFactory";
        }
    };

    public static BufferFactory fileBacked(FileSystemAbstraction fs, Path workingDirectory) {
        try {
            return new SwappingBufferFactory(fs, workingDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferFactories() {}
}

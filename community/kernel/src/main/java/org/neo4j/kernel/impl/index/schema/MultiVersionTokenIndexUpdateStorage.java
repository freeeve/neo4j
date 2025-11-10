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

import static org.neo4j.io.IOUtils.closeAllUnchecked;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.ReadAheadChannel;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.memory.NativeScopedBuffer;
import org.neo4j.io.memory.ScopedBuffer;
import org.neo4j.memory.MemoryTracker;

/**
 * Buffer token index updates by writing them out to a file. Can be read back in insert order through
 * {@link #reader()}.
 *
 * Layout in file looks like this:
 * [vvvv vvvv][eeee eeee][aaaa rrrr][xxxx]...[xxxx][yyyy]...[yyyy] -> 8 + 8 + 4 + 4 + a*4 + r*4
 * v -> version
 * e -> entity id
 * a -> added size
 * r -> removed size
 */
public class MultiVersionTokenIndexUpdateStorage implements Closeable {
    private final FileSystemAbstraction fs;
    private final Path file;
    private ScopedBuffer scopedBuffer;
    private final MemoryTracker memoryTracker;
    private ByteBuffer buffer;
    private StoreChannel storeChannel;
    private long entryCount;
    private volatile boolean allocated;

    MultiVersionTokenIndexUpdateStorage(FileSystemAbstraction fs, Path file, MemoryTracker memoryTracker) {
        this.fs = fs;
        this.file = file;
        this.memoryTracker = memoryTracker;
    }

    void add(long entityId, int[] added, int[] removed, long version) throws IOException {
        allocateResources();
        putLong(version);
        putLong(entityId);
        putInt(added.length);
        putInt(removed.length);

        for (int addedId : added) {
            putInt(addedId);
        }
        for (int removedId : removed) {
            putInt(removedId);
        }
        entryCount++;
    }

    private void putLong(long value) throws IOException {
        ensureCapacity(Long.BYTES);
        buffer.putLong(value);
    }

    private void putInt(int value) throws IOException {
        ensureCapacity(Integer.BYTES);
        buffer.putInt(value);
    }

    private void ensureCapacity(int bytes) throws IOException {
        if (buffer.remaining() < bytes) {
            buffer.flip();
            if (buffer.hasRemaining()) {
                storeChannel.writeAll(buffer);
            }
            buffer.clear();
        }
    }

    Reader reader() throws IOException {
        if (allocated) {
            flush();
        }
        return new Reader(entryCount);
    }

    @Override
    public void close() throws IOException {
        if (allocated) {
            closeAllUnchecked(storeChannel, scopedBuffer, this::deleteFile, () -> allocated = false);
        } else {
            if (fs.fileExists(file)) {
                fs.deleteFile(file);
            }
        }
    }

    private void allocateResources() throws IOException {
        if (!allocated) {
            this.storeChannel = fs.write(file);
            this.scopedBuffer = new NativeScopedBuffer(ByteUnit.kibiBytes(8), ByteOrder.LITTLE_ENDIAN, memoryTracker);
            this.buffer = scopedBuffer.getBuffer();
            this.allocated = true;
        }
    }

    private void deleteFile() {
        try {
            fs.deleteFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void flush() throws IOException {
        if (allocated) {
            buffer.flip();
            storeChannel.writeAll(buffer);
            buffer.clear();
        }
    }

    class Reader implements Closeable {
        private ReadAheadChannel<StoreChannel> channel = null;
        private long entriesLeft;
        long version;
        long entityId;
        int[] added;
        int[] removed;

        Reader(long entryCount) throws IOException {
            if (entryCount > 0) {
                channel = new ReadAheadChannel<>(
                        fs.read(file),
                        new NativeScopedBuffer(ByteUnit.kibiBytes(8), ByteOrder.LITTLE_ENDIAN, memoryTracker));
            }
            entriesLeft = entryCount;
        }

        boolean next() throws IOException {
            if (entriesLeft <= 0) {
                return false;
            }
            version = channel.getLong();
            entityId = channel.getLong();
            int addedSize = channel.getInt();
            int removedSize = channel.getInt();
            added = new int[addedSize];
            removed = new int[removedSize];

            for (int i = 0; i < addedSize; i++) {
                added[i] = channel.getInt();
            }
            for (int i = 0; i < removedSize; i++) {
                removed[i] = channel.getInt();
            }

            entriesLeft--;
            return true;
        }

        @Override
        public void close() throws IOException {
            if (channel != null) {
                channel.close();
            }
        }
    }
}

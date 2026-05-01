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
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.ReadAheadChannel;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.memory.ByteBufferFactory;
import org.neo4j.io.memory.ScopedBuffer;
import org.neo4j.io.pagecache.ByteArrayPageCursor;
import org.neo4j.memory.MemoryTracker;

/**
 * Buffer index update instructions by writing them out to a file. Can be read back in insert order through {@link #reader()}.
 */
public class IndexUpdateStorage<KEY extends NativeIndexKey<KEY>> implements Closeable {

    static final byte STOP_TYPE = -1;

    private static final int TYPE_SIZE = Byte.BYTES;
    private static final int VERSION_SIZE = Long.BYTES;
    private static final byte[] NO_ENTRIES = {STOP_TYPE};

    private final Path file;
    private final FileSystemAbstraction fs;
    private final int blockSize;
    private final MemoryTracker memoryTracker;
    private final ByteBufferFactory.Allocator byteBufferFactory;
    private final IndexLayout<KEY> layout;

    private volatile boolean allocated;
    private ScopedBuffer scopedBuffer;
    private ByteBuffer buffer;
    private ByteArrayPageCursor pageCursor;
    private StoreChannel storeChannel;

    private final AtomicLong count = new AtomicLong();

    IndexUpdateStorage(
            FileSystemAbstraction fs,
            Path file,
            ByteBufferFactory.Allocator byteBufferFactory,
            int blockSize,
            IndexLayout<KEY> layout,
            MemoryTracker memoryTracker) {
        this.fs = fs;
        this.file = file;
        this.byteBufferFactory = byteBufferFactory;
        this.blockSize = blockSize;
        this.memoryTracker = memoryTracker;

        this.layout = layout;
    }

    void add(boolean addition, KEY key, long version) throws IOException {
        allocateResources();
        ensureCapacity(entrySize(key));
        pageCursor.putByte((byte) (addition ? 1 : 0));
        pageCursor.putLong(version);
        BlockEntry.write(pageCursor, layout, key);
        count.incrementAndGet();
    }

    IndexUpdateCursor<KEY> reader() throws IOException {
        if (!allocated) {
            return new IndexUpdateCursor<>(ByteArrayPageCursor.wrap(NO_ENTRIES), layout);
        }

        ReadAheadChannel<StoreChannel> channel =
                new ReadAheadChannel<>(fs.read(file), byteBufferFactory.allocate(blockSize, memoryTracker));
        return new IndexUpdateCursor<>(new ReadableChannelPageCursor(channel), layout);
    }

    long count() {
        return count.get();
    }

    void doneAdding() throws IOException {
        if (!allocated) {
            return;
        }
        ensureCapacity(TYPE_SIZE);
        pageCursor.putByte(STOP_TYPE);
        flush();
    }

    @Override
    public void close() throws IOException {
        if (allocated) {
            closeAllUnchecked(pageCursor, storeChannel, scopedBuffer, this::deleteFile, () -> allocated = false);
        } else {
            if (fs.fileExists(file)) {
                fs.deleteFile(file);
            }
        }
    }

    private int entrySize(KEY key) {
        return TYPE_SIZE + VERSION_SIZE + BlockEntry.keySize(layout, key);
    }

    private void ensureCapacity(int entrySize) throws IOException {
        assert entrySize <= buffer.limit()
                : "Expected entry to fit in buffer (entry=" + entrySize + " bytes, buffer=" + buffer.limit()
                        + " bytes)";
        if (entrySize > buffer.remaining()) {
            flush();
        }
    }

    private void flush() throws IOException {
        buffer.flip();
        storeChannel.writeAll(buffer);
        buffer.clear();
    }

    private void allocateResources() throws IOException {
        if (!allocated) {
            this.scopedBuffer = byteBufferFactory.allocate(blockSize, memoryTracker);
            this.buffer = scopedBuffer.getBuffer();
            this.pageCursor = new ByteArrayPageCursor(buffer);
            this.storeChannel = fs.write(file);
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
}

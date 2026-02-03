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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.neo4j.internal.helpers.MathUtil.roundUp;
import static org.neo4j.internal.unsafe.UnsafeUtil.getDirectByteBufferAddress;
import static org.neo4j.internal.unsafe.UnsafeUtil.setMemory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.SystemUtils;
import org.neo4j.internal.unsafe.UnsafeUtil;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.logging.InternalLog;
import org.neo4j.memory.MemoryTracker;

/**
 * A {@link BufferFactory} that uses memory mapped files as swap memory. This should typically only be used
 * in fallback scenarios when running low and memory since the swapping will incur a potentially massive
 * performance penalty.
 */
class SwappingBufferFactory implements BufferFactory, AutoCloseable {
    private static final Set<OpenOption> OPEN_OPTIONS = Set.of(CREATE, READ, WRITE, DELETE_ON_CLOSE);

    private final AtomicLong currentEnd = new AtomicLong();
    private final StoreChannel channel;
    private final FileSystemAbstraction fs;
    private final Path file;
    private static final boolean FORCE_UNMAP;

    static {
        FORCE_UNMAP = UnsafeUtil.unsafeByteBufferAccessAvailable() && SystemUtils.IS_OS_WINDOWS;
    }

    SwappingBufferFactory(FileSystemAbstraction fs, Path workDirectory) throws IOException {
        this.fs = fs;
        this.file = fs.createTempFile(workDirectory, ".swap", FileSystemAbstraction.DEFAULT_TMP_SUFFIX);
        this.channel = fs.open(file, OPEN_OPTIONS);
    }

    @Override
    public void close() {
        try {
            channel.close();
            deleteQuietly();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteQuietly() {
        try {
            fs.deleteFile(file);
        } catch (IOException e) {
            // ignored
        }
    }

    @Override
    public AllocatedBuffer allocate(int size, MemoryTracker memoryTracker) {
        try {
            long start = currentEnd.getAndAdd(roundUp(size, Long.BYTES));
            final MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_WRITE, start, size);

            AutoCloseable closeable = FORCE_UNMAP ? (() -> UnsafeUtil.invokeCleaner(mapped)) : null;
            if (UnsafeUtil.isCheckNativeAccessEnabled()) {
                // We need to track it in UnsafeUtil, otherwise all the access checks will fail
                long address = getDirectByteBufferAddress(mapped);
                UnsafeUtil.addAllocatedPointer(address, size);
                closeable = () -> {
                    if (FORCE_UNMAP) {
                        UnsafeUtil.invokeCleaner(mapped);
                    }
                    UnsafeUtil.removeAllocatedPointer(address);
                };
            }

            return new AllocatedBuffer(mapped, closeable);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear(ByteBuffer buffer, byte defaultValue) {
        setMemory(getDirectByteBufferAddress(buffer), buffer.capacity(), defaultValue);
    }

    @Override
    public String toString() {
        return "SwappingBufferFactory";
    }

    @Override
    public void warnForUsage(InternalLog log) {
        log.warn("Running low on memory and will start swapping to hard drive. "
                + "This will have a significant impact on performance.");
    }
}

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
package org.neo4j.cloud.storage.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.neo4j.cloud.storage.StorageSystemProvider;
import org.neo4j.io.memory.ByteBuffers;
import org.neo4j.memory.MemoryTracker;

public abstract class WriteableChannel extends OutputStream implements WritableByteChannel, IoErrorTracker {

    private static final int SPINS_BEFORE_SLEEP = 100000;
    private static final int MAX_SLEEP_BEFORE_SPIN = 500;

    private final AtomicReference<IOException> ioError = new AtomicReference<>();

    private final AtomicLong inflightWrites = new AtomicLong();

    protected final MemoryTracker memoryTracker;

    protected final ByteBuffer buffer;

    private final int maxInFlightRequests;

    protected long writtenChunks = 0;

    private boolean closed;

    protected WriteableChannel(int bufferSize, int maxInFlightRequests, MemoryTracker memoryTracker) {
        this.buffer = ByteBuffers.allocateDirect(bufferSize, ByteOrder.LITTLE_ENDIAN, memoryTracker);
        this.maxInFlightRequests = maxInFlightRequests;
        this.memoryTracker = memoryTracker;
    }

    protected abstract long internalGetSize();

    protected abstract void completeWriteProcess() throws IOException;

    /**
     * @param buffer the buffer to write to the underlying storage system. The aim is to return immediately from this
     *               call, so if the write process is asynchronous, the implementation should take a copy of this buffer
     *               to allow this channel to continue to populate its buffer with more updates.
     * @param completionHandler handler to call when the write process completes. <strong>IMPORTANT</strong> this
     *                          <i>must always</i> be called, even in the result of a failure.
     * @throws IOException if unable to write to the underlying storage
     */
    protected abstract void doBufferWrite(ByteBuffer buffer, Runnable completionHandler) throws IOException;

    protected abstract void reportChunksWritten(long chunks);

    protected abstract boolean hasBeenReplicated();

    public long size() throws IOException {
        ensureOpen();
        return internalGetSize();
    }

    public long transferFrom(Path path) throws IOException {
        var transferred = 0L;
        var read = 0;
        try (var fileChannel = toChannel(path)) {
            while ((read = fileChannel.read(buffer)) >= 0) {
                checkBufferIsFull();
                transferred += read;
            }

            // any remaining content in the buffer will be written in completeWriteProcess (via close)
        }

        return transferred;
    }

    private ReadableByteChannel toChannel(Path path) throws IOException {
        if (path.getFileSystem().provider() instanceof StorageSystemProvider) {
            // FileChannel.open is not supported so need to use intermediate byte buffer on input stream
            return Channels.newChannel(Files.newInputStream(path));
        } else {
            return FileChannel.open(path, StandardOpenOption.READ);
        }
    }

    @Override
    public void write(int b) throws IOException {
        ensureOk();
        checkBufferIsFull();
        buffer.put((byte) b);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        ensureOk();

        var pos = 0;
        while (pos < length) {
            checkBufferIsFull();
            final var toWrite = Math.min(length - pos, buffer.remaining());
            buffer.put(bytes, offset + pos, toWrite);
            pos += toWrite;
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOk();

        var written = 0;
        while (src.hasRemaining()) {
            checkBufferIsFull();

            final var toWrite = Math.min(src.remaining(), buffer.remaining());
            final var thisPos = buffer.position();
            final var srcPos = src.position();
            buffer.put(thisPos, src, srcPos, toWrite).position(thisPos + toWrite);

            written += toWrite;
            src.position(srcPos + toWrite);
        }

        return written;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void ensureNoErrors() throws IOException {
        final var error = ioError.get();
        if (error != null) {
            throw error;
        }
    }

    @Override
    public IOException updateErrors(IOException ex) {
        return ioError.updateAndGet(prev -> {
            if (prev == null) {
                return ex;
            } else {
                prev.addSuppressed(ex);
                return prev;
            }
        });
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        try {
            completeWriteProcess();
        } finally {
            ByteBuffers.releaseBuffer(buffer, memoryTracker);
        }
    }

    protected void ensureOpen() throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
    }

    private void ensureOk() throws IOException {
        ensureOpen();
        if (hasBeenReplicated()) {
            throw new IOException("Cannot write to a channel that has been used in a copyFrom");
        }

        ensureNoErrors();
    }

    private void checkBufferIsFull() throws IOException {
        if (!buffer.hasRemaining()) {
            checkInflightRequests();
            inflightWrites.incrementAndGet();
            doBufferWrite(buffer.flip(), inflightWrites::decrementAndGet);
            reportChunksWritten(++writtenChunks);
            buffer.clear();
        }
    }

    private void checkInflightRequests() throws IOException {
        var spinCount = 0;
        var sleepMs = 10;
        while (inflightWrites.get() >= maxInFlightRequests) {
            if (spinCount++ > SPINS_BEFORE_SLEEP) {
                ensureNoErrors();
                try {
                    //noinspection BusyWait
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ex) {
                    throw new IOException("Interrupted whilst waiting for inflight write requests to decrease", ex);
                }

                spinCount = 0;
                sleepMs = Math.min(MAX_SLEEP_BEFORE_SPIN, sleepMs * 2);
            } else {
                Thread.onSpinWait();
            }
        }

        ensureNoErrors();
    }
}

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

import static org.neo4j.io.ByteUnit.kibiBytes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.neo4j.memory.EmptyMemoryTracker;

@SuppressWarnings("ResultOfMethodCallIgnored")
class AbstractTestWriteableChannel extends WriteableChannel {

    static final int BUFFER_SIZE = (int) kibiBytes(8);

    private final FileChannel channel;

    boolean transferred;

    protected AbstractTestWriteableChannel(Path path) throws IOException {
        this(path, Integer.MAX_VALUE);
    }

    protected AbstractTestWriteableChannel(Path path, int maxInflightWrites) throws IOException {
        super(BUFFER_SIZE, maxInflightWrites, EmptyMemoryTracker.INSTANCE);
        this.channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
    }

    @Override
    public long transferFrom(Path path) throws IOException {
        transferred = true;
        return super.transferFrom(path);
    }

    @Override
    protected long internalGetSize() {
        try {
            return channel.size();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    protected void completeWriteProcess() throws IOException {
        try (channel) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    @Override
    protected void doBufferWrite(ByteBuffer data, Runnable completionHandler) throws IOException {
        channel.write(data);
        completionHandler.run();
    }

    @Override
    protected void reportChunksWritten(long chunks) {
        // no-op
    }

    @Override
    protected boolean hasBeenReplicated() {
        return false;
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.cloud.storage.io.AbstractTestWriteableChannel.BUFFER_SIZE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class WriteableChannelTest {

    @Inject
    private TestDirectory directory;

    private Path output;

    @BeforeEach
    void setup() {
        output = directory.file("output");
    }

    @Test
    void maxInflightWriteRequests() throws Exception {
        final var data = new byte[BUFFER_SIZE];
        ThreadLocalRandom.current().nextBytes(data);
        final var buffer = ByteBuffer.wrap(data);

        try (var threadPool = Executors.newFixedThreadPool(2);
                var channel = new BlockingChannel(output, threadPool, 2)) {
            final var beforeWrite = new CountDownLatch(1);
            final Future<Boolean> writeFuture = threadPool.submit(() -> {
                try {
                    channel.write(42);
                    channel.write(buffer); // will cause the channel's buffer to max out and force a write
                    beforeWrite.countDown();
                    channel.write(buffer.clear());
                    return true;
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });

            assertThat(beforeWrite.await(13, TimeUnit.SECONDS))
                    .as("should have attempted to perform the 2 writes")
                    .isTrue();

            channel.waitSignal.set(false);

            assertThat(channel.blockingWriteDone.await(13, TimeUnit.SECONDS))
                    .as("should have performed the blocking write")
                    .isTrue();

            assertThat(writeFuture.get(13, TimeUnit.SECONDS))
                    .as("should have performed the writes to the channel")
                    .isTrue();
            assertThat(channel.totalWriteCalls.await(13, TimeUnit.SECONDS))
                    .as("should have the expected number of write calls")
                    .isTrue();
        }
    }

    @Test
    void exceptionDuringAsyncWrites() throws Exception {
        final var data = new byte[BUFFER_SIZE];
        ThreadLocalRandom.current().nextBytes(data);
        final var buffer = ByteBuffer.wrap(data);

        try (var threadPool = Executors.newFixedThreadPool(2);
                var channel = new BlockingChannel(output, threadPool, -1)) {
            final var beforeWrite = new CountDownLatch(1);
            final var writeFuture = threadPool.submit(() -> {
                try {
                    channel.write(42);
                    channel.write(buffer); // will cause the channel's buffer to max out and force a write

                    beforeWrite.countDown();
                    channel.write(buffer.clear());
                    return null;
                } catch (IOException ex) {
                    return ex;
                }
            });

            assertThat(beforeWrite.await(13, TimeUnit.SECONDS))
                    .as("should have attempted to perform the 2 writes")
                    .isTrue();

            channel.waitSignal.set(false);

            assertThat(writeFuture.get(13, TimeUnit.SECONDS))
                    .isInstanceOf(IOException.class)
                    .as("should have picked up the IOException thrown asynchronously")
                    .hasMessage("boom");
            assertThat(channel.totalWriteCalls.await(13, TimeUnit.SECONDS))
                    .as("should have the only called the blocking write")
                    .isTrue();
        }
    }

    private static class BlockingChannel extends AbstractTestWriteableChannel {

        private final AtomicBoolean waitSignal = new AtomicBoolean(true);

        private final ExecutorService threadPool;

        private final CountDownLatch blockingWriteDone;
        private final CountDownLatch totalWriteCalls;

        private final boolean asyncThrows;

        private int writes = 0;

        private BlockingChannel(Path path, ExecutorService threadPool, int expectedWrites) throws IOException {
            super(path, 1);
            this.threadPool = threadPool;
            this.blockingWriteDone = new CountDownLatch(1);
            this.asyncThrows = expectedWrites == -1;
            this.totalWriteCalls = new CountDownLatch(asyncThrows ? 1 : expectedWrites);
        }

        @Override
        protected void doBufferWrite(ByteBuffer data, Runnable completionHandler) throws IOException {
            if (writes++ == 0) {
                threadPool.execute(() -> {
                    while (waitSignal.get()) {
                        Thread.onSpinWait();
                    }
                    try {
                        if (asyncThrows) {
                            //noinspection ThrowableNotThrown
                            updateErrors(new IOException("boom"));
                            completionHandler.run();
                        } else {
                            BlockingChannel.super.doBufferWrite(data, completionHandler);
                            blockingWriteDone.countDown();
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    } finally {
                        totalWriteCalls.countDown();
                    }
                });
            } else {
                super.doBufferWrite(data, completionHandler);
                totalWriteCalls.countDown();
            }
        }
    }
}

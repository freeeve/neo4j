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

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.neo4j.internal.helpers.Exceptions.chain;
import static org.neo4j.internal.unsafe.UnsafeUtil.getDirectByteBufferAddress;
import static org.neo4j.io.ByteUnit.bytesToString;
import static org.neo4j.util.Preconditions.checkArgument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.neo4j.internal.unsafe.NativeMemoryAllocationRefusedError;
import org.neo4j.internal.unsafe.UnsafeUtil;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.NullLog;
import org.neo4j.memory.MemoryTracker;

public final class NumberArrayFactories {
    private NumberArrayFactories() {}

    /**
     * Puts arrays off-heap, using unsafe calls.
     */
    public static final NumberArrayFactory OFF_HEAP = new NumberArrayFactoryImpl(BufferFactories.OFF_HEAP);

    /**
     * {@link Auto} factory.
     */
    public static final NumberArrayFactory AUTO_WITHOUT_SWAP =
            new NumberArrayFactoryImpl(new Auto(NullLog.getInstance(), BufferFactories.OFF_HEAP));

    /**
     * {@link Auto} factory which has a page cache backed number array as final fallback, in order to prevent OOM errors.
     *
     * @param dir                 directory where cached files are placed.
     * @return a {@link NumberArrayFactory} which tries to allocation off-heap, then potentially on heap and lastly falls back to allocating inside the given
     * {@code pageCache}.
     */
    public static NumberArrayFactory auto(FileSystemAbstraction fs, Path dir, InternalLog log) {
        try {
            SwappingBufferFactory swappingBufferFactory = new SwappingBufferFactory(fs, dir);
            return new NumberArrayFactoryImpl(new Auto(log, allocationAlternatives(swappingBufferFactory)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param additional          other means of allocation to try after the standard off/on heap alternatives.
     * @return an array of {@link NumberArrayFactory} with the desired alternatives.
     */
    private static BufferFactory[] allocationAlternatives(BufferFactory... additional) {
        List<BufferFactory> result = new ArrayList<>(Collections.singletonList(BufferFactories.OFF_HEAP));
        result.addAll(asList(additional));
        return result.toArray(new BufferFactory[0]);
    }

    public static NumberArrayFactory fromBufferFactory(BufferFactory bufferFactory) {
        return new NumberArrayFactoryImpl(bufferFactory);
    }

    static class NumberArrayFactoryImpl implements NumberArrayFactory {
        private final BufferFactory bufferFactory;

        NumberArrayFactoryImpl(BufferFactory bufferFactory) {
            this.bufferFactory = bufferFactory;
        }

        @Override
        public IntArray newIntArray(long length, int defaultValue, MemoryTracker memoryTracker) {
            if (length == 0) {
                return IntArray.EMPTY_ARRAY;
            }
            return new IntArrayImpl(length, 0, toUniformByte(defaultValue), bufferFactory, memoryTracker);
        }

        @Override
        public IntArray newDynamicIntArray(int chunkSize, int defaultValue, MemoryTracker memoryTracker) {
            return new IntArrayImpl(0, chunkSize, toUniformByte(defaultValue), bufferFactory, memoryTracker);
        }

        @Override
        public LongArray newLongArray(long length, long defaultValue, MemoryTracker memoryTracker) {
            if (length == 0) {
                return LongArray.EMPTY_ARRAY;
            }
            return new LongArrayImpl(length, 0, toUniformByte(defaultValue), bufferFactory, memoryTracker);
        }

        @Override
        public LongArray newDynamicLongArray(int chunkSize, long defaultValue, MemoryTracker memoryTracker) {
            return new LongArrayImpl(0, chunkSize, toUniformByte(defaultValue), bufferFactory, memoryTracker);
        }

        @Override
        public ByteArray newByteArray(long length, byte[] defaultValue, MemoryTracker memoryTracker) {
            if (length == 0) {
                return ByteArray.EMPTY_ARRAY;
            }
            return new ByteArrayImpl(
                    length, defaultValue.length, 0, toUniformByte(defaultValue), bufferFactory, memoryTracker);
        }

        @Override
        public ByteArray newDynamicByteArray(int chunkSize, byte[] defaultValue, MemoryTracker memoryTracker) {
            return new ByteArrayImpl(
                    0, defaultValue.length, chunkSize, toUniformByte(defaultValue), bufferFactory, memoryTracker);
        }

        @Override
        public void close() {
            IOUtils.closeAllUnchecked(bufferFactory);
        }

        private static byte toUniformByte(int v) {
            return toUniformByte(new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v});
        }

        private static byte toUniformByte(long v) {
            return toUniformByte(new byte[] {
                (byte) (v >> 56),
                (byte) (v >> 48),
                (byte) (v >> 40),
                (byte) (v >> 32),
                (byte) (v >> 24),
                (byte) (v >> 16),
                (byte) (v >> 8),
                (byte) v
            });
        }

        private static byte toUniformByte(byte[] bytes) {
            byte reference = bytes[0];
            for (int i = 1; i < bytes.length; i++) {
                checkArgument(reference == bytes[i], "Default value must be uniform");
            }
            return reference;
        }
    }

    /**
     * Looks at available memory and decides where the requested array fits best. Tries to allocate the whole array with the first candidate, falling back to
     * others as needed.
     */
    static class Auto implements BufferFactory {
        private final InternalLog log;
        private final BufferFactory[] candidates;
        private volatile BufferFactory currentFactory;
        private Error error;

        Auto(InternalLog log, BufferFactory... candidates) {
            this.log = log;
            this.candidates = candidates;
            this.currentFactory = candidates[0];
        }

        @Override
        public AllocatedBuffer allocate(int size, MemoryTracker memoryTracker) {
            BufferFactory bufferFactory = currentFactory;
            while (true) {
                try {
                    return bufferFactory.allocate(size, memoryTracker);
                } catch (OutOfMemoryError | NativeMemoryAllocationRefusedError e) { // Alright let's try the next one
                    bufferFactory = switchFactory(bufferFactory, size, e);
                }
            }
        }

        private synchronized BufferFactory switchFactory(BufferFactory failedCandidate, int size, Error e) {
            if (currentFactory == failedCandidate) {
                error = chain(e, error);
                int nextIndex = ArrayUtils.indexOf(candidates, failedCandidate) + 1;
                if (nextIndex >= candidates.length) {
                    throw chain(
                            new OutOfMemoryError(format(
                                    "Not enough memory available for allocating %s, tried %s",
                                    bytesToString(size), Arrays.toString(candidates))),
                            error);
                }
                currentFactory = candidates[nextIndex];
                currentFactory.warnForUsage(log);
            }

            return currentFactory;
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
        public void close() {
            IOUtils.closeAllUnchecked(candidates);
        }
    }
}

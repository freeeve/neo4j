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

import static java.lang.Math.ceilDiv;
import static java.lang.Math.multiplyExact;
import static java.lang.invoke.MethodHandles.lookup;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.neo4j.internal.helpers.ArrayUtil.MAX_ARRAY_SIZE;
import static org.neo4j.internal.helpers.VarHandleUtils.arrayElementVarHandle;
import static org.neo4j.internal.helpers.VarHandleUtils.getVarHandle;
import static org.neo4j.util.Preconditions.checkArgument;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.neo4j.internal.helpers.Numbers;
import org.neo4j.io.IOUtils;
import org.neo4j.memory.DefaultScopedMemoryTracker;
import org.neo4j.memory.MemoryTracker;

abstract class BaseDynamicArray implements NumberArray, MemoryStatsVisitor.Visitable, AutoCloseable {
    private static final VarHandle VH_BUFFERS = arrayElementVarHandle(ByteBuffer[].class);
    private static final VarHandle VH_CURRENT_DYNAMIC_SIZE = getVarHandle(lookup(), "currentDynamicSize");

    private final int bufferSize;
    private final long maxNumberOfElements;
    private final int lastBufferIndex;
    protected final int bufferPower;
    protected final int bufferMask;

    private final byte defaultValue;
    private final BufferFactory bufferFactory;
    private final MemoryTracker memoryTracker;
    private final long totalSize;
    private final ArrayList<AutoCloseable> closeables = new ArrayList<>();
    protected final int elementSize;

    @SuppressWarnings("unused")
    private int currentDynamicSize;

    private ByteBuffer[] buffers;

    BaseDynamicArray(
            long maxNumberOfElements,
            int elementSize,
            int elementsPerChunk,
            byte defaultValue,
            BufferFactory bufferFactory,
            MemoryTracker memoryTracker) {
        this.maxNumberOfElements = maxNumberOfElements;
        this.elementSize = elementSize;
        this.defaultValue = defaultValue;
        this.bufferFactory = bufferFactory;
        this.memoryTracker = new DefaultScopedMemoryTracker(memoryTracker);
        int maxChunkSize = MAX_ARRAY_SIZE / elementSize;

        if (maxNumberOfElements == 0) {
            checkArgument(elementsPerChunk > 0, "Array with dynamic size should have a chunk size");
            elementsPerChunk = Math.min(elementsPerChunk, maxChunkSize);
            totalSize = 0;
            lastBufferIndex = -1;
            bufferPower = Numbers.log2floor(elementsPerChunk);
            bufferMask = (1 << bufferPower) - 1;
            bufferSize = multiplyExact(1 << bufferPower, elementSize);
            VH_CURRENT_DYNAMIC_SIZE.set(this, 1);
            buffers = new ByteBuffer[1];
        } else {
            checkArgument(elementsPerChunk == 0, "Array with fixed size should not have a chunk size");
            bufferPower = Numbers.log2floor(maxChunkSize);
            bufferMask = (1 << bufferPower) - 1;
            int elementsPerBuffer = 1 << bufferPower;

            int numberOfBuffers = (int) ceilDiv(maxNumberOfElements, elementsPerBuffer);
            buffers = new ByteBuffer[numberOfBuffers];
            lastBufferIndex = numberOfBuffers - 1;

            totalSize = maxNumberOfElements * elementSize;
            bufferSize = elementsPerBuffer * elementSize;
        }
    }

    @Override
    public void acceptMemoryStatsVisitor(MemoryStatsVisitor visitor) {
        visitor.heapUsage(memoryTracker.estimatedHeapMemory());
        visitor.offHeapUsage(memoryTracker.usedNativeMemory());
    }

    @Override
    public long length() {
        if (isDynamic()) {
            int currentSize = (int) VH_CURRENT_DYNAMIC_SIZE.getAcquire(this);
            return (long) currentSize << bufferPower;
        }
        return maxNumberOfElements;
    }

    @Override
    public synchronized void clear() {
        for (ByteBuffer buffer : buffers) {
            if (buffer != null) {
                bufferFactory.clear(buffer, defaultValue);
            }
        }
    }

    @Override
    public synchronized void close() {
        try {
            IOUtils.closeAll(closeables);
            memoryTracker.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected ByteBuffer getBuffer(long idx) {
        int index = (int) (idx >>> bufferPower);

        // Grow backing array if needed
        if (isDynamic()) {
            if ((int) VH_CURRENT_DYNAMIC_SIZE.getAcquire(this) <= index) {
                growToHold(index);
            }
        }

        // Lazy allocation of buffers
        ByteBuffer buffer = (ByteBuffer) VH_BUFFERS.getAcquire(buffers, index);
        if (buffer == null) {
            buffer = createBufferFor(index);
        }
        return buffer;
    }

    protected int offset(long index) {
        return (int) (index & bufferMask) * elementSize;
    }

    private synchronized ByteBuffer createBufferFor(int index) {
        ByteBuffer buffer = (ByteBuffer) VH_BUFFERS.get(buffers, index);
        if (buffer == null) {
            BufferFactory.AllocatedBuffer alloc;
            if (index == lastBufferIndex) {
                final var size = (int) (totalSize % bufferSize);
                alloc = bufferFactory.allocate(size == 0 ? bufferSize : size, memoryTracker);
            } else {
                alloc = bufferFactory.allocate(bufferSize, memoryTracker);
            }
            //noinspection resource
            if (alloc.closeable() != null) {
                closeables.add(alloc.closeable());
            }
            buffer = alloc.buffer().order(LITTLE_ENDIAN);
            if (defaultValue != 0) {
                bufferFactory.clear(buffer, defaultValue);
            }
            VH_BUFFERS.setRelease(buffers, index, buffer);
        }
        return buffer;
    }

    private synchronized void growToHold(int index) {
        int currentSize = (int) VH_CURRENT_DYNAMIC_SIZE.get(this);
        if (currentSize <= index) {
            int newSize = currentSize;
            while (newSize <= index) {
                newSize = multiplyExact(newSize, 2);
            }
            ByteBuffer[] newArray = new ByteBuffer[newSize];
            System.arraycopy(buffers, 0, newArray, 0, currentSize);
            buffers = newArray;
            VH_CURRENT_DYNAMIC_SIZE.setRelease(this, newSize);
        }
    }

    private boolean isDynamic() {
        return maxNumberOfElements == 0;
    }
}

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

import org.neo4j.memory.MemoryTracker;

/**
 * Factory of {@link LongArray}, {@link IntArray} and {@link ByteArray} instances. Users can select in which type of memory the arrays will be placed, either in
 * {@link NumberArrayFactories#OFF_HEAP}, or use an auto allocator which will have each instance placed where it fits best,
 * favoring the primary candidates.
 */
public interface NumberArrayFactory extends AutoCloseable {
    /**
     * @param length size of the array.
     * @param defaultValue value which will represent unset values.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return a fixed size {@link IntArray}.
     */
    IntArray newIntArray(long length, int defaultValue, MemoryTracker memoryTracker);

    /**
     * @param chunkSize the size of each array (number of items). Where new chunks are added when needed.
     * @param defaultValue value which will represent unset values.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return dynamically growing {@link IntArray}.
     */
    IntArray newDynamicIntArray(int chunkSize, int defaultValue, MemoryTracker memoryTracker);

    /**
     * @param length size of the array.
     * @param defaultValue value which will represent unset values.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return a fixed size {@link LongArray}.
     */
    LongArray newLongArray(long length, long defaultValue, MemoryTracker memoryTracker);

    /**
     * @param chunkSize the size of each array (number of items). Where new chunks are added when needed.
     * @param defaultValue value which will represent unset values.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return dynamically growing {@link LongArray}.
     */
    LongArray newDynamicLongArray(int chunkSize, long defaultValue, MemoryTracker memoryTracker);

    /**
     * @param length size of the array.
     * @param defaultValue value which will represent unset values.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return a fixed size {@link ByteArray}.
     */
    ByteArray newByteArray(long length, byte[] defaultValue, MemoryTracker memoryTracker);

    /**
     * @param chunkSize the size of each array (number of items). Where new chunks are added when needed.
     * @param defaultValue value which will represent unset values.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return dynamically growing {@link ByteArray}.
     */
    ByteArray newDynamicByteArray(int chunkSize, byte[] defaultValue, MemoryTracker memoryTracker);

    @Override
    void close();
}

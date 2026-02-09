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
package org.neo4j.storageengine.api;

import java.io.IOException;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.kernel.KernelVersionProvider;
import org.neo4j.memory.MemoryTracker;

/**
 * Reads {@link StorageCommand commands} from a {@link ReadableChannel channel}.
 * Instances must handle concurrent threads calling it with potentially different channels.
 */
public interface CommandReader extends KernelVersionProvider {
    // Type of command = 0, means the first byte of the command record was only written but second
    // (saying what type) did not get written but the file still got expanded
    byte NONE = (byte) 0;

    /**
     * Reads the next {@link StorageCommand} from {@link ReadableChannel channel}.
     *
     * @param channel {@link ReadableChannel} to read from.
     * @param memoryTracker the {@link MemoryTracker} to use
     * @return {@link StorageCommand} if the next command was read successfully and the command is relevant,
     * otherwise {@link StorageCommand#SKIP}. If the channel end was reached then {@code null} is returned.
     * @throws IOException if channel throws exception.
     */
    StorageCommand read(ReadableChannel channel, MemoryTracker memoryTracker) throws IOException;
}

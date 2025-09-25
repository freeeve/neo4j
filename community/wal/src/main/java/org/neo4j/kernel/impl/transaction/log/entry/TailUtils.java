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
package org.neo4j.kernel.impl.transaction.log.entry;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.neo4j.io.ByteUnit.kibiBytes;

import java.io.IOException;
import java.util.Arrays;
import org.neo4j.io.fs.ReadPastEndException;
import org.neo4j.io.memory.HeapScopedBuffer;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.ReadableLogPositionAwareChannel;
import org.neo4j.memory.EmptyMemoryTracker;

public class TailUtils {
    private TailUtils() {}

    public static void checkTail(ReadableLogPositionAwareChannel channel, LogPosition currentLogPosition, Exception e)
            throws IOException {
        checkTail(channel, currentLogPosition, (int) kibiBytes(16), true, e);
    }

    public static void checkSmallChunkOfTail(ReadableLogPositionAwareChannel channel, LogPosition currentLogPosition)
            throws IOException {
        checkTail(channel, currentLogPosition, (int) kibiBytes(1), false, null);
    }

    private static void checkTail(
            ReadableLogPositionAwareChannel channel,
            LogPosition currentLogPosition,
            int bufferSize,
            boolean checkToEnd,
            Exception e)
            throws IOException {
        var zeroArray = new byte[bufferSize];
        try (var scopedBuffer = new HeapScopedBuffer(bufferSize, LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE)) {
            var buffer = scopedBuffer.getBuffer();
            boolean endReached = false;
            do {
                try {
                    channel.directRead(buffer);
                } catch (ReadPastEndException ee) {
                    // end of the file is encountered while checking ahead we ignore that and checking as much data as
                    // we got
                    endReached = true;
                }
                buffer.flip();
                if (Arrays.mismatch(buffer.array(), 0, buffer.limit(), zeroArray, 0, buffer.limit()) != -1) {
                    throw new IllegalStateException(
                            "Failure to read transaction log file number " + currentLogPosition.getLogVersion()
                                    + ". Unreadable bytes are encountered after last readable position.",
                            e);
                }
            } while (!endReached && checkToEnd);
        }
    }
}

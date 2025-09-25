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
package org.neo4j.kernel.impl.transaction.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.neo4j.io.fs.ReadableChannel;

public interface ReadableLogPositionAwareChannel extends ReadableChannel, LogPositionAwareChannel {
    /**
     * Logically, this method is the same as calling
     * {@link LogPositionAwareChannel#getCurrentLogPosition(LogPositionMarker)} followed by a call to
     * {@link ReadableChannel#getVersion()}. However, in some circumstances the call to get can cause the channel to
     * rollover into the next version when the marker has been positioned in the PREVIOUS channel, giving an
     * inconsistent reading. Implementations should ensure that the positioned marker is correct for the location of
     * the returned byte value.
     * @param marker the marker used to track the position of the underlying channel BEFORE getting the byte value
     * @return the next byte value in the channel
     * @throws IOException if unable to read the channel for data
     */
    default byte markAndGetVersion(LogPositionMarker marker) throws IOException {
        getCurrentLogPosition(marker);
        return getVersion();
    }

    default boolean rewindAfterMarkAndGetVersion() {
        return true;
    }

    /**
     * Reads raw byte data from the channel into the provided buffer
     * including any headers, or other elided data that would not
     * normally be visible using read
     *
     * @param dst buffer to copy data into
     * @return The number of bytes read, possibly zero, or -1 if the channel has reached end-of-stream
     * @throws IOException if unable to read the channel for data
     */
    int directRead(ByteBuffer dst) throws IOException;

    /**
     * Move chanel position forward to the next full entry start. This is a no op on
     * non-enveloped channels, but on enveloped files may even require bridging across several files
     *
     * @return byte offset within current log file that should be used to seek to the entry in the future.
     * N.B. This may differ from the value returned by position() as that may include headers skipped over.
     * @throws IOException if unable to alter channel position
     */
    long alignWithStartEntry() throws IOException;
}

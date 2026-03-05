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
package org.neo4j.kernel.impl.transaction.log.distributed;

import java.io.IOException;
import java.util.UUID;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;

/**
 * Carries all information relevant to Distributed Operation metadata in log entry headers. Features utility methods for
 * writing and reading this information from channels.
 *
 * @param globalSessionId The {@link UUID} for the Global Session.
 * @param ownerId The {@link UUID} for the Raft Member, or {@code null} if not applicable.
 * @param localSessionId The local session id.
 * @param sequenceNumber The sequence number.
 */
public record DistributedOperationInfo(UUID globalSessionId, UUID ownerId, long localSessionId, long sequenceNumber) {

    /**
     * Writes all information about the Distributed Operation to the channel.
     * <p>
     * <b>Note:</b> this method is public so that the logic can be shared with Enterprise Cluster code.
     *
     * @param channel The {@link WritableChannel}.
     * @throws IOException I/O error from channel.
     */
    public void writeMetadata(WritableChannel channel) throws IOException {
        UUIDLogSerializer.write(channel, globalSessionId);
        writeRaftMemberId(channel, ownerId);

        channel.putLong(localSessionId);
        channel.putLong(sequenceNumber);
    }

    /**
     * Writes the Raft Member {@link UUID} (RaftMemberId) to the channel.
     * <p>
     * <b>Note:</b> this method is public so that the logic can be shared with Enterprise Cluster code.
     *
     * @param channel The {@link WritableChannel}.
     * @param memberUuid The {@link UUID} for the Raft Member, or {@code null} if not applicable.
     * @throws IOException I/O error from channel.
     */
    public static void writeRaftMemberId(WritableChannel channel, UUID memberUuid) throws IOException {
        UUIDLogSerializer.writeNullable(channel, memberUuid);
    }

    /**
     * Reads all information about the Distributed Operation from the channel.
     * <p>
     * <b>Note:</b> this method is public so that the logic can be shared with Enterprise Cluster code.
     *
     * @param channel The {@link ReadableChannel}.
     * @return A {@link DistributedOperationInfo}, on which all relevant information is stored.
     * @throws IOException I/O error from channel.
     */
    public static DistributedOperationInfo parse(ReadableChannel channel) throws IOException {
        UUID globalSessionId = UUIDLogSerializer.parse(channel);
        UUID ownerId = parseOwnerId(channel);
        long localSessionId = channel.getLong();
        long sequenceNumber = channel.getLong();
        return new DistributedOperationInfo(globalSessionId, ownerId, localSessionId, sequenceNumber);
    }

    /**
     * Reads the Raft Member {@link UUID} (RaftMemberId) from the channel.
     * <p>
     * <b>Note:</b> this method is public so that the logic can be shared with Enterprise Cluster code.
     *
     * @param channel The {@link ReadableChannel}.
     * @return The {@link UUID} for the Raft Member, or {@code null} if not applicable.
     * @throws IOException I/O error from channel.
     */
    public static UUID parseOwnerId(ReadableChannel channel) throws IOException {
        return UUIDLogSerializer.parseNullable(channel);
    }
}

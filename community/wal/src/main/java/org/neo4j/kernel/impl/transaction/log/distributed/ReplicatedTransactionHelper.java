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

import static org.neo4j.kernel.impl.transaction.log.distributed.BatchType.STORAGE_ENGINE_ID_ONLY_HEADER;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader.DISTRIBUTED_OPERATION_CONTENT_TYPE;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader.REPLICATED_TX_CONTENT_TYPE;

import java.io.IOException;
import org.neo4j.kernel.impl.transaction.log.LogPositionMarker;
import org.neo4j.kernel.impl.transaction.log.ReadableLogPositionAwareChannel;

public class ReplicatedTransactionHelper {
    private ReplicatedTransactionHelper() {}

    public static byte skipDistributedHeaderAndGetKernelVersion(ReadableLogPositionAwareChannel channel)
            throws IOException {
        if (!channel.supportsEntrySkipping()) {
            throw new IllegalStateException(
                    "Replicated transactions should only be encountered on entry skippable/envelope channels");
        }
        LogPositionMarker parseProgress = new LogPositionMarker();
        channel.getCurrentLogPosition(parseProgress);
        if (channel.isAtStartOfFullEntry()) {
            byte contentCode = channel.get();
            if (contentCode != DISTRIBUTED_OPERATION_CONTENT_TYPE) { // ContentCode.DISTRIBUTED_OPERATION
                throw new IllegalStateException("Parsing error on DISTRIBUTED_OPERATION_CONTENT_TYPE at position="
                        + parseProgress.newPosition() + " unexpected contentCode=" + contentCode);
            }
            DistributedOperationInfo.parse(channel);
            channel.getCurrentLogPosition(parseProgress);
            contentCode = channel.get();
            if (contentCode != REPLICATED_TX_CONTENT_TYPE) { // Inner content type
                throw new IllegalStateException("Parsing error on REPLICATED_TX_CONTENT_TYPE at position="
                        + parseProgress.newPosition() + " unexpected contentCode=" + contentCode);
            }
            channel.getCurrentLogPosition(parseProgress);
            byte headerByte = channel.get();
            if (headerByte != STORAGE_ENGINE_ID_ONLY_HEADER.byteValue()) { // BatchType.STORAGE_ENGINE_ID_ONLY_HEADER
                throw new IllegalStateException("Parsing error on STORAGE_ENGINE_ID_ONLY_HEADER at position="
                        + parseProgress.newPosition() + " unexpected headerByte=" + headerByte);
            }
            channel.getCurrentLogPosition(parseProgress);
            byte storageEngineId = channel.get(); // Storage engine ID is stored even when there's no header
            if (storageEngineId < 0) {
                throw new IllegalStateException("Storage engine ID should never be negative at position="
                        + parseProgress.newPosition() + " unexpected storageEngineId=" + storageEngineId);
            }
            channel.getCurrentLogPosition(parseProgress);
        }
        // TODO MERGELOG - currently KernelVersion is inlined rather than in the envelope header
        // Note also this is also used for envelope only marshalling, but the header will have been separately skipped
        return channel.get();
    }
}

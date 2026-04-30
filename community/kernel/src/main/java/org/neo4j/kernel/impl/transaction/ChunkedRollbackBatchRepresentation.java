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
package org.neo4j.kernel.impl.transaction;

import static java.util.Collections.emptyList;
import static org.neo4j.storageengine.AppendIndexProvider.UNKNOWN_APPEND_INDEX;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_CONSENSUS_INDEX;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_TX_ID;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_TX_SEQUENCE_NUMBER;

import java.io.IOException;
import org.apache.commons.lang3.mutable.MutableLong;
import org.neo4j.common.Subject;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.api.chunk.ChunkMetadata;
import org.neo4j.kernel.impl.api.chunk.ChunkedCommandBatch;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryWriter;
import org.neo4j.storageengine.api.CommandBatch;

public record ChunkedRollbackBatchRepresentation(
        KernelVersion kernelVersion,
        long transactionId,
        long appendIndex,
        long chunkId,
        long timeWritten,
        int checksum,
        int previousChecksum)
        implements CommittedCommandBatchRepresentation {

    @Override
    public CommandBatch commandBatch() {
        return new ChunkedCommandBatch(
                emptyList(),
                new ChunkMetadata(
                        false,
                        true,
                        true,
                        UNKNOWN_APPEND_INDEX,
                        chunkId,
                        new MutableLong(UNKNOWN_CONSENSUS_INDEX),
                        new MutableLong(appendIndex),
                        timeWritten,
                        UNKNOWN_TX_ID,
                        timeWritten,
                        -1,
                        kernelVersion,
                        Subject.AUTH_DISABLED));
    }

    @Override
    public int serialize(LogEntryWriter<? extends WritableChannel> writer) throws IOException {
        return writer.writeRollbackEntry(
                kernelVersion, transactionId, appendIndex, chunkId, timeWritten, UNKNOWN_TX_SEQUENCE_NUMBER);
    }

    @Override
    public int checksum() {
        return checksum;
    }

    @Override
    public long timeWritten() {
        return timeWritten;
    }

    @Override
    public KernelVersion kernelVersion() {
        return kernelVersion;
    }

    @Override
    public long txId() {
        return transactionId;
    }

    @Override
    public boolean isRollback() {
        return true;
    }

    @Override
    public long previousBatchAppendIndex() {
        return UNKNOWN_APPEND_INDEX;
    }
}

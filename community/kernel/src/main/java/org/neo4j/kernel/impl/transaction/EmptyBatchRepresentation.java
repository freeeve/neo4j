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

import static org.neo4j.storageengine.AppendIndexProvider.UNKNOWN_APPEND_INDEX;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_CHUNK_ID;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_TX_CHECKSUM;

import java.io.IOException;
import java.util.Iterator;
import org.neo4j.common.Subject;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.internal.helpers.collection.Visitor;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryWriter;
import org.neo4j.storageengine.api.CommandBatch;
import org.neo4j.storageengine.api.Leases;
import org.neo4j.storageengine.api.StorageCommand;

public record EmptyBatchRepresentation(KernelVersion kernelVersion, long appendIndex)
        implements CommittedCommandBatchRepresentation {

    record EmptyCommandBatch(KernelVersion kernelVersionBatch, long appendIndexBatch) implements CommandBatch {
        @Override
        public long consensusIndex() {
            return 0;
        }

        @Override
        public void setConsensusIndex(long commandIndex) {}

        @Override
        public long getTimeStarted() {
            return 0;
        }

        @Override
        public long getLatestCommittedTxWhenStarted() {
            return 0;
        }

        @Override
        public long getTimeCommitted() {
            return 0;
        }

        @Override
        public int getLeaseId() {
            return 0;
        }

        @Override
        public Leases leases() {
            return null;
        }

        @Override
        public Subject subject() {
            return null;
        }

        @Override
        public String toString(boolean includeCommands) {
            return "Empty command batch. KernelVersion " + kernelVersionBatch + ", appendIndex " + appendIndexBatch;
        }

        @Override
        public boolean isLast() {
            return true;
        }

        @Override
        public boolean isFirst() {
            return true;
        }

        @Override
        public boolean isRollback() {
            return false;
        }

        @Override
        public int commandCount() {
            return 0;
        }

        @Override
        public long appendIndex() {
            return appendIndexBatch;
        }

        @Override
        public long chunkId() {
            return UNKNOWN_CHUNK_ID;
        }

        @Override
        public void setAppendIndex(long appendIndex) {}

        @Override
        public boolean accept(Visitor<StorageCommand, IOException> visitor) {
            return false;
        }

        @Override
        public Iterator<StorageCommand> iterator() {
            return Iterators.emptyResourceIterator();
        }

        @Override
        public KernelVersion kernelVersion() {
            return kernelVersionBatch;
        }
    }

    @Override
    public CommandBatch commandBatch() {
        return new EmptyCommandBatch(kernelVersion, appendIndex);
    }

    @Override
    public int serialize(LogEntryWriter<? extends WritableChannel> writer) throws IOException {
        throw new IllegalStateException("EmptyBatchRepresentation should never be serialized");
    }

    @Override
    public int checksum() {
        return UNKNOWN_TX_CHECKSUM;
    }

    @Override
    public int previousChecksum() {
        return UNKNOWN_TX_CHECKSUM;
    }

    @Override
    public long timeWritten() {
        return -1;
    }

    @Override
    public long txId() {
        return appendIndex;
    }

    @Override
    public boolean isRollback() {
        return false;
    }

    @Override
    public long previousBatchAppendIndex() {
        return UNKNOWN_APPEND_INDEX;
    }
}

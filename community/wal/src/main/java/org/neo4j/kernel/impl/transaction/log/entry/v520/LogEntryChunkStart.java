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
package org.neo4j.kernel.impl.transaction.log.entry.v520;

import static org.neo4j.kernel.impl.transaction.log.entry.LogEntryTypeCodes.CHUNK_START;

import java.util.Arrays;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.entry.AbstractVersionAwareLogEntry;
import org.neo4j.storageengine.api.Leases;
import org.neo4j.string.Mask;

public class LogEntryChunkStart extends AbstractVersionAwareLogEntry {
    private final long timeWritten;
    private final long chunkId;
    private final long previousBatchAppendIndex;
    private final byte[] additionalHeader;
    private final long appendIndex;
    private final int leaseId;
    private final Leases leases;

    public LogEntryChunkStart(
            KernelVersion kernelVersion,
            long timeWritten,
            long chunkId,
            long appendIndex,
            long previousBatchAppendIndex,
            int leaseId,
            Leases leases,
            byte[] additionalHeader) {
        super(kernelVersion, CHUNK_START);
        this.timeWritten = timeWritten;
        this.chunkId = chunkId;
        this.previousBatchAppendIndex = previousBatchAppendIndex;
        this.additionalHeader = additionalHeader;
        this.appendIndex = appendIndex;
        this.leaseId = leaseId;
        this.leases = leases;
    }

    public long getTimeWritten() {
        return timeWritten;
    }

    public long getChunkId() {
        return chunkId;
    }

    public long getAppendIndex() {
        return appendIndex;
    }

    public long getPreviousBatchAppendIndex() {
        return previousBatchAppendIndex;
    }

    public int getLeaseId() {
        return leaseId;
    }

    public Leases getLeases() {
        return leases;
    }

    public byte[] getAdditionalHeader() {
        return additionalHeader;
    }

    @Override
    public String toString(Mask mask) {
        return "LogEntryChunkStartV5_20{" + "previousBatchAppendIndex="
                + previousBatchAppendIndex + ", chunkId="
                + chunkId + ", timeWritten="
                + timeWritten + ", additionalHeader="
                + Arrays.toString(additionalHeader) + ", appendIndex="
                + appendIndex
                + ", leaseId=" + leaseId
                + ", leases=" + leases + '}';
    }
}

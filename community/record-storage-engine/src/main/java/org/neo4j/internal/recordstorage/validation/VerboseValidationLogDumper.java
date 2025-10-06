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
package org.neo4j.internal.recordstorage.validation;

import static java.lang.System.lineSeparator;
import static org.neo4j.storageengine.util.VersionValidation.PAGE_ID_MASK;

import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.neo4j.kernel.impl.locking.LockManager;
import org.neo4j.lock.ActiveLock;
import org.neo4j.lock.ResourceType;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;
import org.neo4j.storageengine.api.txstate.validation.ValidationLockDumper;

public class VerboseValidationLogDumper implements ValidationLockDumper {
    private final Log log;
    private final MutableLongObjectMap<TraceEntry> pageTraces = new LongObjectHashMap<>();

    public VerboseValidationLogDumper(LogProvider logProvider) {
        this.log = logProvider.getLog(getClass());
    }

    @Override
    public void dumpLocks(LockManager.Client lockClient, int chunkNumber, long txId) {
        try {
            StringBuilder locksDumpBuilder = new StringBuilder();
            locksDumpBuilder
                    .append("Transaction sequence number: ")
                    .append(lockClient.getTransactionId())
                    .append(" with tx id(chunk): ")
                    .append(txId)
                    .append("(")
                    .append(chunkNumber)
                    .append(")");
            var locks = lockClient.activeLocks();
            if (locks.isEmpty()) {
                locksDumpBuilder.append(" does not have any validation page locks.");
            } else {
                locksDumpBuilder.append(" locked page(s):").append(lineSeparator());

                for (ActiveLock activeLock : locks) {
                    if (activeLock.resourceType() != ResourceType.PAGE) {
                        continue;
                    }
                    long resourceId = activeLock.resourceId();
                    long pageId = resourceId & PAGE_ID_MASK;
                    TraceEntry traceEntry = pageTraces.get(pageId);
                    if (traceEntry == null) {
                        traceEntry = TraceEntry.UNKNOWN_ENTRY;
                    }
                    locksDumpBuilder
                            .append(pageId)
                            .append(" of ")
                            .append(traceEntry.storeName())
                            .append(" store, with records per page ")
                            .append(traceEntry.recordsPerPage())
                            .append(" observed page version: ")
                            .append(traceEntry.headVersion())
                            .append(lineSeparator());
                }
            }
            log.error(locksDumpBuilder.toString());
        } finally {
            reset();
        }
    }

    private void reset() {
        pageTraces.clear();
    }

    @Override
    public void add(long pageId, int unitsPerPage, String storeName, long chainHead) {
        pageTraces.put(pageId, new TraceEntry(storeName, unitsPerPage, chainHead));
    }

    private record TraceEntry(String storeName, long recordsPerPage, long headVersion) {
        private static final long UNKNOWN = -1;
        static final TraceEntry UNKNOWN_ENTRY = new TraceEntry("UNKNOWN", UNKNOWN, UNKNOWN);
    }
}

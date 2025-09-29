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
package org.neo4j.io.pagecache.context;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_LONG_ARRAY;

/**
 * Version context that allows reading everything and support dynamic committing trsansaction id
 */
public class UnboundedReadVersionContext implements VersionContext {
    private static final long INVALID_TRANSACTION_ID = 0;
    private final long oldestTransactionId;
    private long committingTransactionId = INVALID_TRANSACTION_ID;
    private long committingAppendIndex = INVALID_TRANSACTION_ID;

    public UnboundedReadVersionContext(
            long committingTransactionId, long committingAppendIndex, long oldestTransactionId) {
        this.committingTransactionId = committingTransactionId;
        this.committingAppendIndex = committingAppendIndex;
        this.oldestTransactionId = oldestTransactionId;
    }

    @Override
    public void initRead() {}

    @Override
    public void initWrite(long committingTransactionId) {
        this.committingTransactionId = committingTransactionId;
    }

    @Override
    public long committingTransactionId() {
        return committingTransactionId;
    }

    @Override
    public void initAppendIndex(long committingAppendIndex) {
        this.committingAppendIndex = committingAppendIndex;
    }

    @Override
    public long committingAppendIndex() {
        return committingAppendIndex;
    }

    @Override
    public long lastClosedTransactionId() {
        return Long.MAX_VALUE;
    }

    @Override
    public long highestClosed() {
        return Long.MAX_VALUE;
    }

    @Override
    public void markAsDirty() {}

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public long[] notVisibleTransactionIds() {
        return EMPTY_LONG_ARRAY;
    }

    @Override
    public long oldestVisibleTransactionNumber() {
        return oldestTransactionId;
    }

    @Override
    public void refreshVisibilityBoundaries() {}

    @Override
    public void observedChainHead(long headVersion) {}

    @Override
    public boolean invisibleHeadObserved() {
        return false;
    }

    @Override
    public void resetObsoleteHeadState() {}

    @Override
    public void markHeadInvisible() {}

    @Override
    public long chainHeadVersion() {
        return Long.MIN_VALUE;
    }

    @Override
    public boolean initializedForWrite() {
        return committingTransactionId != INVALID_TRANSACTION_ID;
    }

    @Override
    public int stamp() {
        return 0;
    }

    @Override
    public boolean validateStamp(int stamp) {
        return true;
    }

    @Override
    public String toString() {
        return "UnboundedReadVersionContext{" + "oldestTransactionId="
                + oldestTransactionId + ", committingTransactionId="
                + committingTransactionId + ", committingAppendIndex="
                + committingAppendIndex + '}';
    }
}

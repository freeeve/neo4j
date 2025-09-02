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
package org.neo4j.mvcc.index;

import java.util.Arrays;
import org.neo4j.io.pagecache.context.OldestTransactionIdFactory;
import org.neo4j.io.pagecache.context.TransactionIdSnapshotFactory;
import org.neo4j.io.pagecache.context.VersionContext;
import org.neo4j.io.pagecache.context.VersionContextSupplier;

public class MultiVersionTreeTestContext implements VersionContext {
    private long committingTxId;
    private long appendIndex;

    private final long lastClosedTxId;
    private final long highestClosed;
    private final long[] notVisible;
    private final long oldestTransactionId;

    MultiVersionTreeTestContext(long closedTxId, long highestClosed, long[] notVisible, long oldestTransactionId) {
        this.lastClosedTxId = closedTxId;
        this.highestClosed = highestClosed;
        this.notVisible = notVisible;
        this.oldestTransactionId = oldestTransactionId;
    }

    @Override
    public void initRead() {}

    @Override
    public void initWrite(long committingTxId) {
        this.committingTxId = committingTxId;
    }

    @Override
    public long committingTransactionId() {
        return committingTxId;
    }

    @Override
    public void initAppendIndex(long committingAppendIndex) {
        this.appendIndex = committingAppendIndex;
    }

    @Override
    public long committingAppendIndex() {
        return appendIndex;
    }

    @Override
    public long lastClosedTransactionId() {
        return lastClosedTxId;
    }

    @Override
    public long highestClosed() {
        return highestClosed;
    }

    @Override
    public void markAsDirty() {}

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public long[] notVisibleTransactionIds() {
        return notVisible;
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
        return committingTxId > 0;
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
        return "MultiversionTreeTestContext{" + "committingTxId="
                + committingTxId + ", lastClosedTxId="
                + lastClosedTxId + ", highestClosed="
                + highestClosed + ", notVisible="
                + Arrays.toString(notVisible) + ", oldestTransactionId="
                + oldestTransactionId + '}';
    }

    public static class MultiVersionTreeTestContextSupplier implements VersionContextSupplier {
        private TransactionIdSnapshotFactory snapshotFactory;
        private OldestTransactionIdFactory oldestTransactionIdFactory;

        @Override
        public void init(
                TransactionIdSnapshotFactory snapshotFactory, OldestTransactionIdFactory oldestTransactionIdFactory) {
            this.snapshotFactory = snapshotFactory;
            this.oldestTransactionIdFactory = oldestTransactionIdFactory;
        }

        @Override
        public VersionContext createVersionContext() {
            var snapshot = snapshotFactory.createSnapshot();
            return new MultiVersionTreeTestContext(
                    snapshot.lastClosedTxId(),
                    snapshot.highestEverSeen(),
                    snapshot.notVisibleTransactions(),
                    oldestTransactionIdFactory.oldestTransactionId());
        }
    }
}

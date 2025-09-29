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
package org.neo4j.kernel.impl.context;

import static org.neo4j.storageengine.AppendIndexProvider.UNKNOWN_APPEND_INDEX;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_TX_ID;

import org.neo4j.io.pagecache.context.OldestTransactionIdFactory;
import org.neo4j.io.pagecache.context.TransactionIdSnapshot;
import org.neo4j.io.pagecache.context.TransactionIdSnapshotFactory;
import org.neo4j.io.pagecache.context.UnboundedReadVersionContext;
import org.neo4j.io.pagecache.context.VersionContext;

/**
 * Transactional version context that used by read transaction to read data of specific version.
 * Or perform versioned data modification.
 */
public class TransactionVersionContext implements VersionContext {
    private static final long UNKNOWN_OBSOLETE_HEAD_VERSION = -1;
    private final TransactionIdSnapshotFactory transactionIdSnapshotFactory;
    private final OldestTransactionIdFactory oldestTransactionIdFactory;
    private long transactionId = UNKNOWN_TX_ID;
    private long appendIndex = UNKNOWN_APPEND_INDEX;
    private TransactionIdSnapshot transactionIds;
    private long oldestTransactionId = UNKNOWN_TX_ID;
    private long headChain;
    private boolean dirty;
    private boolean nonVisibleHead;
    private int currentStamp = 0;

    public TransactionVersionContext(
            TransactionIdSnapshotFactory transactionIdSnapshotFactory, OldestTransactionIdFactory oldestIdFactory) {
        this.transactionIdSnapshotFactory = transactionIdSnapshotFactory;
        this.oldestTransactionIdFactory = oldestIdFactory;
    }

    private TransactionVersionContext(
            TransactionIdSnapshotFactory transactionIdSnapshotFactory,
            OldestTransactionIdFactory oldestTransactionIdFactory,
            long transactionId,
            long appendIndex,
            TransactionIdSnapshot transactionIds,
            long oldestTransactionId,
            long headChain,
            boolean dirty,
            boolean nonVisibleHead,
            int currentStamp) {
        this(transactionIdSnapshotFactory, oldestTransactionIdFactory);
        this.transactionId = transactionId;
        this.appendIndex = appendIndex;
        this.transactionIds = transactionIds;
        this.oldestTransactionId = oldestTransactionId;
        this.headChain = headChain;
        this.dirty = dirty;
        this.nonVisibleHead = nonVisibleHead;
        this.currentStamp = currentStamp;
    }

    @Override
    public void initRead() {
        refreshVisibilityBoundaries();
        dirty = false;
    }

    @Override
    public void initWrite(long committingTxId) {
        assert committingTxId >= BASE_TX_ID;
        transactionId = committingTxId;
        oldestTransactionId = oldestTransactionIdFactory.oldestTransactionId();
    }

    @Override
    public long committingTransactionId() {
        return transactionId;
    }

    @Override
    public void initAppendIndex(long committingAppendIndex) {
        appendIndex = committingAppendIndex;
    }

    @Override
    public long committingAppendIndex() {
        return appendIndex;
    }

    @Override
    public long lastClosedTransactionId() {
        return transactionIds.lastClosedTxId();
    }

    @Override
    public long highestClosed() {
        return transactionIds.highestEverSeen();
    }

    @Override
    public long[] notVisibleTransactionIds() {
        return transactionIds.notVisibleTransactions();
    }

    @Override
    public long oldestVisibleTransactionNumber() {
        return oldestTransactionId;
    }

    @Override
    public void refreshVisibilityBoundaries() {
        transactionIds = transactionIdSnapshotFactory.createSnapshot();
        currentStamp++;
    }

    @Override
    public void observedChainHead(long headVersion) {
        headChain = headVersion;
    }

    @Override
    public boolean invisibleHeadObserved() {
        return nonVisibleHead;
    }

    @Override
    public void markHeadInvisible() {
        nonVisibleHead = true;
    }

    @Override
    public void resetObsoleteHeadState() {
        headChain = UNKNOWN_OBSOLETE_HEAD_VERSION;
        nonVisibleHead = false;
    }

    @Override
    public long chainHeadVersion() {
        return headChain;
    }

    @Override
    public void markAsDirty() {
        dirty = true;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public boolean initializedForWrite() {
        return transactionId >= BASE_TX_ID;
    }

    @Override
    public int stamp() {
        return currentStamp;
    }

    @Override
    public boolean validateStamp(int stamp) {
        return currentStamp == stamp;
    }

    @Override
    public VersionContext createRelatedContext() {
        return new TransactionVersionContext(
                transactionIdSnapshotFactory,
                oldestTransactionIdFactory,
                transactionId,
                appendIndex,
                transactionIds,
                oldestTransactionId,
                headChain,
                dirty,
                nonVisibleHead,
                currentStamp);
    }

    @Override
    public VersionContext createUnboundedReadRelatedContext() {
        return new UnboundedReadVersionContext(
                transactionId, appendIndex, oldestTransactionIdFactory.oldestTransactionId());
    }

    @Override
    public String toString() {
        return "TransactionVersionContext{" + "transactionId=" + transactionId + ", appendIndex=" + appendIndex + ", "
                + "transactionIds=" + transactionIds
                + ", oldestTransactionId=" + oldestTransactionId + ", headChain=" + headChain + ", dirty=" + dirty
                + ", nonVisibleHead=" + nonVisibleHead + '}';
    }
}

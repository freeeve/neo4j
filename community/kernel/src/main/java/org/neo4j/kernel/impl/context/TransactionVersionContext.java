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

import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_CHUNK_ID;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_TX_ID;

import org.neo4j.io.pagecache.context.OldestVisibilityHorizonFactory;
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
    private final OldestVisibilityHorizonFactory oldestVisibilityHorizonFactory;
    private long transactionId = UNKNOWN_TX_ID;
    private long chunkId = UNKNOWN_CHUNK_ID;
    private TransactionIdSnapshot transactionIds;
    private long oldestVisibilityHorizon = UNKNOWN_TX_ID;
    private long headChain;
    private boolean dirty;
    private boolean nonVisibleHead;
    private int currentStamp = 0;

    public TransactionVersionContext(
            TransactionIdSnapshotFactory transactionIdSnapshotFactory, OldestVisibilityHorizonFactory oldestIdFactory) {
        this.transactionIdSnapshotFactory = transactionIdSnapshotFactory;
        this.oldestVisibilityHorizonFactory = oldestIdFactory;
    }

    private TransactionVersionContext(
            TransactionIdSnapshotFactory transactionIdSnapshotFactory,
            OldestVisibilityHorizonFactory oldestVisibilityHorizonFactory,
            long transactionId,
            long chunkId,
            TransactionIdSnapshot transactionIds,
            long oldestVisibilityHorizon,
            long headChain,
            boolean dirty,
            boolean nonVisibleHead,
            int currentStamp) {
        this(transactionIdSnapshotFactory, oldestVisibilityHorizonFactory);
        this.transactionId = transactionId;
        this.chunkId = chunkId;
        this.transactionIds = transactionIds;
        this.oldestVisibilityHorizon = oldestVisibilityHorizon;
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
        oldestVisibilityHorizon = oldestVisibilityHorizonFactory.oldestVisibilityHorizon();
    }

    @Override
    public long committingTransactionId() {
        return transactionId;
    }

    @Override
    public void initChunkId(long committingChunkId) {
        chunkId = committingChunkId;
    }

    @Override
    public long committingChunkId() {
        return chunkId;
    }

    @Override
    public long highestGapFree() {
        return transactionIds.highestGapFree();
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
    public long oldestVisibilityHorizon() {
        assert initializedForWrite();
        return oldestVisibilityHorizon;
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
                oldestVisibilityHorizonFactory,
                transactionId,
                chunkId,
                transactionIds,
                oldestVisibilityHorizon,
                headChain,
                dirty,
                nonVisibleHead,
                currentStamp);
    }

    @Override
    public VersionContext createUnboundedReadRelatedContext() {
        return new UnboundedReadVersionContext(
                transactionId, chunkId, oldestVisibilityHorizonFactory.oldestVisibilityHorizon());
    }

    @Override
    public String toString() {
        return "TransactionVersionContext{" + "transactionId=" + transactionId + ", appendIndex=" + chunkId + ", "
                + "transactionIds=" + transactionIds
                + ", oldestTransactionId=" + oldestVisibilityHorizon + ", headChain=" + headChain + ", dirty=" + dirty
                + ", nonVisibleHead=" + nonVisibleHead + '}';
    }
}

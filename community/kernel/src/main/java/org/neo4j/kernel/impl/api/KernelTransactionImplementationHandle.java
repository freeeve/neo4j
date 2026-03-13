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
package org.neo4j.kernel.impl.api;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.graphdb.NotInTransactionException;
import org.neo4j.graphdb.TransactionTerminatedException;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;
import org.neo4j.internal.kernel.api.security.AuthSubject;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.context.VersionContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.KernelTransactionHandle;
import org.neo4j.kernel.api.TerminationMark;
import org.neo4j.kernel.api.TransactionTimeout;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.query.ExecutingQuery;
import org.neo4j.kernel.impl.api.transaction.trace.TransactionInitializationTrace;
import org.neo4j.lock.ActiveLock;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.time.SystemNanoClock;

/**
 * A {@link KernelTransactionHandle} that wraps the given {@link KernelTransactionImplementation}.
 * This handle knows that {@link KernelTransactionImplementation}s can be reused and represents a single logical
 * transaction. This means that methods like {@link #markForTermination(Status)} can only terminate running
 * transaction this handle was created for.
 */
class KernelTransactionImplementationHandle implements KernelTransactionHandle {
    private static final String USER_TRANSACTION_NAME_SEPARATOR = "-transaction-";

    private final long startTime;
    private final long startTimeNanos;
    private final TransactionTimeout timeout;
    private final KernelTransactionImplementation tx;
    private final SystemNanoClock clock;
    private final ClientConnectionInfo clientInfo;
    private final AuthSubject subject;
    private final Optional<TerminationMark> terminationMark;
    private final Optional<ExecutingQuery> executingQuery;
    private final Map<String, Object> metaData;
    private final String statusDetails;
    private final TransactionInitializationTrace initializationTrace;
    private final KernelTransactionStamp transactionStamp;
    private final String databaseName;
    private final long highestGapFreeTxId;
    private final long transactionHorizon;

    KernelTransactionImplementationHandle(
            KernelTransactionImplementation tx, SystemNanoClock clock, CursorContext cursorContext) {
        this.transactionStamp = new KernelTransactionStamp(tx);
        this.startTime = tx.startTime();
        this.startTimeNanos = tx.startTimeNanos();
        this.timeout = tx.timeout();
        this.subject = tx.subjectOrAnonymous();
        this.terminationMark = tx.getTerminationMark();
        this.executingQuery = tx.executingQuery();
        this.metaData = tx.getMetaData();
        this.statusDetails = tx.statusDetails();
        this.initializationTrace = tx.getInitializationTrace();
        this.clientInfo = tx.clientInfo();
        this.databaseName = tx.getDatabaseName();
        var versionContext = cursorContext.getVersionContext();
        this.highestGapFreeTxId = versionContext.highestGapFree();
        this.transactionHorizon = transactionHorizon(versionContext);
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public long startTime() {
        return startTime == 0L ? Long.MAX_VALUE : startTime;
    }

    @Override
    public long startTimeNanos() {
        return startTimeNanos == 0L ? Long.MAX_VALUE : startTimeNanos;
    }

    @Override
    public TransactionTimeout timeout() {
        return timeout == null ? TransactionTimeout.NO_TIMEOUT : timeout;
    }

    @Override
    public boolean isOpen() {
        return transactionStamp.isOpen();
    }

    @Override
    public boolean isCommitting() {
        return transactionStamp.isCommitting();
    }

    @Override
    public boolean isRollingback() {
        return transactionStamp.isRollingback();
    }

    @Override
    public boolean markForTermination(Status reason) {
        return tx.markForTermination(transactionStamp.getTransactionSequenceNumber(), reason);
    }

    @Override
    public AuthSubject subject() {
        return subject == null ? AuthSubject.ANONYMOUS : subject;
    }

    @Override
    public Map<String, Object> getMetaData() {
        return metaData == null ? Map.of() : metaData;
    }

    @Override
    public String getStatusDetails() {
        return statusDetails == null ? StringUtils.EMPTY : statusDetails;
    }

    @Override
    public Optional<TerminationMark> terminationMark() {
        return terminationMark;
    }

    @Override
    public boolean isUnderlyingTransaction(KernelTransaction tx) {
        return this.tx == tx;
    }

    @Override
    public long getTransactionSequenceNumber() {
        return transactionStamp.getTransactionSequenceNumber();
    }

    @Override
    public String getUserTransactionName() {
        return getDatabaseName() + USER_TRANSACTION_NAME_SEPARATOR + getTransactionSequenceNumber();
    }

    private String getDatabaseName() {
        return databaseName == null ? StringUtils.EMPTY : databaseName;
    }

    @Override
    public Optional<ExecutingQuery> executingQuery() {
        return executingQuery;
    }

    @Override
    public Collection<ActiveLock> activeLocks(MemoryTracker memoryTracker) {
        return tx.activeLocks(memoryTracker);
    }

    @Override
    public long activeLockCount() {
        try {
            return tx.lockClient().activeLockCount();
        } catch (NotInTransactionException | TransactionTerminatedException e) {
            return 0L;
        }
    }

    @Override
    public TransactionExecutionStatistic transactionStatistic() {
        if (transactionStamp.isNotExpired()) {
            return new TransactionExecutionStatistic(tx, clock, startTime);
        }
        return TransactionExecutionStatistic.NOT_AVAILABLE;
    }

    @Override
    public TransactionInitializationTrace transactionInitialisationTrace() {
        return initializationTrace == null ? TransactionInitializationTrace.NONE : initializationTrace;
    }

    @Override
    public Optional<ClientConnectionInfo> clientInfo() {
        return ofNullable(clientInfo);
    }

    @Override
    public boolean isSchemaTransaction() {
        return tx.isSchemaTransaction();
    }

    @Override
    public long getHighestGapFreeTxId() {
        return highestGapFreeTxId;
    }

    @Override
    public long getTransactionHorizon() {
        return transactionHorizon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KernelTransactionImplementationHandle that = (KernelTransactionImplementationHandle) o;
        return transactionStamp.equals(that.transactionStamp);
    }

    @Override
    public int hashCode() {
        return transactionStamp.hashCode();
    }

    @Override
    public String toString() {
        return "KernelTransactionImplementationHandle{" + "startTime="
                + startTime + ", startTimeNanos="
                + startTimeNanos + ", timeout="
                + timeout + ", tx="
                + tx + ", clock="
                + clock + ", clientInfo="
                + clientInfo + ", subject="
                + subject + ", terminationMark="
                + terminationMark + ", executingQuery="
                + executingQuery + ", metaData="
                + metaData + ", statusDetails='"
                + statusDetails + '\'' + ", initializationTrace="
                + initializationTrace + ", transactionStamp="
                + transactionStamp + ", databaseName='"
                + databaseName + '\'' + ", highestGapFreeTxId="
                + highestGapFreeTxId + ", transactionHorizon="
                + transactionHorizon + '}';
    }

    private long transactionHorizon(VersionContext versionContext) {
        // if transaction has already started committing its horizon is oldestVisibleTransactionNumber which was
        // recorded at the time commit started
        if (versionContext.initializedForWrite()) {
            return versionContext.oldestVisibilityHorizon();
        }
        // otherwise, its horizon is the latest gap free closed transaction at the time it started
        return versionContext.highestGapFree();
    }
}

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
package org.neo4j.kernel.impl.api.transaction.monitor;

import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.neo4j.configuration.Config;
import org.neo4j.kernel.api.KernelTransactionHandle;
import org.neo4j.kernel.api.TerminationMark;
import org.neo4j.kernel.api.TransactionTimeout;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.impl.api.KernelTransactions;
import org.neo4j.kernel.impl.api.TransactionVisibilityProvider;
import org.neo4j.kernel.impl.api.index.IndexingService;
import org.neo4j.kernel.impl.api.transaction.trace.TransactionInitializationTrace;
import org.neo4j.logging.internal.LogService;
import org.neo4j.storageengine.api.TransactionIdStore;
import org.neo4j.time.SystemNanoClock;

public class KernelTransactionMonitor extends TransactionMonitor<KernelTransactionMonitor.MonitoredKernelTransaction>
        implements TransactionVisibilityProvider {
    private final KernelTransactions kernelTransactions;
    private final TransactionIdStore transactionIdStore;
    private final IndexingService indexingService;
    private final AtomicLong oldestCleanupHorizon = new AtomicLong(BASE_TX_ID);
    private final AtomicLong oldestVisibilityHorizon = new AtomicLong(BASE_TX_ID);

    public KernelTransactionMonitor(
            KernelTransactions kernelTransactions,
            TransactionIdStore transactionIdStore,
            Config config,
            SystemNanoClock clock,
            LogService logService,
            IndexingService indexingService) {
        super(config, clock, logService);
        this.kernelTransactions = kernelTransactions;
        this.transactionIdStore = transactionIdStore;
        this.indexingService = indexingService;
        oldestVisibilityHorizon.setRelease(
                transactionIdStore.getHighestEverClosedTransaction().id());
        oldestCleanupHorizon.setRelease(
                transactionIdStore.getHighestEverClosedTransaction().id());
    }

    @Override
    protected void updateTransactionBoundaries() {
        // we return gap free transaction that is already closed, and if we do not have any readers it should be safe to
        // assume that no one will need
        // data before that point of history
        var oldestGapFreeClosedTransactionId = transactionIdStore.getHighestGapFreeClosedTransactionId();
        var executingTransactions = kernelTransactions.executingTransactions();
        long minHighestGapFree = oldestGapFreeClosedTransactionId;
        long minCleanupHorizon = oldestGapFreeClosedTransactionId;

        for (var txHandle : executingTransactions) {
            if (txHandle.terminationMark().isEmpty()) {
                minHighestGapFree = Math.min(minHighestGapFree, txHandle.getHighestGapFreeTxId());
                minCleanupHorizon = Math.min(minCleanupHorizon, txHandle.getTransactionHorizon());
            }
        }
        var populationJobs = indexingService.getPopulationJobs();
        for (var job : populationJobs) {
            // for this purpose population job is read only transaction
            // so it's horizon is last closed transaction
            long populationHorizon = job.populationHorizon();
            minHighestGapFree = Math.min(minHighestGapFree, populationHorizon);
            minCleanupHorizon = Math.min(minCleanupHorizon, populationHorizon);
        }
        oldestVisibilityHorizon.setRelease(minHighestGapFree);
        oldestCleanupHorizon.setRelease(minCleanupHorizon);
    }

    @Override
    protected Set<MonitoredKernelTransaction> getActiveTransactions() {
        return kernelTransactions.activeTransactions().stream()
                .map(MonitoredKernelTransaction::new)
                .collect(Collectors.toSet());
    }

    @Override
    public long oldestVisibilityHorizon() {
        return oldestVisibilityHorizon.getAcquire();
    }

    @Override
    public long oldestCleanupHorizon() {
        return oldestCleanupHorizon.getAcquire();
    }

    @Override
    public long youngestObservableHorizon() {
        long youngestHorizon = Long.MIN_VALUE;
        for (var monitoredTx : getActiveTransactions()) {
            if (monitoredTx.terminationMark().isEmpty()) {
                youngestHorizon = Math.max(youngestHorizon, monitoredTx.kernelTransaction.getTransactionHorizon());
            }
        }
        return youngestHorizon;
    }

    static class MonitoredKernelTransaction implements MonitoredTransaction {
        private final KernelTransactionHandle kernelTransaction;

        private MonitoredKernelTransaction(KernelTransactionHandle kernelTransaction) {
            this.kernelTransaction = kernelTransaction;
        }

        @Override
        public long startTimeNanos() {
            return kernelTransaction.startTimeNanos();
        }

        @Override
        public TransactionTimeout timeout() {
            return kernelTransaction.timeout();
        }

        @Override
        public Optional<TerminationMark> terminationMark() {
            return kernelTransaction.terminationMark();
        }

        @Override
        public boolean isSchemaTransaction() {
            return kernelTransaction.isSchemaTransaction();
        }

        @Override
        public boolean markForTermination(Status reason) {
            return kernelTransaction.markForTermination(reason);
        }

        @Override
        public String getIdentifyingDescription() {
            // this is a legacy implementation, so let's use
            // 'toString' on KernelTransactionHandle which was used for years for this purpose
            return kernelTransaction.toString();
        }

        @Override
        public TransactionInitializationTrace transactionInitialisationTrace() {
            return kernelTransaction.transactionInitialisationTrace();
        }
    }
}

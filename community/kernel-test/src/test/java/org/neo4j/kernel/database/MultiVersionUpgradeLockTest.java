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
package org.neo4j.kernel.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.database.DatabaseUpgradeTransactionHandler.DatabaseUpgradeListener.MultiVersionUpgradeGate;
import org.neo4j.kernel.impl.api.KernelTransactionImplementation;
import org.neo4j.kernel.impl.api.KernelTransactions;
import org.neo4j.kernel.impl.api.state.TxState;

class MultiVersionUpgradeLockTest {

    @Test
    void acquireUpgradePermit() {
        KernelTransactions transactions = mock(KernelTransactions.class);
        when(transactions.earliestTransactionSequenceNumber()).thenReturn(1L);
        KernelTransactionImplementation kernelTransaction = mock(KernelTransactionImplementation.class);
        when(kernelTransaction.getTransactionSequenceNumber()).thenReturn(1L);
        when(kernelTransaction.txState()).thenReturn(mock(TxState.class));

        DatabaseUpgradeTransactionHandler.DatabaseUpgradeListener.MultiVersionUpgradeGate upgradeLock =
                new DatabaseUpgradeTransactionHandler.DatabaseUpgradeListener.MultiVersionUpgradeGate(transactions);
        assertTrue(upgradeLock.upgradeGate(kernelTransaction));
    }

    @Test
    void multiChunkTransactionFailToAcquireUpgradePermit() {
        KernelTransactions transactions = mock(KernelTransactions.class);
        when(transactions.earliestTransactionSequenceNumber()).thenReturn(1L);
        KernelTransactionImplementation kernelTransaction = mock(KernelTransactionImplementation.class);
        when(kernelTransaction.getTransactionSequenceNumber()).thenReturn(1L);
        TxState txState = mock(TxState.class);
        when(txState.isMultiChunk()).thenReturn(true);
        when(kernelTransaction.txState()).thenReturn(txState);

        DatabaseUpgradeTransactionHandler.DatabaseUpgradeListener.MultiVersionUpgradeGate upgradeLock =
                new DatabaseUpgradeTransactionHandler.DatabaseUpgradeListener.MultiVersionUpgradeGate(transactions);
        assertFalse(upgradeLock.upgradeGate(kernelTransaction));
    }

    @Test
    void transactionWithHigherSequenceNumberBlocked() {
        KernelTransactions transactions = mock(KernelTransactions.class);
        when(transactions.earliestTransactionSequenceNumber()).thenReturn(5L);

        KernelTransactionImplementation kernelTransaction1 = mock(KernelTransactionImplementation.class);
        when(kernelTransaction1.getTransactionSequenceNumber()).thenReturn(5L);
        when(kernelTransaction1.txState()).thenReturn(mock(TxState.class));

        KernelTransactionImplementation kernelTransaction2 = mock(KernelTransactionImplementation.class);
        when(kernelTransaction2.getTransactionSequenceNumber()).thenReturn(10L);
        when(kernelTransaction2.txState()).thenReturn(mock(TxState.class));

        DatabaseUpgradeTransactionHandler.DatabaseUpgradeListener.MultiVersionUpgradeGate upgradeLock =
                new MultiVersionUpgradeGate(transactions);
        assertTrue(upgradeLock.upgradeGate(kernelTransaction1));

        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                Future<?> future = executor.submit(() -> upgradeLock.upgradeGate(kernelTransaction2));
                assertThrows(TimeoutException.class, () -> future.get(5, TimeUnit.SECONDS));
            } finally {
                when(transactions.earliestTransactionSequenceNumber()).thenReturn(10L);
                upgradeLock.release();
            }
        }
    }
}

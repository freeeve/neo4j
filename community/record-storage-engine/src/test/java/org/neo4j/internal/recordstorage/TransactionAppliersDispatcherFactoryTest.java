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
package org.neo4j.internal.recordstorage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.neo4j.storageengine.api.StorageEngineTransaction;

class TransactionAppliersDispatcherFactoryTest {
    private TransactionAppliersDispatcherFactory facade;
    private TransactionApplierFactory applier1;
    private TransactionApplierFactory applier2;
    private TransactionApplierFactory applier3;

    @BeforeEach
    void setUp() throws Exception {
        TransactionApplier txApplier1 = mock(TransactionApplier.class);
        applier1 = mock(TransactionApplierFactory.class);
        when(applier1.startTx(any(StorageEngineTransaction.class), any(BatchContext.class)))
                .thenReturn(txApplier1);

        TransactionApplier txApplier2 = mock(TransactionApplier.class);
        applier2 = mock(TransactionApplierFactory.class);
        when(applier2.startTx(any(StorageEngineTransaction.class), any(BatchContext.class)))
                .thenReturn(txApplier2);

        TransactionApplier txApplier3 = mock(TransactionApplier.class);
        applier3 = mock(TransactionApplierFactory.class);
        when(applier3.startTx(any(StorageEngineTransaction.class), any(BatchContext.class)))
                .thenReturn(txApplier3);

        facade = new TransactionAppliersDispatcherFactory(
                (workSync, cursorContext) -> workSync.newBatch(cursorContext, false), applier1, applier2, applier3);
    }

    @Test
    void testStartTxCorrectOrder() throws Exception {
        // GIVEN
        var tx = mock(StorageEngineTransaction.class);
        var batchContext = mock(BatchContext.class);

        // WHEN
        facade.startTx(tx, batchContext);

        // THEN
        InOrder inOrder = inOrder(applier1, applier2, applier3);

        inOrder.verify(applier1).startTx(tx, batchContext);
        inOrder.verify(applier2).startTx(tx, batchContext);
        inOrder.verify(applier3).startTx(tx, batchContext);
    }

    @Test
    void testStartTxCorrectOrderWithLockGroup() throws Exception {
        // GIVEN
        StorageEngineTransaction tx = mock(StorageEngineTransaction.class);
        var batchContext = mock(BatchContext.class);

        // WHEN
        facade.startTx(tx, batchContext);

        // THEN
        InOrder inOrder = inOrder(applier1, applier2, applier3);

        inOrder.verify(applier1).startTx(tx, batchContext);
        inOrder.verify(applier2).startTx(tx, batchContext);
        inOrder.verify(applier3).startTx(tx, batchContext);
    }
}

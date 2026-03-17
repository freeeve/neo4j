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

import java.util.function.Supplier;
import org.neo4j.storageengine.api.StorageEngineTransaction;

/**
 * Serves as a reusable utility for building a chain of {@link StorageEngineTransaction} instances,
 * where the instances themselves form the linked list. This utility is just for easily being able
 * to append to the end and then at regular intervals batch through the whole queue.
 */
public class TransactionQueue {

    @FunctionalInterface
    public interface Applier {
        void apply(StorageEngineTransaction tx) throws Exception;
    }

    private final int maxSize;
    private volatile Applier applier;
    private StorageEngineTransaction tail;
    private StorageEngineTransaction head;
    private int size;

    public TransactionQueue(int maxSize, Applier applier) {
        this.maxSize = maxSize;
        this.applier = applier;
    }

    public TransactionQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    public void installApplier(Applier applier) {
        this.applier = applier;
    }

    public boolean willQueueWithCurrentBatch(StorageEngineTransaction transaction) {
        // will we add this transaction to the current batch in the queue (if any)
        return isEmpty()
                || (transaction.commandBatch().kernelVersion()
                        == tail.commandBatch().kernelVersion());
    }

    public void queue(StorageEngineTransaction transaction) throws Exception {
        assert applier != null;

        if (isNotEmpty()) {
            if (transaction.commandBatch().kernelVersion()
                    != tail.commandBatch().kernelVersion()) {
                // Different kernel versions.. Let's split the batch to make upgrade easier
                applyTransactions();
                queue(transaction);
                return;
            }
            tail.next(transaction);
        } else {
            head = transaction;
        }
        tail = transaction;
        if (++size == maxSize) {
            applyTransactions();
        }
    }

    /**
     * @param transactionSupplier supplier for a {@link StorageEngineTransaction}. We create it lazily to avoid trying
     *                            to call the Kernel components needed to do so before they are guaranteed to exist.
     * @return true if not ignored
     */
    public boolean queueNonTx(Supplier<StorageEngineTransaction> transactionSupplier) throws Exception {
        // Only interested in non-tx things that happen after kernel store has been created.
        // For first start up SeedStoreEntry block until kernel is up to guarantee we don't miss non tx of interest
        // For subsequent starts kernel should be up before processing begins
        if (applier == null) {
            return false;
        }

        queue(transactionSupplier.get());
        return true;
    }

    public void applyTransactions() throws Exception {
        if (isNotEmpty()) {
            applier.apply(head);
            tail = null;
            head = null;
            size = 0;
        }
    }

    private boolean isEmpty() {
        return size == 0;
    }

    private boolean isNotEmpty() {
        return !isEmpty();
    }
}

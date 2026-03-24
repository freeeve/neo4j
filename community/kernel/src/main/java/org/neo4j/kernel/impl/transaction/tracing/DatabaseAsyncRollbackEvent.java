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
package org.neo4j.kernel.impl.transaction.tracing;

public interface DatabaseAsyncRollbackEvent extends AutoCloseable {

    DatabaseAsyncRollbackEvent NULL = new DatabaseAsyncRollbackEvent() {
        @Override
        public TransactionRollbackEvent beginAsyncTransactionRollback() {
            return TransactionRollbackEvent.NULL;
        }

        @Override
        public void databaseRollbackCompleted(
                boolean completedSuccessfully, int rolledBackTransactions, int chunkedOngoingTransactions) {}

        @Override
        public void close() {}
    };

    /**
     * Rollback event of transaction that is replicated in a cluster commit.
     * @return rollback event
     */
    TransactionRollbackEvent beginAsyncTransactionRollback();

    void databaseRollbackCompleted(
            boolean completedSuccessfully, int rolledBackTransactions, int chunkedOngoingTransactions);

    @Override
    void close();
}

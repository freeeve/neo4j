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

public interface TransactionVisibilityProvider {
    TransactionVisibilityProvider EMPTY_VISIBILITY_PROVIDER = new TransactionVisibilityProvider() {
        @Override
        public long oldestVisibilityHorizon() {
            return Long.MAX_VALUE;
        }

        @Override
        public long oldestCleanupHorizon() {
            return Long.MIN_VALUE;
        }

        @Override
        public long youngestObservableHorizon() {
            return Long.MAX_VALUE;
        }
    };

    /**
     * Minimum highest gap free transaction id among all executing transactions.
     * Means that there is no transaction that sees version `oldestVisibilityHorizon-1` but does not see `oldestVisibilityHorizon`
     * Means that transaction with id oldestVisibilityHorizon is visible to everyone.
     */
    long oldestVisibilityHorizon();

    /**
     * Versions equal to or greater than this still can be accessed by the _updaters_ (in the tree) to create history chains.
     *
     * This is not read visibility. It means there is a transaction that was intialized for write with this number as oldestVisibilityHorizon.
     * That write transaction when updating trees will be preserving history up to that oldestVisibilityHorizon.
     *
     * This is the smallest of such oldestVisibilityHorizon's
     */
    long oldestCleanupHorizon();

    long youngestObservableHorizon();
}

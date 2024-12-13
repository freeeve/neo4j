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
package org.neo4j.batchimport.api;

import java.io.IOException;
import java.util.List;
import org.neo4j.exceptions.KernelException;
import org.neo4j.internal.schema.IndexDescriptor;

/**
 * Used by the {@link BatchImporter} to create/drop indexes during the import process.
 */
public interface IndexesLifecycleManager extends AutoCloseable {

    /**
     * @param index the index descriptor to complete before writing to the schema store
     * @return the completed index descriptor
     */
    IndexDescriptor completeConfiguration(IndexDescriptor index);

    /**
     * Drop all the provided indexes
     * @param dropListener callback mechanism used to track the status of the drop process
     * @param indexDescriptors the indexes to drop
     */
    void drop(DropListener dropListener, List<IndexDescriptor> indexDescriptors);

    /**
     * Creates all the provided indexes
     * @param creationListener callback mechanism used to track the status of the creation process
     * @param indexDescriptors the indexes to create
     * @throws IOException if unable to create any indexes
     */
    void create(CreationListener creationListener, List<IndexDescriptor> indexDescriptors) throws IOException;

    @Override
    void close();

    interface DropListener {
        /**
         * Updates interested parties when the index has been removed for the provided {@link IndexDescriptor}
         * @param indexDescriptor the index that has been dropped
         * @return <code>true</code> if the index is considered dropped
         */
        boolean onDrop(IndexDescriptor indexDescriptor);

        /**
         * Updates interested parties when unable to drop the provided {@link IndexDescriptor}
         * @param indexDescriptor the index to drop
         * @param ex the error that occurred
         */
        void onDropFailed(IndexDescriptor indexDescriptor, RuntimeException ex);

        /**
         * When the index removal for all the indexes have been completed
         * @param dropOk the number of indexes successfully dropped
         * @param dropFailed the number of indexes where the drop failed
         */
        void onDropCompleted(int dropOk, int dropFailed);
    }

    interface CreationListener {
        /**
         * Updates interested parties with the latest status of the index creation for the provided {@link IndexDescriptor}
         * @param indexDescriptor the index that has been updated
         * @param percentDelta indicator of how much of the index creation changed from the last update
         */
        void onUpdate(IndexDescriptor indexDescriptor, float percentDelta);

        /**
         * Updates interested parties when the provided {@link IndexDescriptor} failed to be created
         * @param indexDescriptor the index that failed creation
         * @param error the error that was raised
         */
        void onFailure(IndexDescriptor indexDescriptor, KernelException error);

        /**
         * When the index creation for all the indexes have been completed
         * @param withSuccess <code>true</code> if the creation process completed successfully
         */
        void onCreationCompleted(boolean withSuccess);

        /**
         * When the index creation for all the indexes have been check pointed. This will only be called if the creation
         * step completed successfully.
         */
        void onCheckpointingCompleted();
    }

    IndexesLifecycleManager EMPTY_CREATOR = new IndexesLifecycleManager() {
        @Override
        public void drop(DropListener dropListener, List<IndexDescriptor> indexDescriptors) {
            // no-op
        }

        @Override
        public IndexDescriptor completeConfiguration(IndexDescriptor index) {
            return index;
        }

        @Override
        public void create(CreationListener creationListener, List<IndexDescriptor> indexDescriptors) {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    };
}

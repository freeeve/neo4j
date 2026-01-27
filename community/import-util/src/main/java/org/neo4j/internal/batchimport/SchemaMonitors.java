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
package org.neo4j.internal.batchimport;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.function.LongPredicate;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.internal.helpers.progress.ProgressMonitorFactory;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.api.index.IndexAccessor;

public interface SchemaMonitors extends Closeable {
    SchemaMonitors NO_SCHEMA = new SchemaMonitors() {
        @Override
        public LongSet validate(Collector collector, ProgressMonitorFactory progressMonitorFactory) {
            return LongSets.immutable.empty();
        }

        @Override
        public void writeToTarget(
                LongPredicate violatingIdMapperEntityIds,
                LongSet otherViolatingEntityIds,
                ProgressMonitorFactory progressMonitorFactory) {}

        @Override
        public LongSet affectedIndexes() {
            return LongSets.immutable.empty();
        }

        @Override
        public Optional<IndexDescriptor> indexDescriptor(long indexId) {
            return Optional.empty();
        }

        @Override
        public Optional<IndexAccessor> openTempIndexAccessor(long indexId) {
            return Optional.empty();
        }

        @Override
        public Optional<IndexAccessor> openTargetIndexAccessor(long indexId) {
            return Optional.empty();
        }

        @Override
        public void close() {}

        @Override
        public SchemaMonitor newMonitor(int workerId) {
            return SchemaMonitor.NO_MONITOR;
        }
    };

    /**
     * Validates indexes for correctness (mostly uniqueness for those that have such constraints)
     * and returns a {@link LongSet} of entity IDs that violate such constraints.
     * @param collector for reporting the violating entities.
     * @param progressMonitorFactory for progress reporting.
     * @return a {@link LongSet} of entity IDs that violate any constraints.
     * @throws IOException on I/O error.
     */
    LongSet validate(Collector collector, ProgressMonitorFactory progressMonitorFactory) throws IOException;

    /**
     * Writes the built indexes into their target index. For full import this is just moving the index into
     * place in the happy case. For incremental import the incremental indexes are merged into the target indexes.
     *
     * @param violatingIdMapperEntityIds entity IDs from the {@link org.neo4j.internal.batchimport.cache.idmapping.IdMapper}
     * that violate constraints
     * @param otherViolatingEntityIds entity IDs from e.g. {@link #validate(Collector, ProgressMonitorFactory)}.
     * @param progressMonitorFactory for progress reporting.
     * @throws IOException on I/O error.
     */
    void writeToTarget(
            LongPredicate violatingIdMapperEntityIds,
            LongSet otherViolatingEntityIds,
            ProgressMonitorFactory progressMonitorFactory)
            throws IOException;

    LongSet affectedIndexes();

    Optional<IndexDescriptor> indexDescriptor(long indexId);

    /**
     * Opens the index accessor for the "temp" index with the given id.
     * In `ImportIndexBuilder` the "temp" index is the one holding all new data until it gets merged into the target index.
     * This should return a non-empty accessor if we have had any new data for this index during the import.
     */
    Optional<IndexAccessor> openTempIndexAccessor(long indexId) throws IOException;

    /**
     * Opens the index accessor for the target index with the given id.
     * In `ImportIndexBuilder` the target index is the one that will hold all data after the import is done.
     * It also contains all data prior to the import.
     * This should never return an empty accessor expect for {@link #NO_SCHEMA}.
     */
    Optional<IndexAccessor> openTargetIndexAccessor(long indexId);

    SchemaMonitor newMonitor(int workerId);
}

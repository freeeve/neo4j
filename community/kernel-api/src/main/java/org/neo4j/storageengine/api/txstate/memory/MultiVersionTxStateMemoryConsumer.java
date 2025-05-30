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
package org.neo4j.storageengine.api.txstate.memory;

import static org.neo4j.configuration.GraphDatabaseInternalSettings.multi_version_deletion_additional_reservation_size;

import org.neo4j.configuration.Config;
import org.neo4j.memory.MemoryTracker;

public class MultiVersionTxStateMemoryConsumer implements TxStateMemoryConsumer {

    private final Config config;
    private long consumptionSize;

    public MultiVersionTxStateMemoryConsumer(Config config) {
        this.config = config;
    }

    @Override
    public void initialize() {
        consumptionSize = config.get(multi_version_deletion_additional_reservation_size);
    }

    @Override
    public void consume(MemoryTracker memoryTracker) {
        memoryTracker.allocateHeap(consumptionSize);
    }
}

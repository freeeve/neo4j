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

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.multi_version_deletion_additional_reservation_size;

import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.memory.DefaultScopedMemoryTracker;
import org.neo4j.memory.EmptyMemoryTracker;

class MultiVersionTxStateMemoryConsumerTest {

    @Test
    void consumeConfiguredMemory() {
        Config config = Config.newBuilder()
                .set(multi_version_deletion_additional_reservation_size, 10L)
                .build();
        DefaultScopedMemoryTracker memoryTracker = new DefaultScopedMemoryTracker(EmptyMemoryTracker.INSTANCE);

        var memoryConsumer = new MultiVersionTxStateMemoryConsumer(config);
        memoryConsumer.initialize();

        memoryConsumer.consume(memoryTracker);
        assertThat(memoryTracker.estimatedHeapMemory()).isEqualTo(10);

        config.set(multi_version_deletion_additional_reservation_size, 20L);
        memoryConsumer.initialize();

        memoryConsumer.consume(memoryTracker);
        assertThat(memoryTracker.estimatedHeapMemory()).isEqualTo(30);
    }
}

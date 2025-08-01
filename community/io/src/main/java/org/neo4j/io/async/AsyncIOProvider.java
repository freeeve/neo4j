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
package org.neo4j.io.async;

import org.neo4j.annotations.service.Service;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.service.PrioritizedService;
import org.neo4j.service.Services;

@Service
public interface AsyncIOProvider extends PrioritizedService {

    AsyncBlockAccessor createAsyncBlockAccessor(
            int queueSize,
            AsyncCompletionHandler completionHandler,
            AsyncFailureHandler failureHandler,
            MemoryTracker memoryTracker);

    static AsyncIOProvider getInstance() {
        return AsyncIOProviderHolder.ASYNC_IO_PROVIDER;
    }

    final class AsyncIOProviderHolder {
        private static final AsyncIOProvider ASYNC_IO_PROVIDER = loadProvider();

        private static AsyncIOProvider loadProvider() {
            return Services.loadByPriority(AsyncIOProvider.class)
                    .orElseThrow(
                            () -> new IllegalStateException("Failed to load instance of " + AsyncIOProvider.class));
        }
    }
}

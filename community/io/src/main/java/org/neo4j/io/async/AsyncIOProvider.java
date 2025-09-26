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

import static org.neo4j.io.async.IllegalStateExceptionFailureHandler.ILLEGAL_STATE_HANDLER;
import static org.neo4j.util.FeatureToggles.flag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.neo4j.annotations.service.Service;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.service.PrioritizedService;
import org.neo4j.service.Services;

@Service
public interface AsyncIOProvider extends PrioritizedService {

    boolean PRINT_SERVICE_LOADER_STACK_TRACES = flag(AsyncIOProvider.class, "printServiceLoaderStackTraces", false);

    AsyncBlockAccessor createAsyncBlockAccessor(
            int queueSize,
            AsyncCompletionHandler completionHandler,
            AsyncFailureHandler failureHandler,
            MemoryTracker memoryTracker);

    String describe();

    static AsyncIOProvider getInstance() {
        return AsyncIOProviderHolder.ASYNC_IO_PROVIDER;
    }

    final class AsyncIOProviderHolder {
        private static final AsyncIOProvider ASYNC_IO_PROVIDER = loadProvider();

        private static AsyncIOProvider loadProvider() {
            List<AsyncIOProvider> availableIOProviders = new ArrayList<>(Services.loadAll(AsyncIOProvider.class));
            availableIOProviders.sort(Comparator.comparingInt(AsyncIOProvider::getPriority));
            for (AsyncIOProvider provider : availableIOProviders) {
                try {
                    createTestAccessor(provider);
                    return provider;
                } catch (Throwable t) {
                    if (PRINT_SERVICE_LOADER_STACK_TRACES) {
                        t.printStackTrace();
                    }
                }
            }
            throw new IllegalStateException(
                    new IllegalStateException("Failed to load instance of " + AsyncIOProvider.class));
        }

        private static void createTestAccessor(AsyncIOProvider provider) {
            try (AsyncBlockAccessor testAccessor = provider.createAsyncBlockAccessor(
                    128,
                    AsyncCompletionHandler.EMPTY_COMPLETION_HANDLER,
                    ILLEGAL_STATE_HANDLER,
                    EmptyMemoryTracker.INSTANCE)) {
                // try to create accessor to make sure we have all the access to call the native methods for async
                // provider if that is available
            }
        }
    }
}

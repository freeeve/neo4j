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
package org.neo4j.storageengine.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.neo4j.exceptions.KernelException;
import org.neo4j.exceptions.UnderlyingStorageException;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.storageengine.api.IndexUpdateListener;
import org.neo4j.storageengine.api.IndexUpdatesListener;
import org.neo4j.util.concurrent.AsyncApply;
import org.neo4j.util.concurrent.Work;
import org.neo4j.util.concurrent.WorkSync;

public class IndexUpdatesWorkSync {
    private final WorkSync<IndexUpdateListener, IndexUpdatesWork> workSync;
    private final IndexUpdateListener listener;
    private final boolean parallelApply;

    /**
     * @param parallelApply if {@code false} the updates from multiple concurrent applying transactions are work-synced where one thread
     * will end up applying all the updates. Otherwise if {@code true} each thread will apply their updates itself, with the "parallel" note
     * passed down to the updaters to arrange for this fact.
     */
    public IndexUpdatesWorkSync(IndexUpdateListener listener, boolean parallelApply) {
        this.listener = listener;
        this.parallelApply = parallelApply;
        this.workSync = parallelApply ? null : new WorkSync<>(listener);
    }

    public Batch newBatch(CursorContext cursorContext) {
        return new Batch(cursorContext);
    }

    public class Batch implements IndexUpdatesListener {
        private final List<IndexEntryUpdate> updates = new ArrayList<>();
        private final CursorContext cursorContext;
        private AsyncApply apply;

        public Batch(CursorContext cursorContext) {
            this.cursorContext = cursorContext;
        }

        /**
         * {@inheritDoc}
         * <p>
         * When applying the updates later during {@link #close()},
         * elements from the {@code indexUpdates} list will be nulled while iterating over the list.
         * This is to reduce memory retention.
         */
        @Override
        public void indexUpdate(IndexEntryUpdate indexUpdate) {
            updates.add(indexUpdate);
        }

        @Override
        public void close() throws IOException {
            try {
                if (apply == null) {
                    apply();
                }
                apply.await();
            } catch (ExecutionException e) {
                throw wrapExecutionException(e);
            }
        }

        private IOException wrapExecutionException(ExecutionException e) {
            return e.getCause() instanceof IOException ioe ? ioe : new IOException(e.getCause());
        }

        private void apply() throws IOException, ExecutionException {
            if (!updates.isEmpty()) {
                if (parallelApply) {
                    // Just skip the work-sync if this is parallel apply and instead update straight in
                    try {
                        sortUpdatesByIndex();
                        listener.applyUpdates(listNullingIterator(updates), cursorContext, true);
                    } catch (KernelException e) {
                        throw new IOException(e);
                    }
                } else {
                    workSync.apply(new IndexUpdatesWork(listNullingIterator(updates), cursorContext));
                }
            }
            apply = AsyncApply.EMPTY;
        }

        @Override
        public void applyAsync() throws IOException {
            if (apply != null) {
                throw new IllegalStateException("Already applied");
            }

            if (parallelApply) {
                try {
                    apply();
                } catch (ExecutionException e) {
                    throw wrapExecutionException(e);
                }
                return;
            }
            apply = updates.isEmpty()
                    ? AsyncApply.EMPTY
                    : workSync.applyAsync(new IndexUpdatesWork(listNullingIterator(updates), cursorContext));
        }

        private void sortUpdatesByIndex() {
            updates.sort((o1, o2) -> {
                // It doesn't matter which individual order the updates are in, as long as they are sorted by index key.
                // In fact they can't be sorted on their values because they aren't materialized yet.
                long id1 = o1.indexKey().getId();
                long id2 = o2.indexKey().getId();
                return Long.compare(id1, id2);
            });
        }
    }

    /**
     * Combines index updates from multiple transactions into one bigger job.
     */
    private static class IndexUpdatesWork implements Work<IndexUpdateListener, IndexUpdatesWork> {
        record OneWork(Iterator<IndexEntryUpdate> updates, CursorContext cursorContext) {}

        private final List<OneWork> works = new ArrayList<>(1);

        IndexUpdatesWork(Iterator<IndexEntryUpdate> updates, CursorContext cursorContext) {
            works.add(new OneWork(updates, cursorContext));
        }

        @Override
        public IndexUpdatesWork combine(IndexUpdatesWork work) {
            works.addAll(work.works);
            return this;
        }

        @Override
        public void apply(IndexUpdateListener material) {
            try {
                for (OneWork work : works) {
                    material.applyUpdates(work.updates, work.cursorContext, false);
                }
            } catch (IOException | KernelException e) {
                throw new UnderlyingStorageException(e);
            }
        }
    }

    /**
     * The IndexEntryUpdate instances are lazy in that they contain suppliers for the actual values.
     * Those values are cached though (because one "value" can be used in multiple index updates) and so they build
     * up over time when applying index updates. To keep memory retention low this index update iterator
     * nulls out the items it has seen. This allows the actual {@link org.neo4j.storageengine.api.ValueIndexEntryUpdate}
     * instances to be garbage collected, and eventually the value suppliers (at some point containing actual values).
     */
    private static <T> Iterator<T> listNullingIterator(List<T> list) {
        return new Iterator<>() {
            private final Iterator<T> delegate = list.iterator();
            private int index = 0;

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
                T next = delegate.next();
                list.set(index++, null);
                return next;
            }
        };
    }
}

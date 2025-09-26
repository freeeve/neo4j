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
package org.neo4j.io.pagecache.tracing.async;

import org.neo4j.io.pagecache.tracing.AutoCloseablePageCacheTracerEvent;

public interface AsyncFlushCompletion extends AutoCloseablePageCacheTracerEvent {
    AsyncFlushCompletion NULL = new AsyncFlushCompletion() {
        @Override
        public void addBytesWritten(int bytes) {}

        @Override
        public void addPagesCompleted(int pageCount) {}

        @Override
        public void reportIO(int completedIOs) {}

        @Override
        public void reset() {}

        @Override
        public long pagesFlushed() {
            return 0;
        }

        @Override
        public long ioPerformed() {
            return 0;
        }

        @Override
        public long getLocalBytesWritten() {
            return 0;
        }

        @Override
        public void close() {}
    };

    void addBytesWritten(int bytes);

    void addPagesCompleted(int pageCount);

    void reportIO(int completedIOs);

    void reset();

    long pagesFlushed();

    long ioPerformed();

    long getLocalBytesWritten();
}

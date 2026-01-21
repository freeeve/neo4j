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
package org.neo4j.internal.id.indexed;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.context.OldestTransactionIdFactory;
import org.neo4j.io.pagecache.context.VersionContext;

public class LockedPages {

    static final LockedPages EMPTY_LOCKED_PAGES = new EmptyLockedPages();

    private final ConcurrentMap<Long, Long> lockedPageRanges;

    public LockedPages() {
        this(new ConcurrentHashMap<>());
    }

    public LockedPages(ConcurrentHashMap<Long, Long> lockedPageRanges) {
        this.lockedPageRanges = lockedPageRanges;
    }

    public boolean add(long pageId, CursorContext cursorContext) {
        var replaceWitness = new MutableBoolean(false);
        lockedPageRanges.compute(pageId, (k, pageBoundary) -> {
            if (pageBoundary != null && pageBoundary >= boundary(cursorContext)) {
                return pageBoundary;
            }
            replaceWitness.setTrue();
            return Long.MAX_VALUE;
        });
        return replaceWitness.booleanValue();
    }

    public void remove(long pageId, CursorContext context) {
        lockedPageRanges.replace(pageId, boundary(context));
    }

    public void remove(LongSet pageIds, CursorContext cursorContext) {
        long boundary = boundary(cursorContext);
        pageIds.forEach(id -> lockedPageRanges.replace(id, boundary));
    }

    public void maintenance(OldestTransactionIdFactory oldestTransactionIdFactory) {
        lockedPageRanges.values().removeIf(boundary -> boundary < oldestTransactionIdFactory.oldestTransactionId());
    }

    private static long boundary(CursorContext context) {
        VersionContext versionContext = context.getVersionContext();
        return Math.max(versionContext.highestClosed(), versionContext.committingTransactionId());
    }

    private static class EmptyLockedPages extends LockedPages {
        @Override
        public boolean add(long pageId, CursorContext cursorContext) {
            return true;
        }

        @Override
        public void remove(long pageId, CursorContext context) {}

        @Override
        public void maintenance(OldestTransactionIdFactory oldestTransactionIdFactory) {}
    }
}

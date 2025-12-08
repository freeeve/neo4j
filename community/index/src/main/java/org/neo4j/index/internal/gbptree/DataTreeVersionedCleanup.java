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
package org.neo4j.index.internal.gbptree;

import static org.neo4j.index.internal.gbptree.CleanupTree.cleanLevels;

import java.io.IOException;
import java.util.function.LongConsumer;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.CursorContext;

/**
 * This is a tree write operation that releases all pages of the tree with the current committing transaction id. The
 * pages are only reused once they are no longer visible (oldest visible transaction > released version). It is used when deleting
 * a root key in a multi versioned multi root tree.
 */
public class DataTreeVersionedCleanup<K, V> implements TreeWriteOperation<K, V> {
    @Override
    public boolean run(
            Layout<K, V> layout,
            InternalAccess<K, V> internalAccess,
            PageCursor cursor,
            StructurePropagation<K> structurePropagation,
            long stableGeneration,
            long unstableGeneration,
            CursorContext cursorContext,
            LongConsumer rootSetter,
            IdProvider freeList)
            throws IOException {
        if (!internalAccess.coordination().pessimistic()) {
            return false;
        }

        // cleanup all levels first
        cleanLevels(
                internalAccess,
                cursor,
                stableGeneration,
                unstableGeneration,
                cursorContext,
                freeList::releaseIdWithVersion);

        // lastly release root id too
        freeList.releaseIdWithVersion(
                stableGeneration,
                unstableGeneration,
                cursor.getCurrentPageId(),
                CursorCreator.bind(cursor),
                cursorContext);

        return true;
    }
}

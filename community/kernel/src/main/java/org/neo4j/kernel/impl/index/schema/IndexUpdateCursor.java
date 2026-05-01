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
package org.neo4j.kernel.impl.index.schema;

import static org.neo4j.kernel.impl.index.schema.IndexUpdateStorage.STOP_TYPE;

import java.io.Closeable;
import org.neo4j.index.internal.gbptree.Layout;
import org.neo4j.io.pagecache.PageCursor;

/**
 * Cursor over serialized index update instructions.
 * Reads the updates in sequential order. Field instances are reused, so consumer is responsible for creating copies if result needs to be cached.
 */
public class IndexUpdateCursor<KEY> implements Closeable {
    private final PageCursor cursor;
    private final Layout<KEY, NullValue> layout;

    private boolean addition;
    private long version;
    private final KEY key;

    IndexUpdateCursor(PageCursor cursor, Layout<KEY, NullValue> layout) {
        this.cursor = cursor;
        this.layout = layout;
        this.key = layout.newKey();
    }

    public boolean next() {
        byte updateModeType = cursor.getByte();
        if (updateModeType == STOP_TYPE) {
            return false;
        }

        addition = updateModeType == 1;
        version = cursor.getLong();
        BlockEntry.read(cursor, layout, key);
        return true;
    }

    public KEY key() {
        return key;
    }

    public boolean addition() {
        return addition;
    }

    public long version() {
        return version;
    }

    @Override
    public void close() {
        cursor.close();
    }
}

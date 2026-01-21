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
package org.neo4j.kernel.impl.store.cursor;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.neo4j.util.FeatureToggles.flag;

import java.util.Arrays;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.storageengine.api.cursor.CursorType;
import org.neo4j.storageengine.api.cursor.StoreCursors;

public abstract class AbstractCachedStoreCursors implements StoreCursors {
    protected static final boolean CHECK_READ_CURSORS =
            flag(AbstractCachedStoreCursors.class, "CHECK_READ_CURSORS", false);
    protected CursorContext cursorContext;
    private final int numTypes;

    private final PageCursor[] cursorsByType;
    private final PageCursor[] noCurrentTransactionCursorsByType;

    public AbstractCachedStoreCursors(CursorContext cursorContext, int numTypes) {
        this.cursorContext = cursorContext;
        this.numTypes = numTypes;
        this.cursorsByType = createEmptyCursorArray();
        this.noCurrentTransactionCursorsByType = createEmptyCursorArray();
    }

    @Override
    public void reset(CursorContext cursorContext) {
        this.cursorContext = cursorContext;
        resetCursors();
    }

    protected void resetCursors() {
        reset(cursorsByType);
        reset(noCurrentTransactionCursorsByType);
    }

    private void reset(PageCursor[] cursors) {
        boolean clearCursors = false;
        for (int i = 0; i < cursors.length; i++) {
            PageCursor pageCursor = cursors[i];
            if (pageCursor != null) {
                clearCursors = true;
                if (CHECK_READ_CURSORS) {
                    checkReadCursor(pageCursor, i, EMPTY);
                }
                pageCursor.close();
            }
        }
        if (clearCursors) {
            Arrays.fill(cursors, null);
        }
    }

    @Override
    public PageCursor readCursor(CursorType type, boolean includeChangesFromThisTransaction) {
        if (includeChangesFromThisTransaction) {
            return readCursorFrom(type, cursorsByType, cursorContext);
        }
        return readCursorFrom(type, noCurrentTransactionCursorsByType, cursorContext.noCurrentTransactionContext());
    }

    private PageCursor readCursorFrom(CursorType type, PageCursor[] cursorsArray, CursorContext context) {
        int value = type.value();
        var cursor = cursorsArray[value];
        if (cursor != null) {
            return cursor;
        }
        var newCursor = createReadCursor(type, context);
        cursorsArray[value] = newCursor;
        return newCursor;
    }

    protected abstract PageCursor createReadCursor(CursorType type, CursorContext cursorContext);

    @Override
    public void close() {
        resetCursors();
    }

    private PageCursor[] createEmptyCursorArray() {
        return new PageCursor[numTypes];
    }

    protected static void checkReadCursor(PageCursor pageCursor, int type, String prefix) {
        if (pageCursor.getRawCurrentFile() == null) {
            throw new IllegalStateException("%sRead cursor %s with type: %d is closed outside of owning store cursors."
                    .formatted(prefix, ReflectionToStringBuilder.toString(pageCursor), type));
        }
    }
}

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

import static org.neo4j.index.internal.gbptree.GenerationSafePointer.FIRST_STABLE_GENERATION;
import static org.neo4j.index.internal.gbptree.GenerationSafePointer.FIRST_UNSTABLE_GENERATION;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.neo4j.index.internal.gbptree.FreeListIdProvider.FreelistMetaData;
import org.neo4j.index.internal.gbptree.FreeListIdProvider.FreelistPositions;
import org.neo4j.io.pagecache.PageCursor;

/**
 * Tree state is defined as top level tree metadata which changes as the tree and its constructs changes, such as:
 * <ul>
 * <li>stable/unstable generation numbers</li>
 * <li>root id, the page id containing the root of the tree</li>
 * <li>last id, the page id which is the highest allocated in the store</li>
 * <li>pointers into free-list (page id + offset)</li>
 * </ul>
 * This class also knows how to
 * {@link #write(PageCursor, long, long, long, long, FreelistMetaData, boolean) write} and
 * {@link #read(PageCursor, boolean) read} tree state to and from a {@link PageCursor}, although it doesn't care where
 * in the store that is.
 */
class TreeState {
    private static final byte CLEAN_BYTE = 0x01;
    private static final byte DIRTY_BYTE = 0x00;
    private static final int MISSING_INT = -1;
    private static final long MISSING_LONG = -1;
    private static final int BASE_FIELD_SIZE = Integer.BYTES * 2 + Long.BYTES + Long.BYTES + Byte.BYTES;
    private static final int DEFAULT_FIELD_SIZE = BASE_FIELD_SIZE + FreelistMetaData.NON_VERSIONED_SIZE;
    private static final int MULTI_VERSION_FIELD_SIZE = BASE_FIELD_SIZE + FreelistMetaData.VERSIONED_SIZE;

    /**
     * Page id this tree state has been read from.
     */
    private final long pageId;

    /**
     * Stable generation of the tree.
     */
    private final long stableGeneration;

    /**
     * Unstable generation of the tree.
     */
    private final long unstableGeneration;

    /**
     * Page id which is the root of the tree.
     */
    private final long rootId;

    /**
     * Generation of {@link #rootId}.
     */
    private final long rootGeneration;

    /**
     * State of the free list
     */
    private final FreelistMetaData freelistMetaData;

    /**
     * Due to writing with potential concurrent page flushing tree state is written twice, the second
     * state acting as checksum. If both states match this variable should be set to {@code true},
     * otherwise to {@code false}.
     */
    private boolean valid;

    /**
     * Is tree clean or dirty. Clean means it was closed without any non-checkpointed changes.
     */
    private final boolean clean;

    TreeState(
            long pageId,
            long stableGeneration,
            long unstableGeneration,
            long rootId,
            long rootGeneration,
            FreelistMetaData freelistMetaData,
            boolean clean,
            boolean valid) {
        this.pageId = pageId;
        this.stableGeneration = stableGeneration;
        this.unstableGeneration = unstableGeneration;
        this.rootId = rootId;
        this.rootGeneration = rootGeneration;
        this.freelistMetaData = freelistMetaData;
        this.clean = clean;
        this.valid = valid;
    }

    long pageId() {
        return pageId;
    }

    long stableGeneration() {
        return stableGeneration;
    }

    long unstableGeneration() {
        return unstableGeneration;
    }

    long rootId() {
        return rootId;
    }

    long rootGeneration() {
        return rootGeneration;
    }

    FreelistMetaData freelistMetaData() {
        return freelistMetaData;
    }

    boolean isValid() {
        return valid;
    }

    /**
     * Simulates the tree state before first checkpoint
     */
    static TreeState firstState(boolean multiVersioned) {
        FreelistPositions firstPositions = new FreelistPositions(MISSING_LONG, MISSING_LONG, MISSING_INT, MISSING_INT);
        FreelistMetaData freelistMetadata = multiVersioned
                ? FreelistMetaData.versioned(MISSING_LONG, firstPositions, firstPositions)
                : FreelistMetaData.nonVersioned(MISSING_LONG, firstPositions);
        return new TreeState(
                MISSING_LONG,
                FIRST_STABLE_GENERATION,
                FIRST_UNSTABLE_GENERATION,
                MISSING_LONG,
                MISSING_LONG,
                freelistMetadata,
                false,
                true);
    }

    /**
     * Size of one set of tree-state fields.
     */
    static int fieldsSize(boolean multiVersioned) {
        return multiVersioned ? MULTI_VERSION_FIELD_SIZE : DEFAULT_FIELD_SIZE;
    }

    /**
     * Size of a tree-state altogether, which consists of two sets of tree-state fields.
     */
    static int size(boolean multiVersioned) {
        return fieldsSize(multiVersioned) * 2;
    }

    /**
     * Writes provided tree state to {@code cursor} at its current offset. Two versions of the state
     * are written after each other, the second one acting as checksum for the first, see {@link #valid} field.
     *
     * @param cursor {@link PageCursor} to write into, at its current offset.
     * @param stableGeneration stable generation.
     * @param unstableGeneration unstable generation.
     * @param rootId root id.
     * @param rootGeneration root generation.
     * @param clean is tree clean or dirty
     */
    static void write(
            PageCursor cursor,
            long stableGeneration,
            long unstableGeneration,
            long rootId,
            long rootGeneration,
            FreelistMetaData freelistMetaData,
            boolean clean) {
        GenerationSafePointer.assertGenerationOnWrite(stableGeneration);
        GenerationSafePointer.assertGenerationOnWrite(unstableGeneration);

        writeStateOnce(
                cursor,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                freelistMetaData,
                clean); // Write state
        writeStateOnce(
                cursor,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                freelistMetaData,
                clean); // Write checksum
    }

    /**
     * Reads tree state from {@code cursor} at its current offset. If checksum matches then {@link #valid}
     * is set to {@code true}, otherwise {@code false}.
     *
     * @param cursor {@link PageCursor} to read tree state from, at its current offset.
     * @return {@link TreeState} instance containing read tree state.
     */
    static TreeState read(PageCursor cursor, boolean multiVersioned) throws IOException {
        byte[] buffer = new byte[size(multiVersioned)];
        cursor.getBytes(buffer);
        return read(cursor.getCurrentPageId(), ByteBuffer.wrap(buffer).order(cursor.getByteOrder()), multiVersioned);
    }

    /**
     * @see #read(PageCursor, boolean)
     *
     * @param pageId current page
     * @param buffer temporary buffer to use
     * @return {@link TreeState} instance containing read tree state.
     */
    static TreeState read(long pageId, ByteBuffer buffer, boolean multiVersioned) {
        TreeState state = readStateOnce(pageId, buffer, multiVersioned);
        TreeState checksumState = readStateOnce(pageId, buffer, multiVersioned);

        boolean valid = state.equals(checksumState);

        boolean isEmpty = state.isEmpty();
        valid &= !isEmpty;

        return state.setValid(valid);
    }

    private TreeState setValid(boolean valid) {
        this.valid = valid;
        return this;
    }

    boolean isEmpty() {
        return stableGeneration == 0L && unstableGeneration == 0L && rootId == 0L && freelistMetaData.isEmpty();
    }

    private static TreeState readStateOnce(long pageId, ByteBuffer buffer, boolean multiVersioned) {
        int expectedFieldSize = fieldsSize(multiVersioned);
        assert buffer.remaining() >= expectedFieldSize
                : "Not enough data. Remaining " + buffer.remaining() + ", expected " + expectedFieldSize;
        long stableGeneration = buffer.getInt() & GenerationSafePointer.GENERATION_MASK;
        long unstableGeneration = buffer.getInt() & GenerationSafePointer.GENERATION_MASK;
        long rootId = buffer.getLong();
        long rootGeneration = buffer.getLong();
        FreelistMetaData freelistMetaData = FreelistMetaData.read(buffer, multiVersioned);
        boolean clean = buffer.get() == CLEAN_BYTE;
        return new TreeState(
                pageId, stableGeneration, unstableGeneration, rootId, rootGeneration, freelistMetaData, clean, true);
    }

    private static void writeStateOnce(
            PageCursor cursor,
            long stableGeneration,
            long unstableGeneration,
            long rootId,
            long rootGeneration,
            FreelistMetaData freelistMetaData,
            boolean clean) {
        cursor.putInt((int) stableGeneration);
        cursor.putInt((int) unstableGeneration);
        cursor.putLong(rootId);
        cursor.putLong(rootGeneration);
        freelistMetaData.write(cursor);
        cursor.putByte(clean ? CLEAN_BYTE : DIRTY_BYTE);
    }

    @Override
    public String toString() {
        return String.format(
                "pageId=%d, stableGeneration=%d, unstableGeneration=%d, rootId=%d, rootGeneration=%d, "
                        + "freelistMetadata=%s, clean=%b, valid=%b",
                pageId, stableGeneration, unstableGeneration, rootId, rootGeneration, freelistMetaData, clean, valid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TreeState treeState = (TreeState) o;
        return pageId == treeState.pageId
                && stableGeneration == treeState.stableGeneration
                && unstableGeneration == treeState.unstableGeneration
                && rootId == treeState.rootId
                && rootGeneration == treeState.rootGeneration
                && freelistMetaData.equals(treeState.freelistMetaData)
                && clean == treeState.clean
                && valid == treeState.valid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                pageId, stableGeneration, unstableGeneration, rootId, rootGeneration, freelistMetaData, clean, valid);
    }

    public boolean isClean() {
        return clean;
    }
}

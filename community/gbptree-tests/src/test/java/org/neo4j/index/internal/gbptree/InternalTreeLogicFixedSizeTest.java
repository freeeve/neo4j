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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.neo4j.index.internal.gbptree.SimpleLongLayout.longLayout;

import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InternalTreeLogicFixedSizeTest extends InternalTreeLogicTestBase<MutableLong, MutableLong> {
    private final SimpleLongLayout layout = longLayout().build();

    @Override
    protected ValueMerger<MutableLong, MutableLong> getAdder() {
        return (existingKey, newKey, base, add) -> {
            base.add(add.longValue());
            return ValueMerger.MergeResult.MERGED;
        };
    }

    @Override
    protected LeafNodeBehaviour<MutableLong, MutableLong> getLeaf(
            int pageSize,
            Layout<MutableLong, MutableLong> layout,
            OffloadStore<MutableLong, MutableLong> offloadStore) {
        return new LeafNodeFixedSize<>(pageSize, layout);
    }

    @Override
    protected InternalNodeBehaviour<MutableLong> getInternal(
            int pageSize,
            Layout<MutableLong, MutableLong> layout,
            OffloadStore<MutableLong, MutableLong> offloadStore) {
        return new InternalNodeFixedSize<>(pageSize, layout);
    }

    @Override
    protected TestLayout<MutableLong, MutableLong> getLayout() {
        return layout;
    }

    @ParameterizedTest
    @MethodSource("generators")
    void shouldCreateNewVersionWhenInsertInStableInternal(
            String name, GenerationManager generationManager, boolean isCheckpointing) throws Exception {
        assumeTrue(isCheckpointing, "No checkpointing, no successor");

        // GIVEN
        initialize();
        long someHighMultiplier = 1000;
        for (int i = 0; numberOfRootSplits < 2; i++) {
            long seed = i * someHighMultiplier;
            insert(key(seed), value(seed));
        }
        long rootAfterInitialData = root.id();
        root.goTo(readCursor);
        assertEquals(1, keyCount());
        long leftInternal = childAt(readCursor, 0, stableGeneration, unstableGeneration);
        long rightInternal = childAt(readCursor, 1, stableGeneration, unstableGeneration);
        assertSiblings(leftInternal, rightInternal, TreeNodeUtil.NO_NODE_FLAG);
        goTo(readCursor, leftInternal);
        int leftInternalKeyCount = keyCount();
        assertThat(TreeNodeUtil.isInternal(readCursor)).isTrue();
        long leftLeaf = childAt(readCursor, 0, stableGeneration, unstableGeneration);
        goTo(readCursor, leftLeaf);
        MutableLong firstKeyInLeaf = keyAt(0, false);
        long seedOfFirstKeyInLeaf = getSeed(firstKeyInLeaf);

        // WHEN
        generationManager.checkpoint();
        long targetLastId =
                id.lastId() + 3; /*one for successor in leaf, one for split leaf, one for successor in internal*/
        for (int i = 0; id.lastId() < targetLastId; i++) {
            insert(key(seedOfFirstKeyInLeaf + i), value(seedOfFirstKeyInLeaf + i));
            assertThat(structurePropagation.hasRightKeyInsert).isFalse(); // there should be no root split
        }

        // THEN
        // root hasn't been split further
        assertThat(root.id()).isEqualTo(rootAfterInitialData);

        root.goTo(readCursor);
        long successorLeftInternal = id.lastId();
        assertThat(childAt(readCursor, 0, stableGeneration, unstableGeneration)).isEqualTo(successorLeftInternal);
        goTo(readCursor, successorLeftInternal);
        int successorLeftInternalKeyCount = keyCount();
        // there's a successor to left internal w/ one more key in
        assertEquals(leftInternalKeyCount + 1, successorLeftInternalKeyCount);

        // and left internal points to the successor
        goTo(readCursor, leftInternal);
        assertThat(successor(readCursor, stableGeneration, unstableGeneration)).isEqualTo(successorLeftInternal);
        assertSiblings(successorLeftInternal, rightInternal, TreeNodeUtil.NO_NODE_FLAG);
    }
}

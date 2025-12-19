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
package org.neo4j.kernel.impl.newapi;

import org.neo4j.internal.kernel.api.NodeValueIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.RelationshipValueIndexCursor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.StorageNodeCursor;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;
import org.neo4j.storageengine.api.StorageRelationshipTraversalCursor;
import org.neo4j.storageengine.api.cursor.StoreCursors;

public interface DefaultCursorFactory {

    DefaultNodeCursor nodeCursor(
            CursorPool<DefaultNodeCursor> pool,
            StorageNodeCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    DefaultNodeCursor fullAccessNodeCursor(CursorPool<DefaultNodeCursor> pool, StorageNodeCursor storeCursor);

    DefaultRelationshipScanCursor relationshipScanCursor(
            CursorPool<DefaultRelationshipScanCursor> pool,
            StorageRelationshipScanCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    DefaultRelationshipScanCursor fullAccessRelationshipScanCursor(
            CursorPool<DefaultRelationshipScanCursor> pool, StorageRelationshipScanCursor storeCursor);

    DefaultRelationshipTraversalCursor relationshipTraversalCursor(
            CursorPool<DefaultRelationshipTraversalCursor> pool,
            StorageRelationshipTraversalCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    DefaultRelationshipTraversalCursor fullAccessRelationshipTraversalCursor(
            CursorPool<DefaultRelationshipTraversalCursor> pool, StorageRelationshipTraversalCursor storeCursor);

    PropertyCursor propertyCursor(
            CursorPool<TraceablePropertyCursor> pool,
            StoragePropertyCursor storeCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    PropertyCursor fullAccessPropertyCursor(
            CursorPool<TraceablePropertyCursor> pool, StoragePropertyCursor storeCursor);

    NodeValueIndexCursor nodeValueIndexCursor(
            CursorPool<DefaultNodeValueIndexCursor> pool,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    DefaultNodeLabelIndexCursor nodeLabelIndexCursor(
            CursorPool<DefaultNodeLabelIndexCursor> pool,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    DefaultNodeLabelIndexCursor fullAccessNodeLabelIndexCursor(CursorPool<DefaultNodeLabelIndexCursor> pool);

    RelationshipValueIndexCursor relationshipValueIndexCursor(
            CursorPool<DefaultRelationshipValueIndexCursor> pool,
            DefaultRelationshipScanCursor relationshipScanCursor,
            InternalCursorFactory internalCursors,
            boolean applyAccessModeToTxState);

    DefaultNodeBasedRelationshipTypeIndexCursor nodeBasedRelationshipTypeIndexCursor(
            CursorPool<DefaultNodeBasedRelationshipTypeIndexCursor> pool,
            DefaultNodeCursor nodeCursor,
            DefaultRelationshipTraversalCursor relationshipTraversalCursor);

    DefaultNodeBasedRelationshipTypeIndexCursor fullAccessNodeBasedRelationshipTypeIndexCursor(
            CursorPool<DefaultNodeBasedRelationshipTypeIndexCursor> pool,
            DefaultNodeCursor nodeCursor,
            DefaultRelationshipTraversalCursor relationshipTraversalCursor);

    DefaultRelationshipBasedRelationshipTypeIndexCursor relationshipBasedRelationshipTypeIndexCursor(
            CursorPool<DefaultRelationshipBasedRelationshipTypeIndexCursor> pool,
            DefaultRelationshipScanCursor relationshipScanCursor,
            boolean applyAccessModeToTxState);

    DefaultRelationshipBasedRelationshipTypeIndexCursor fullAccessRelationshipBasedRelationshipTypeIndexCursor(
            CursorPool<DefaultRelationshipBasedRelationshipTypeIndexCursor> pool,
            DefaultRelationshipScanCursor relationshipScanCursor);

    InternalCursorFactory internalCursors(
            StorageReader storageReader,
            StoreCursors storeCursors,
            CursorContext cursorContext,
            MemoryTracker memoryTracker,
            boolean applyAccessModeToTxState);

    DefaultCursorFactory DEFAULT = new DefaultCursorFactory() {

        @Override
        public DefaultNodeCursor nodeCursor(
                CursorPool<DefaultNodeCursor> pool,
                StorageNodeCursor storeCursor,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultNodeCursor(pool, storeCursor, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public FullAccessNodeCursor fullAccessNodeCursor(
                CursorPool<DefaultNodeCursor> pool, StorageNodeCursor storeCursor) {
            return new FullAccessNodeCursor(pool, storeCursor);
        }

        @Override
        public DefaultRelationshipScanCursor relationshipScanCursor(
                CursorPool<DefaultRelationshipScanCursor> pool,
                StorageRelationshipScanCursor storeCursor,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultRelationshipScanCursor(pool, storeCursor, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public FullAccessRelationshipScanCursor fullAccessRelationshipScanCursor(
                CursorPool<DefaultRelationshipScanCursor> pool, StorageRelationshipScanCursor storeCursor) {
            return new FullAccessRelationshipScanCursor(pool, storeCursor);
        }

        @Override
        public DefaultRelationshipTraversalCursor relationshipTraversalCursor(
                CursorPool<DefaultRelationshipTraversalCursor> pool,
                StorageRelationshipTraversalCursor storeCursor,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultRelationshipTraversalCursor(pool, storeCursor, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public FullAccessRelationshipTraversalCursor fullAccessRelationshipTraversalCursor(
                CursorPool<DefaultRelationshipTraversalCursor> pool, StorageRelationshipTraversalCursor storeCursor) {
            return new FullAccessRelationshipTraversalCursor(pool, storeCursor);
        }

        @Override
        public DefaultPropertyCursor propertyCursor(
                CursorPool<TraceablePropertyCursor> pool,
                StoragePropertyCursor storeCursor,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultPropertyCursor(pool, storeCursor, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public FullAccessPropertyCursor fullAccessPropertyCursor(
                CursorPool<TraceablePropertyCursor> pool, StoragePropertyCursor storeCursor) {
            return new FullAccessPropertyCursor(pool, storeCursor);
        }

        @Override
        public DefaultNodeValueIndexCursor nodeValueIndexCursor(
                CursorPool<DefaultNodeValueIndexCursor> pool,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultNodeValueIndexCursor(pool, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public DefaultNodeLabelIndexCursor nodeLabelIndexCursor(
                CursorPool<DefaultNodeLabelIndexCursor> pool,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultNodeLabelIndexCursor(pool, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public FullAccessNodeLabelIndexCursor fullAccessNodeLabelIndexCursor(
                CursorPool<DefaultNodeLabelIndexCursor> pool) {
            return new FullAccessNodeLabelIndexCursor(pool);
        }

        @Override
        public DefaultRelationshipValueIndexCursor relationshipValueIndexCursor(
                CursorPool<DefaultRelationshipValueIndexCursor> pool,
                DefaultRelationshipScanCursor relationshipScanCursor,
                InternalCursorFactory internalCursors,
                boolean applyAccessModeToTxState) {
            return new DefaultRelationshipValueIndexCursor(
                    pool, relationshipScanCursor, internalCursors, applyAccessModeToTxState);
        }

        @Override
        public DefaultNodeBasedRelationshipTypeIndexCursor nodeBasedRelationshipTypeIndexCursor(
                CursorPool<DefaultNodeBasedRelationshipTypeIndexCursor> pool,
                DefaultNodeCursor nodeCursor,
                DefaultRelationshipTraversalCursor relationshipTraversalCursor) {
            return new DefaultNodeBasedRelationshipTypeIndexCursor(pool, nodeCursor, relationshipTraversalCursor);
        }

        @Override
        public DefaultNodeBasedRelationshipTypeIndexCursor fullAccessNodeBasedRelationshipTypeIndexCursor(
                CursorPool<DefaultNodeBasedRelationshipTypeIndexCursor> pool,
                DefaultNodeCursor nodeCursor,
                DefaultRelationshipTraversalCursor relationshipTraversalCursor) {
            return new DefaultNodeBasedRelationshipTypeIndexCursor(pool, nodeCursor, relationshipTraversalCursor);
        }

        @Override
        public DefaultRelationshipBasedRelationshipTypeIndexCursor relationshipBasedRelationshipTypeIndexCursor(
                CursorPool<DefaultRelationshipBasedRelationshipTypeIndexCursor> pool,
                DefaultRelationshipScanCursor relationshipScanCursor,
                boolean applyAccessModeToTxState) {
            return new DefaultRelationshipBasedRelationshipTypeIndexCursor(
                    pool, relationshipScanCursor, applyAccessModeToTxState);
        }

        @Override
        public FullAccessRelationshipBasedRelationshipTypeIndexCursor
                fullAccessRelationshipBasedRelationshipTypeIndexCursor(
                        CursorPool<DefaultRelationshipBasedRelationshipTypeIndexCursor> pool,
                        DefaultRelationshipScanCursor relationshipScanCursor) {
            return new FullAccessRelationshipBasedRelationshipTypeIndexCursor(pool, relationshipScanCursor);
        }

        @Override
        public InternalCursorFactory internalCursors(
                StorageReader storageReader,
                StoreCursors storeCursors,
                CursorContext cursorContext,
                MemoryTracker memoryTracker,
                boolean applyAccessModeToTxState) {
            return new InternalCursorFactory(
                    storageReader, storeCursors, cursorContext, memoryTracker, applyAccessModeToTxState);
        }
    };
}

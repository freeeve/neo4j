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

import java.util.ArrayList;
import org.neo4j.configuration.Config;
import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.NodeValueIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.RelationshipScanCursor;
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor;
import org.neo4j.internal.kernel.api.RelationshipTypeIndexCursor;
import org.neo4j.internal.kernel.api.RelationshipValueIndexCursor;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.cursor.StoreCursors;

/**
 * Cursor factory which pools 1 cursor of each kind. Not thread-safe at all.
 */
public class DefaultPooledCursors extends DefaultCursors implements CursorFactory {
    protected final StorageReader storageReader;
    protected final StoreCursors storeCursors;
    private final StorageEngineIndexingBehaviour indexingBehaviour;
    protected final boolean applyAccessModeToTxState;
    private DefaultNodeCursor nodeCursor;
    private DefaultNodeCursor fullAccessNodeCursor;
    private DefaultRelationshipScanCursor relationshipScanCursor;
    private DefaultRelationshipScanCursor fullAccessRelationshipScanCursor;
    private DefaultRelationshipTraversalCursor relationshipTraversalCursor;
    private DefaultRelationshipTraversalCursor fullAccessRelationshipTraversalCursor;
    private DefaultPropertyCursor propertyCursor;
    private PropertyCursor fullAccessPropertyCursor;
    private DefaultNodeValueIndexCursor nodeValueIndexCursor;
    private DefaultNodeLabelIndexCursor nodeLabelIndexCursor;
    private DefaultRelationshipValueIndexCursor relationshipValueIndexCursor;
    private DefaultNodeLabelIndexCursor fullAccessNodeLabelIndexCursor;
    private InternalRelationshipTypeIndexCursor relationshipTypeIndexCursor;
    private InternalRelationshipTypeIndexCursor fullAccessRelationshipTypeIndexCursor;

    public DefaultPooledCursors(
            StorageReader storageReader,
            StoreCursors storeCursors,
            Config config,
            StorageEngineIndexingBehaviour indexingBehaviour,
            boolean applyAccessModeToTxState) {
        super(new ArrayList<>(), config);
        this.storageReader = storageReader;
        this.storeCursors = storeCursors;
        this.indexingBehaviour = indexingBehaviour;
        this.applyAccessModeToTxState = applyAccessModeToTxState;
    }

    @Override
    public DefaultNodeCursor allocateNodeCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (nodeCursor == null) {
            return trace(newNodeCursor(cursorContext, memoryTracker));
        }
        try {
            return acquire(nodeCursor);
        } finally {
            nodeCursor = null;
        }
    }

    protected DefaultNodeCursor newNodeCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new DefaultNodeCursor(
                this::accept,
                storageReader.allocateNodeCursor(cursorContext, storeCursors, memoryTracker),
                newInternalCursors(cursorContext, memoryTracker),
                applyAccessModeToTxState);
    }

    protected void accept(DefaultNodeCursor cursor) {
        if (nodeCursor != null) {
            nodeCursor.release();
        }
        cursor.removeTracer();
        nodeCursor = cursor;
    }

    @Override
    public DefaultNodeCursor allocateFullAccessNodeCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (fullAccessNodeCursor == null) {
            return trace(newFullNodeCursor(cursorContext, memoryTracker));
        }
        try {
            return acquire(fullAccessNodeCursor);
        } finally {
            fullAccessNodeCursor = null;
        }
    }

    protected DefaultNodeCursor newFullNodeCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new FullAccessNodeCursor(
                this::acceptFullAccess, storageReader.allocateNodeCursor(cursorContext, storeCursors, memoryTracker));
    }

    protected void acceptFullAccess(DefaultNodeCursor cursor) {
        if (fullAccessNodeCursor != null) {
            fullAccessNodeCursor.release();
        }
        cursor.removeTracer();
        fullAccessNodeCursor = cursor;
    }

    @Override
    public RelationshipScanCursor allocateRelationshipScanCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (relationshipScanCursor == null) {
            return trace(newRelationshipScanCursor(cursorContext, memoryTracker));
        }
        try {
            return acquire(relationshipScanCursor);
        } finally {
            relationshipScanCursor = null;
        }
    }

    protected DefaultRelationshipScanCursor newRelationshipScanCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new DefaultRelationshipScanCursor(
                this::accept,
                storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker),
                newInternalCursors(cursorContext, memoryTracker),
                applyAccessModeToTxState);
    }

    protected void accept(DefaultRelationshipScanCursor cursor) {
        if (relationshipScanCursor != null) {
            relationshipScanCursor.release();
        }
        cursor.removeTracer();
        relationshipScanCursor = cursor;
    }

    @Override
    public DefaultRelationshipScanCursor allocateFullAccessRelationshipScanCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (fullAccessRelationshipScanCursor == null) {
            return trace(newFullRelationshipScanCursor(cursorContext, memoryTracker));
        }

        try {
            return acquire(fullAccessRelationshipScanCursor);
        } finally {
            fullAccessRelationshipScanCursor = null;
        }
    }

    protected DefaultRelationshipScanCursor newFullRelationshipScanCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new FullAccessRelationshipScanCursor(
                this::acceptFullAccess,
                storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker));
    }

    private static <C extends TraceableCursor> C acquire(C cursor) {
        cursor.acquire();
        return cursor;
    }

    protected void acceptFullAccess(DefaultRelationshipScanCursor cursor) {
        if (fullAccessRelationshipScanCursor != null) {
            fullAccessRelationshipScanCursor.release();
        }
        cursor.removeTracer();
        fullAccessRelationshipScanCursor = cursor;
    }

    @Override
    public DefaultRelationshipTraversalCursor allocateRelationshipTraversalCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (relationshipTraversalCursor == null) {
            return trace(newTraversalCursor(cursorContext, memoryTracker));
        }

        try {
            return acquire(relationshipTraversalCursor);
        } finally {
            relationshipTraversalCursor = null;
        }
    }

    protected DefaultRelationshipTraversalCursor newTraversalCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new DefaultRelationshipTraversalCursor(
                this::accept,
                storageReader.allocateRelationshipTraversalCursor(cursorContext, storeCursors, memoryTracker),
                newInternalCursors(cursorContext, memoryTracker),
                applyAccessModeToTxState);
    }

    protected void accept(DefaultRelationshipTraversalCursor cursor) {
        if (relationshipTraversalCursor != null) {
            relationshipTraversalCursor.release();
        }
        cursor.removeTracer();
        relationshipTraversalCursor = cursor;
    }

    @Override
    public RelationshipTraversalCursor allocateFullAccessRelationshipTraversalCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (fullAccessRelationshipTraversalCursor == null) {
            return trace(newFullAccessRelationshipTraversalCursor(cursorContext, memoryTracker));
        }

        try {
            return acquire(fullAccessRelationshipTraversalCursor);
        } finally {
            fullAccessRelationshipTraversalCursor = null;
        }
    }

    protected DefaultRelationshipTraversalCursor newFullAccessRelationshipTraversalCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new FullAccessRelationshipTraversalCursor(
                this::acceptFullAccess,
                storageReader.allocateRelationshipTraversalCursor(cursorContext, storeCursors, memoryTracker));
    }

    protected void acceptFullAccess(DefaultRelationshipTraversalCursor cursor) {
        if (fullAccessRelationshipTraversalCursor != null) {
            fullAccessRelationshipTraversalCursor.release();
        }
        cursor.removeTracer();
        fullAccessRelationshipTraversalCursor = cursor;
    }

    @Override
    public PropertyCursor allocatePropertyCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (propertyCursor == null) {
            return trace((PropertyCursor) new DefaultPropertyCursor(
                    this::accept,
                    storageReader.allocatePropertyCursor(cursorContext, storeCursors, memoryTracker),
                    newInternalCursors(cursorContext, memoryTracker),
                    applyAccessModeToTxState));
        }
        try {
            return acquire(propertyCursor);
        } finally {
            propertyCursor = null;
        }
    }

    protected void accept(DefaultPropertyCursor cursor) {
        if (propertyCursor != null) {
            propertyCursor.release();
        }
        cursor.removeTracer();
        propertyCursor = cursor;
    }

    @Override
    public PropertyCursor allocateFullAccessPropertyCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (fullAccessPropertyCursor == null) {
            return trace((PropertyCursor) new FullAccessPropertyCursor(
                    this::acceptFullAccess,
                    storageReader.allocatePropertyCursor(cursorContext, storeCursors, memoryTracker)));
        }

        try {
            return acquire((TraceableCursor & PropertyCursor) fullAccessPropertyCursor);
        } finally {
            fullAccessPropertyCursor = null;
        }
    }

    protected void acceptFullAccess(DefaultPropertyCursor cursor) {
        if (fullAccessPropertyCursor != null) {
            ((TraceableCursor) fullAccessPropertyCursor).release();
        }
        cursor.removeTracer();
        fullAccessPropertyCursor = cursor;
    }

    @Override
    public NodeValueIndexCursor allocateNodeValueIndexCursor(CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (nodeValueIndexCursor == null) {
            return trace(new DefaultNodeValueIndexCursor(
                    this::accept, newInternalCursors(cursorContext, memoryTracker), applyAccessModeToTxState));
        }

        try {
            return acquire(nodeValueIndexCursor);
        } finally {
            nodeValueIndexCursor = null;
        }
    }

    protected void accept(DefaultNodeValueIndexCursor cursor) {
        if (nodeValueIndexCursor != null) {
            nodeValueIndexCursor.release();
        }
        cursor.removeTracer();
        nodeValueIndexCursor = cursor;
    }

    @Override
    public DefaultNodeLabelIndexCursor allocateNodeLabelIndexCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (nodeLabelIndexCursor == null) {
            return trace(new DefaultNodeLabelIndexCursor(
                    this::accept, newInternalCursors(cursorContext, memoryTracker), applyAccessModeToTxState));
        }

        try {
            return acquire(nodeLabelIndexCursor);
        } finally {
            nodeLabelIndexCursor = null;
        }
    }

    protected void accept(DefaultNodeLabelIndexCursor cursor) {
        if (nodeLabelIndexCursor != null) {
            nodeLabelIndexCursor.release();
        }
        cursor.removeTracer();
        nodeLabelIndexCursor = cursor;
    }

    @Override
    public DefaultNodeLabelIndexCursor allocateFullAccessNodeLabelIndexCursor(CursorContext cursorContext) {
        if (fullAccessNodeLabelIndexCursor == null) {
            return trace(new FullAccessNodeLabelIndexCursor(this::acceptFullAccess));
        }

        try {
            return acquire(fullAccessNodeLabelIndexCursor);
        } finally {
            fullAccessNodeLabelIndexCursor = null;
        }
    }

    protected void acceptFullAccess(DefaultNodeLabelIndexCursor cursor) {
        if (fullAccessNodeLabelIndexCursor != null) {
            fullAccessNodeLabelIndexCursor.release();
        }
        fullAccessNodeLabelIndexCursor = cursor;
    }

    @Override
    public RelationshipValueIndexCursor allocateRelationshipValueIndexCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (relationshipValueIndexCursor == null) {
            var internalCursors = newInternalCursors(cursorContext, memoryTracker);
            DefaultRelationshipScanCursor relationshipScanCursor = new DefaultRelationshipScanCursor(
                    c -> {},
                    storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker),
                    internalCursors,
                    applyAccessModeToTxState);
            return trace(new DefaultRelationshipValueIndexCursor(
                    this::accept, relationshipScanCursor, internalCursors, applyAccessModeToTxState));
        }

        try {
            return acquire(relationshipValueIndexCursor);
        } finally {
            relationshipValueIndexCursor = null;
        }
    }

    public void accept(DefaultRelationshipValueIndexCursor cursor) {
        if (relationshipValueIndexCursor != null) {
            relationshipValueIndexCursor.release();
        }
        cursor.removeTracer();
        relationshipValueIndexCursor = cursor;
    }

    @Override
    public RelationshipTypeIndexCursor allocateRelationshipTypeIndexCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (relationshipTypeIndexCursor == null) {
            var internalCursors = newInternalCursors(cursorContext, memoryTracker);
            if (indexingBehaviour.useNodeIdsInRelationshipTokenIndex()) {
                return trace(newNodeBasedRelationshipTypeIndex(cursorContext, memoryTracker, internalCursors));
            } else {
                return trace(
                        newRelationshipBasedRelationshipTypeIndexCursor(cursorContext, memoryTracker, internalCursors));
            }
        }

        try {
            return acquire(relationshipTypeIndexCursor);
        } finally {
            relationshipTypeIndexCursor = null;
        }
    }

    protected DefaultNodeBasedRelationshipTypeIndexCursor newNodeBasedRelationshipTypeIndex(
            CursorContext cursorContext, MemoryTracker memoryTracker, InternalCursorFactory internalCursors) {
        var relationshipTraversalCursor = new DefaultRelationshipTraversalCursor(
                c -> {},
                storageReader.allocateRelationshipTraversalCursor(cursorContext, storeCursors, memoryTracker),
                internalCursors,
                applyAccessModeToTxState);

        return new DefaultNodeBasedRelationshipTypeIndexCursor(
                this::accept, internalCursors.allocateNodeCursor(), relationshipTraversalCursor);
    }

    protected DefaultRelationshipBasedRelationshipTypeIndexCursor newRelationshipBasedRelationshipTypeIndexCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker, InternalCursorFactory internalCursors) {
        var relationshipScanCursor = new DefaultRelationshipScanCursor(
                c -> {},
                storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker),
                internalCursors,
                applyAccessModeToTxState);
        return new DefaultRelationshipBasedRelationshipTypeIndexCursor(
                this::accept, relationshipScanCursor, applyAccessModeToTxState);
    }

    protected void accept(InternalRelationshipTypeIndexCursor cursor) {
        if (relationshipTypeIndexCursor != null) {
            relationshipTypeIndexCursor.release();
        }
        cursor.removeTracer();
        relationshipTypeIndexCursor = cursor;
    }

    @Override
    public RelationshipTypeIndexCursor allocateFullAccessRelationshipTypeIndexCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        if (fullAccessRelationshipTypeIndexCursor == null) {
            if (indexingBehaviour.useNodeIdsInRelationshipTokenIndex()) {
                var closeable = newFullAccessNodeBasedRelationshipTypeIndexCursor(cursorContext, memoryTracker);
                return trace(closeable);
            } else {

                var closeable = newFullAccessRelationshipBasedRelationshipTypeIndexCursor(cursorContext, memoryTracker);
                return trace(closeable);
            }
        }

        try {
            return acquire(fullAccessRelationshipTypeIndexCursor);
        } finally {
            fullAccessRelationshipTypeIndexCursor = null;
        }
    }

    protected DefaultNodeBasedRelationshipTypeIndexCursor newFullAccessNodeBasedRelationshipTypeIndexCursor(
            CursorContext cursorContext, MemoryTracker memoryTracker) {
        var nodeCursor = new FullAccessNodeCursor(
                c -> {}, storageReader.allocateNodeCursor(cursorContext, storeCursors, memoryTracker));
        var relationshipTraversalCursor = new FullAccessRelationshipTraversalCursor(
                c -> {}, storageReader.allocateRelationshipTraversalCursor(cursorContext, storeCursors, memoryTracker));
        var closeable = new DefaultNodeBasedRelationshipTypeIndexCursor(
                this::acceptFullAccess, nodeCursor, relationshipTraversalCursor);
        return closeable;
    }

    protected FullAccessRelationshipBasedRelationshipTypeIndexCursor
            newFullAccessRelationshipBasedRelationshipTypeIndexCursor(
                    CursorContext cursorContext, MemoryTracker memoryTracker) {

        var relationshipScanCursor = new FullAccessRelationshipScanCursor(
                c -> {}, storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker));
        var closeable = new FullAccessRelationshipBasedRelationshipTypeIndexCursor(
                this::acceptFullAccess, relationshipScanCursor);
        return closeable;
    }

    protected void acceptFullAccess(InternalRelationshipTypeIndexCursor cursor) {
        if (fullAccessRelationshipTypeIndexCursor != null) {
            fullAccessRelationshipTypeIndexCursor.release();
        }
        fullAccessRelationshipTypeIndexCursor = cursor;
    }

    public void release() {
        if (nodeCursor != null) {
            nodeCursor.release();
        }
        if (fullAccessNodeCursor != null) {
            fullAccessNodeCursor.release();
        }
        if (relationshipScanCursor != null) {
            relationshipScanCursor.release();
        }
        if (fullAccessRelationshipScanCursor != null) {
            fullAccessRelationshipScanCursor.release();
        }
        if (relationshipTraversalCursor != null) {
            relationshipTraversalCursor.release();
        }
        if (fullAccessRelationshipTraversalCursor != null) {
            fullAccessRelationshipTraversalCursor.release();
        }
        if (propertyCursor != null) {
            propertyCursor.release();
        }
        if (fullAccessPropertyCursor != null) {
            ((TraceableCursor) fullAccessPropertyCursor).release();
        }
        if (nodeValueIndexCursor != null) {
            nodeValueIndexCursor.release();
        }
        if (nodeLabelIndexCursor != null) {
            nodeLabelIndexCursor.release();
        }
        if (fullAccessNodeLabelIndexCursor != null) {
            fullAccessNodeLabelIndexCursor.release();
        }
        if (relationshipValueIndexCursor != null) {
            relationshipValueIndexCursor.release();
        }
        if (relationshipTypeIndexCursor != null) {
            relationshipTypeIndexCursor.release();
        }
        if (fullAccessRelationshipTypeIndexCursor != null) {
            fullAccessRelationshipTypeIndexCursor.release();
        }
        nodeCursor = null;
        fullAccessNodeCursor = null;
        relationshipScanCursor = null;
        fullAccessRelationshipScanCursor = null;
        relationshipTraversalCursor = null;
        fullAccessRelationshipTraversalCursor = null;
        propertyCursor = null;
        fullAccessPropertyCursor = null;
        nodeValueIndexCursor = null;
        nodeLabelIndexCursor = null;
        fullAccessNodeLabelIndexCursor = null;
        relationshipValueIndexCursor = null;
        relationshipTypeIndexCursor = null;
        fullAccessRelationshipTypeIndexCursor = null;
    }

    protected InternalCursorFactory newInternalCursors(CursorContext cursorContext, MemoryTracker memoryTracker) {
        return new InternalCursorFactory(
                storageReader, storeCursors, cursorContext, memoryTracker, applyAccessModeToTxState);
    }
}

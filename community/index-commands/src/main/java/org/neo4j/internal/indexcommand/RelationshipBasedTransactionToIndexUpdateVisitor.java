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
package org.neo4j.internal.indexcommand;

import static org.neo4j.common.EntityType.RELATIONSHIP;
import static org.neo4j.storageengine.api.TokenIndexEntryUpdate.tokenChange;
import static org.neo4j.token.api.TokenConstants.ANY_RELATIONSHIP_TYPE;

import org.neo4j.exceptions.KernelException;
import org.neo4j.internal.kernel.api.exceptions.schema.ConstraintValidationException;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.IOUtils;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.storageengine.api.txstate.RelationshipModifications;
import org.neo4j.storageengine.api.txstate.TxStateVisitor;

public final class RelationshipBasedTransactionToIndexUpdateVisitor extends TransactionToIndexUpdateVisitor {
    private final StorageRelationshipScanCursor relationshipCursor;
    private final IndexDescriptor relationshipTypeIndex;

    public RelationshipBasedTransactionToIndexUpdateVisitor(
            TxStateVisitor next,
            IndexUpdatesState indexUpdatesState,
            StorageReader storageReader,
            CursorContext cursorContext,
            StoreCursors storeCursors,
            MemoryTracker memoryTracker) {
        super(next, indexUpdatesState, storageReader, cursorContext, storeCursors, memoryTracker);
        this.relationshipTypeIndex = getTokenIndex(storageReader, RELATIONSHIP);
        this.relationshipCursor =
                storageReader.allocateRelationshipScanCursor(cursorContext, storeCursors, memoryTracker);
    }

    @Override
    public void visitRelationshipModifications(RelationshipModifications modifications)
            throws ConstraintValidationException {
        super.visitRelationshipModifications(modifications);

        if (relationshipTypeIndex == null) {
            return;
        }

        modifications
                .creations()
                .forEach((id, type, sn, en, ap, rp) -> indexUpdatesState.addTokenUpdate(
                        tokenChange(id, relationshipTypeIndex, NO_TOKENS, new int[] {type})));
        modifications
                .deletions()
                .forEach((id, type, sn, en, ap, rp) -> indexUpdatesState.addTokenUpdate(
                        tokenChange(id, relationshipTypeIndex, new int[] {findTypeToRemove(id, type)}, NO_TOKENS)));
    }

    private int findTypeToRemove(long id, int type) {
        if (type == ANY_RELATIONSHIP_TYPE) {
            relationshipCursor.single(id);
            if (!relationshipCursor.next()) {
                throw new IllegalStateException(
                        "Relationship being deleted should exist along with its nodes. Relationship[" + id + "]");
            }
            return relationshipCursor.type();
        }
        return type;
    }

    @Override
    public void close() throws KernelException {
        IOUtils.closeAllUnchecked(super::close, relationshipCursor);
    }
}

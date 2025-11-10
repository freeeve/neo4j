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
package org.neo4j.internal.kernel.api;

import static org.neo4j.kernel.api.StatementConstants.NO_SUCH_NODE;
import static org.neo4j.kernel.api.StatementConstants.NO_SUCH_RELATIONSHIP;
import static org.neo4j.kernel.api.StatementConstants.NO_SUCH_RELATIONSHIP_TYPE;
import static org.neo4j.storageengine.api.LongReference.NULL_REFERENCE;

import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;

/**
 * Cursor for relationships
 */
public interface RelationshipCursor extends EntityCursor {
    @Override
    default long reference() {
        return relationshipReference();
    }

    long relationshipReference();

    int type();

    long sourceNodeReference();

    long targetNodeReference();

    void source(NodeCursor cursor);

    void target(NodeCursor cursor);

    class Empty extends DoNothingCloseListenable implements RelationshipCursor {

        @Override
        public long relationshipReference() {
            return NO_SUCH_RELATIONSHIP;
        }

        @Override
        public int type() {
            return NO_SUCH_RELATIONSHIP_TYPE;
        }

        @Override
        public long sourceNodeReference() {
            return NO_SUCH_NODE;
        }

        @Override
        public long targetNodeReference() {
            return NO_SUCH_NODE;
        }

        @Override
        public Reference propertiesReference() {
            return NULL_REFERENCE;
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void source(NodeCursor cursor) {}

        @Override
        public void target(NodeCursor cursor) {}

        @Override
        public void properties(PropertyCursor cursor, PropertySelection selection) {}

        @Override
        public void setTracer(KernelReadTracer tracer) {}

        @Override
        public void removeTracer() {}

        @Override
        public void closeInternal() {}
    }

    RelationshipCursor EMPTY = new RelationshipCursor.Empty();
}

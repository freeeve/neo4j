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
package org.neo4j.internal.kernel.api.helpers;

import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.RelationshipValueIndexCursor;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;

public class StubRelationshipValueIndexCursor extends StubEntityValueIndexCursor
        implements RelationshipValueIndexCursor {

    @Override
    public boolean readFromStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long relationshipReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int type() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long sourceNodeReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long targetNodeReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void source(NodeCursor cursor) {}

    @Override
    public void target(NodeCursor cursor) {}

    @Override
    public void properties(PropertyCursor cursor, PropertySelection selection) {}

    @Override
    public Reference propertiesReference() {
        throw new UnsupportedOperationException();
    }
}

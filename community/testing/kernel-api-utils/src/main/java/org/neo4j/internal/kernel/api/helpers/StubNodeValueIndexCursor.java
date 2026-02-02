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
import org.neo4j.internal.kernel.api.NodeValueIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.storageengine.api.Degrees;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.RelationshipSelection;

public class StubNodeValueIndexCursor extends StubEntityValueIndexCursor implements NodeValueIndexCursor {

    @Override
    public void node(NodeCursor cursor) {}

    @Override
    public long nodeReference() {
        return reference();
    }

    @Override
    public TokenSet labels() {
        throw unsupportedOperation();
    }

    @Override
    public TokenSet labelsIgnoringTxStateSetRemove() {
        throw unsupportedOperation();
    }

    @Override
    public boolean hasLabel(int label) {
        throw unsupportedOperation();
    }

    @Override
    public boolean hasLabel() {
        throw unsupportedOperation();
    }

    @Override
    public void relationships(RelationshipTraversalCursor relationships, RelationshipSelection selection) {}

    @Override
    public boolean supportsFastRelationshipsTo() {
        throw unsupportedOperation();
    }

    @Override
    public void relationshipsTo(
            RelationshipTraversalCursor relationships, RelationshipSelection selection, long neighbourNodeReference) {
        throw unsupportedOperation();
    }

    @Override
    public long relationshipsReference() {
        throw unsupportedOperation();
    }

    @Override
    public boolean supportsFastDegreeLookup() {
        return false;
    }

    @Override
    public int[] relationshipTypes() {
        throw unsupportedOperation();
    }

    @Override
    public Degrees degrees(RelationshipSelection selection) {
        throw unsupportedOperation();
    }

    @Override
    public long degree(RelationshipSelection selection) {
        throw unsupportedOperation();
    }

    @Override
    public long degreeWithMax(long maxDegree, RelationshipSelection selection) {
        throw unsupportedOperation();
    }

    @Override
    public boolean readFromStore() {
        throw unsupportedOperation();
    }

    @Override
    public void properties(PropertyCursor cursor, PropertySelection selection) {
        throw unsupportedOperation();
    }

    @Override
    public Reference propertiesReference() {
        throw unsupportedOperation();
    }

    private UnsupportedOperationException unsupportedOperation() {
        return new UnsupportedOperationException("StubNodeValueIndexCursor does not support NodeCursorOperations");
    }
}

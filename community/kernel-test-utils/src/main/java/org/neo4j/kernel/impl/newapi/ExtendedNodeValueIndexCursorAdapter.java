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

import org.neo4j.internal.kernel.api.DefaultCloseListenable;
import org.neo4j.internal.kernel.api.IndexQueryConstraints;
import org.neo4j.internal.kernel.api.KernelReadTracer;
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.NodeValueIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.api.AccessModeProvider;
import org.neo4j.kernel.api.index.IndexProgressor;
import org.neo4j.kernel.api.txstate.TxStateHolder;
import org.neo4j.storageengine.api.Degrees;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.RelationshipSelection;
import org.neo4j.values.storable.Value;

public class ExtendedNodeValueIndexCursorAdapter extends DefaultCloseListenable
        implements NodeValueIndexCursor, EntityIndexSeekClient {
    @Override
    public void closeInternal() {}

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean next() {
        return false;
    }

    @Override
    public void node(NodeCursor cursor) {}

    @Override
    public long nodeReference() {
        return 0;
    }

    @Override
    public int numberOfProperties() {
        return 0;
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public Value propertyValue(int offset) {
        return null;
    }

    @Override
    public float score() {
        return 0;
    }

    @Override
    public void initState(
            Read read,
            TxStateHolder txStateHolder,
            AccessModeProvider accessModeProvider,
            boolean includeChangesFromThisTransaction) {}

    @Override
    public void initializeQuery(
            IndexDescriptor descriptor,
            IndexProgressor progressor,
            boolean indexIncludesTransactionState,
            boolean needStoreFilter,
            IndexQueryConstraints constraints,
            PropertyIndexQuery... query) {}

    @Override
    public boolean acceptEntity(long reference, float score, Value... values) {
        return false;
    }

    @Override
    public boolean needsValues() {
        return false;
    }

    @Override
    public void setTracer(KernelReadTracer tracer) {
        throw notImplemented();
    }

    @Override
    public void removeTracer() {
        throw notImplemented();
    }

    @Override
    public TokenSet labels() {
        throw notImplemented();
    }

    @Override
    public TokenSet labelsIgnoringTxStateSetRemove() {
        throw notImplemented();
    }

    @Override
    public boolean hasLabel(int label) {
        throw notImplemented();
    }

    @Override
    public boolean hasLabel() {
        throw notImplemented();
    }

    @Override
    public void relationships(RelationshipTraversalCursor relationships, RelationshipSelection selection) {
        throw notImplemented();
    }

    @Override
    public boolean supportsFastRelationshipsTo() {
        throw notImplemented();
    }

    @Override
    public void relationshipsTo(
            RelationshipTraversalCursor relationships, RelationshipSelection selection, long neighbourNodeReference) {
        throw notImplemented();
    }

    @Override
    public long relationshipsReference() {
        throw notImplemented();
    }

    @Override
    public boolean supportsFastDegreeLookup() {
        throw notImplemented();
    }

    @Override
    public int[] relationshipTypes() {
        throw notImplemented();
    }

    @Override
    public Degrees degrees(RelationshipSelection selection) {
        throw notImplemented();
    }

    @Override
    public int degree(RelationshipSelection selection) {
        throw notImplemented();
    }

    @Override
    public int degreeWithMax(int maxDegree, RelationshipSelection selection) {
        throw notImplemented();
    }

    @Override
    public boolean readFromStore() {
        throw notImplemented();
    }

    @Override
    public void properties(PropertyCursor cursor, PropertySelection selection) {
        throw notImplemented();
    }

    @Override
    public Reference propertiesReference() {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("not implemented");
    }
}

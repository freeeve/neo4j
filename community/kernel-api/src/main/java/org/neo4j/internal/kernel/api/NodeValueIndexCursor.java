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

import static org.neo4j.values.storable.Values.NO_VALUE;

import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.storageengine.api.Degrees;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.RelationshipSelection;
import org.neo4j.values.storable.Value;

/**
 * Cursor for scanning the property values of nodes in a schema index.
 * <p>
 * Usage pattern:
 * <pre><code>
 *     int nbrOfProps = cursor.numberOfProperties();
 *
 *     Value[] values = new Value[nbrOfProps];
 *     while ( cursor.next() )
 *     {
 *         if ( cursor.hasValue() )
 *         {
 *             for ( int i = 0; i < nbrOfProps; i++ )
 *             {
 *                 values[i] = cursor.propertyValue( i );
 *             }
 *         }
 *         else
 *         {
 *              for ( int i = 0; i < nbrOfProps; i++ )
 *              {
 *                  values[i] = getPropertyValueFromStore( cursor.nodeReference(), cursor.propertyKey( i ) )
 *              }
 *         }
 *
 *         doWhatYouWantToDoWith( values );
 *     }
 * </code></pre>
 */
public interface NodeValueIndexCursor extends NodeIndexCursor, ValueIndexCursor {

    class Empty extends DoNothingCloseListenable implements NodeValueIndexCursor {

        @Override
        public void node(NodeCursor cursor) {}

        @Override
        public long nodeReference() {
            return StatementConstants.NO_SUCH_NODE;
        }

        @Override
        public TokenSet labels() {
            throw notReadFromStore();
        }

        @Override
        public TokenSet labelsIgnoringTxStateSetRemove() {
            throw notReadFromStore();
        }

        @Override
        public boolean hasLabel(int label) {
            throw notReadFromStore();
        }

        @Override
        public boolean hasLabel() {
            throw notReadFromStore();
        }

        @Override
        public void relationships(RelationshipTraversalCursor relationships, RelationshipSelection selection) {
            throw notReadFromStore();
        }

        @Override
        public boolean supportsFastRelationshipsTo() {
            throw notReadFromStore();
        }

        @Override
        public void relationshipsTo(
                RelationshipTraversalCursor relationships,
                RelationshipSelection selection,
                long neighbourNodeReference) {
            throw notReadFromStore();
        }

        @Override
        public long relationshipsReference() {
            throw notReadFromStore();
        }

        @Override
        public boolean supportsFastDegreeLookup() {
            throw notReadFromStore();
        }

        @Override
        public int[] relationshipTypes() {
            throw notReadFromStore();
        }

        @Override
        public Degrees degrees(RelationshipSelection selection) {
            throw notReadFromStore();
        }

        @Override
        public long degree(RelationshipSelection selection) {
            throw notReadFromStore();
        }

        @Override
        public long degreeWithMax(long maxDegree, RelationshipSelection selection) {
            throw notReadFromStore();
        }

        @Override
        public boolean readFromStore() {
            return false;
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public void closeInternal() {
            // do nothing
        }

        @Override
        public boolean isClosed() {
            return false;
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
        public float score() {
            return Float.NaN;
        }

        @Override
        public Value propertyValue(int offset) {
            return NO_VALUE;
        }

        @Override
        public void setTracer(KernelReadTracer tracer) {}

        @Override
        public void removeTracer() {}

        @Override
        public void properties(PropertyCursor cursor, PropertySelection selection) {
            throw notReadFromStore();
        }

        @Override
        public Reference propertiesReference() {
            throw notReadFromStore();
        }

        private IllegalStateException notReadFromStore() {
            throw new IllegalStateException("Node hasn't been read from store");
        }
    }

    NodeValueIndexCursor EMPTY = new Empty();
}

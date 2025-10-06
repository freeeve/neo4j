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
package org.neo4j.internal.batchimport;

import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.neo4j.batchimport.api.input.ApplicationMode;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.values.storable.Value;

public interface SchemaMonitor extends AutoCloseable {
    ExistingPropertyKeysLookup NO_EXISTING_PROPERTY_KEYS_LOOKUP = (entityId, keysToLookup) -> IntSets.immutable.empty();

    SchemaMonitor NO_MONITOR = new SchemaMonitor() {
        @Override
        public void applicationMode(ApplicationMode mode) {}

        @Override
        public void property(int propertyKeyId, Object value, boolean identifier) {}

        @Override
        public void removedProperty(int propertyKeyId) {}

        @Override
        public void existingEntityTokens(int[] entityTokens) {}

        @Override
        public void entityToken(int entityTokenId) {}

        @Override
        public void entityTokens(int[] entityTokenIds) {}

        @Override
        public void removedEntityTokens(int[] entityTokens) {}

        @Override
        public boolean endOfEntity(
                long entityId,
                ExistingPropertyKeysLookup existingPropertyKeysLookup,
                ViolationVisitor violationVisitor) {
            return true;
        }

        @Override
        public void indexUpdate(IndexEntryUpdate indexUpdate) {}

        @Override
        public void reset() {}

        @Override
        public void close() {}
    };

    void applicationMode(ApplicationMode mode);

    void property(int propertyKeyId, Object value, boolean identifier);

    void removedProperty(int propertyKeyId);

    void existingEntityTokens(int[] entityTokens);

    void entityToken(int entityTokenId);

    void entityTokens(int[] entityTokenIds);

    void removedEntityTokens(int[] entityTokens);

    boolean endOfEntity(
            long entityId, ExistingPropertyKeysLookup existingPropertyKeysLookup, ViolationVisitor violationVisitor);

    /**
     * Used when there's something figuring out relevant index updates for an updated entity,
     * for updated entities where existing data is also read
     * @param indexUpdate index update to apply.
     */
    void indexUpdate(IndexEntryUpdate indexUpdate);

    void reset();

    @Override
    void close();

    interface ViolationVisitor {
        void accept(long entityId, IntList tokens, IntObjectMap<Value> properties, String constraintDescription);
    }

    interface ExistingPropertyKeysLookup {
        IntSet lookupPropertyKeys(long entityId, IntSet keysToLookup);
    }
}

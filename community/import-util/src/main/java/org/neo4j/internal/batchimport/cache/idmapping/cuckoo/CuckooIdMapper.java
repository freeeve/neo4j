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
package org.neo4j.internal.batchimport.cache.idmapping.cuckoo;

import java.util.function.LongPredicate;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.impl.iterator.ImmutableEmptyLongIterator;
import org.neo4j.batchimport.api.PropertyValueLookup;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.internal.batchimport.cache.MemoryStatsVisitor;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.internal.batchimport.cache.idmapping.IdMapper;
import org.neo4j.internal.helpers.progress.ProgressMonitorFactory;
import org.neo4j.memory.MemoryTracker;

public class CuckooIdMapper implements IdMapper {
    final CuckooTable cuckooTable;
    final int groupShift;
    final long longValueMask;

    public CuckooIdMapper(
            long estimatedNumberOfNodes,
            NumberArrayFactory arrayFactory,
            ReadableGroups groups,
            MemoryTracker memoryTracker) {
        cuckooTable = new CuckooTable(estimatedNumberOfNodes, arrayFactory, memoryTracker);
        groupShift = calculateGroupShift(groups.size() - 1);
        longValueMask = groupShift == 0 ? 0 : -(1L << (Long.SIZE - groupShift));
    }

    @Override
    public Setter newSetter(int workerId) {
        return (inputId, actualId, group) -> cuckooTable.insert(getKey(inputId, group), actualId);
    }

    @Override
    public Getter newGetter(int workerId) {
        return new Getter() {
            @Override
            public long get(Object inputId, Group group) {
                return cuckooTable.get(getKey(inputId, group));
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void remove(Object inputId, long actualId, Group group) {
        cuckooTable.remove(actualId);
    }

    @Override
    public boolean needsPreparation() {
        return false;
    }

    @Override
    public void prepare(
            PropertyValueLookup propertyValueLookup,
            Collector collector,
            ProgressMonitorFactory progressMonitorFactory,
            LongSet otherViolatingNodes) {
        otherViolatingNodes.forEach(cuckooTable::remove);
    }

    @Override
    public void close() {
        cuckooTable.close();
    }

    @Override
    public MemoryStatsVisitor.Visitable memoryEstimation(long numberOfNodes) {
        long sizePerKeyPair = Long.BYTES + Long.BYTES;
        return visitor -> visitor.offHeapUsage((long) ((numberOfNodes * sizePerKeyPair) / 0.95d));
    }

    public static long estimateMemory(long numberOfNodes) {
        long sizePerKeyPair = Long.BYTES + Long.BYTES;
        return (long) ((numberOfNodes * sizePerKeyPair) / 0.95d);
    }

    @Override
    public LongIterator leftOverDuplicateNodesIds() {
        return ImmutableEmptyLongIterator.INSTANCE;
    }

    @Override
    public LongPredicate leftOverDuplicateNodesIdsPredicate() {
        return null;
    }

    @Override
    public void acceptMemoryStatsVisitor(MemoryStatsVisitor visitor) {}

    private long getKey(Object inputId, Group group) {
        long idHash = inputIdToLongHash(inputId, longValueMask);
        return (idHash << groupShift) | group.id();
    }

    private static long inputIdToLongHash(Object inputId, long longValueMask) {
        if (inputId instanceof Number n) {
            long l = n.longValue();
            if ((l & longValueMask) != 0) {
                throw new IllegalArgumentException("Id " + l + " overflowed");
            }
            return l;
        }
        throw new IllegalArgumentException("Id " + inputId + " not a number");
    }

    /**
     * @param numberOfGroups number of groups.
     * @return the number of bit's required to distinguish between the provided number of groups.
     */
    private static int calculateGroupShift(int numberOfGroups) {
        if (numberOfGroups <= 0) {
            return 0;
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(numberOfGroups);
    }
}

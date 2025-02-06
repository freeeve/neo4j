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
package org.neo4j.internal.batchimport.cache.idmapping;

import static java.lang.Math.clamp;
import static org.neo4j.internal.batchimport.cache.idmapping.string.EncodingIdMapper.NO_MONITOR;
import static org.neo4j.internal.batchimport.cache.idmapping.string.TrackerFactories.dynamic;
import static org.neo4j.io.ByteUnit.gibiBytes;
import static org.neo4j.util.FeatureToggles.flag;

import java.util.function.LongPredicate;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.impl.iterator.ImmutableEmptyLongIterator;
import org.neo4j.batchimport.api.PropertyValueLookup;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.collection.PrimitiveLongCollections;
import org.neo4j.internal.batchimport.cache.MemoryStatsVisitor;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.internal.batchimport.cache.idmapping.cuckoo.CuckooIdMapper;
import org.neo4j.internal.batchimport.cache.idmapping.cuckoo.StringCuckooIdMapper;
import org.neo4j.internal.batchimport.cache.idmapping.string.EncodingIdMapper;
import org.neo4j.internal.batchimport.cache.idmapping.string.LongCollisionValues;
import org.neo4j.internal.batchimport.cache.idmapping.string.LongEncoder;
import org.neo4j.internal.batchimport.cache.idmapping.string.ParallelSort;
import org.neo4j.internal.batchimport.cache.idmapping.string.Radix;
import org.neo4j.internal.batchimport.cache.idmapping.string.StringCollisionValues;
import org.neo4j.internal.batchimport.cache.idmapping.string.StringEncoder;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.internal.helpers.progress.ProgressMonitorFactory;
import org.neo4j.memory.MemoryTracker;

/**
 * Place to instantiate common {@link IdMapper} implementations.
 */
public final class IdMappers {
    public static final boolean USE_CUCKOO_MAPPER = flag(IdMappers.class, "use_cuckoo", false);

    private static class ActualIdMapper implements IdMapper, IdMapper.Getter {
        @Override
        public Setter newSetter() {
            return (inputId, actualId, group) -> {};
        }

        @Override
        public void remove(Object inputId, long actualId, Group group) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean needsPreparation() {
            return false;
        }

        @Override
        public void prepare(
                PropertyValueLookup inputIdLookup,
                Collector collector,
                ProgressMonitorFactory progressMonitorFactory,
                LongSet otherViolatingNodes) { // No need to prepare anything
        }

        @Override
        public long get(Object inputId, Group group) {
            return (Long) inputId;
        }

        @Override
        public Getter newGetter() {
            return this;
        }

        @Override
        public void acceptMemoryStatsVisitor(MemoryStatsVisitor visitor) { // No memory usage
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }

        @Override
        public void close() { // Nothing to close
        }

        @Override
        public MemoryStatsVisitor.Visitable memoryEstimation(long numberOfNodes) {
            return MemoryStatsVisitor.NONE;
        }

        @Override
        public LongIterator leftOverDuplicateNodesIds() {
            return ImmutableEmptyLongIterator.INSTANCE;
        }

        @Override
        public LongPredicate leftOverDuplicateNodesIdsPredicate() {
            return id -> false;
        }
    }

    private IdMappers() {}

    /**
     * An {@link IdMapper} that doesn't touch the input ids, but just asserts that node ids arrive in ascending order.
     * This is for advanced usage and puts constraints on the input in that all node ids given as input
     * must be valid. There will not be further checks, other than that for order of the ids.
     */
    public static IdMapper actual() {
        return new ActualIdMapper();
    }

    /**
     * An {@link IdMapper} capable of mapping {@link String strings} to long ids.
     *
     * @param cacheFactory {@link NumberArrayFactory} for allocating memory for the cache used by this index.
     * @param groups {@link Groups} containing all id groups.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return {@link IdMapper} for when input ids are strings.
     */
    public static IdMapper strings(
            NumberArrayFactory cacheFactory,
            ReadableGroups groups,
            boolean strictNodeCheck,
            MemoryTracker memoryTracker,
            long estimatedNumberOfNodes,
            PropertyValueLookup inputIdLookup) {
        if (USE_CUCKOO_MAPPER) {
            return new StringCuckooIdMapper(estimatedNumberOfNodes, cacheFactory, groups, memoryTracker, inputIdLookup);
        }
        return new EncodingIdMapper(
                cacheFactory,
                new StringEncoder(),
                strictNodeCheck,
                Radix.STRING,
                NO_MONITOR,
                dynamic(memoryTracker),
                groups,
                numberOfCollisions -> new StringCollisionValues(cacheFactory, numberOfCollisions, memoryTracker),
                goodChunkSize(estimatedNumberOfNodes),
                EncodingIdMapper.defaultNumberOfSortWorkers(),
                ParallelSort.DEFAULT,
                memoryTracker);
    }

    /**
     * An {@link IdMapper} capable of mapping {@link Long arbitrary longs} to long ids.
     *
     * @param cacheFactory {@link NumberArrayFactory} for allocating memory for the cache used by this index.
     * @param groups {@link Groups} containing all id groups.
     * @param memoryTracker underlying buffers allocation memory tracker
     * @return {@link IdMapper} for when input ids are numbers.
     */
    public static IdMapper longs(
            NumberArrayFactory cacheFactory,
            ReadableGroups groups,
            MemoryTracker memoryTracker,
            long estimatedNumberOfNodes) {
        if (USE_CUCKOO_MAPPER) {
            return new CuckooIdMapper(estimatedNumberOfNodes, cacheFactory, groups, memoryTracker);
        }
        return new EncodingIdMapper(
                cacheFactory,
                new LongEncoder(),
                false /* no need for strict checkups for longs*/,
                Radix.LONG,
                NO_MONITOR,
                dynamic(memoryTracker),
                groups,
                numberOfCollisions -> new LongCollisionValues(cacheFactory, numberOfCollisions, memoryTracker),
                goodChunkSize(estimatedNumberOfNodes),
                EncodingIdMapper.defaultNumberOfSortWorkers(),
                ParallelSort.DEFAULT,
                memoryTracker);
    }

    private static int goodChunkSize(long estimatedNumberOfNodes) {
        return (int) clamp(
                estimatedNumberOfNodes / 100, EncodingIdMapper.DEFAULT_CACHE_CHUNK_SIZE, gibiBytes(1) / Long.BYTES);
    }

    public static LongPredicate combineSkipFilter(LongPredicate duplicateIds, LongSet otherViolatingIds) {
        boolean emptyDuplicateIds = duplicateIds == null;
        boolean emptyOtherViolatingIds = otherViolatingIds == null || otherViolatingIds.isEmpty();
        if (emptyDuplicateIds && emptyOtherViolatingIds) {
            return null;
        }

        return emptyDuplicateIds
                ? otherViolatingIds::contains
                : (nodeId -> otherViolatingIds.contains(nodeId) || duplicateIds.test(nodeId));
    }

    public static LongIterator combineSkipListSorted(LongIterator duplicateIds, LongSet otherViolatingIds) {
        if (otherViolatingIds == null || otherViolatingIds.isEmpty()) {
            return duplicateIds;
        }
        var sortedViolations = otherViolatingIds.toSortedList().longIterator();
        if (!duplicateIds.hasNext()) {
            return sortedViolations;
        }
        return new MergingLongIterator(duplicateIds, sortedViolations);
    }

    private static class MergingLongIterator extends PrimitiveLongCollections.AbstractPrimitiveLongBaseIterator {
        private final LongIterator first;
        private final LongIterator other;

        private boolean hasFirst;
        private boolean hasOther;
        private long firstHead;
        private long otherHead;

        private MergingLongIterator(LongIterator first, LongIterator other) {
            this.first = first;
            this.other = other;
            getAndAdvanceFirst();
            getAndAdvanceOther();
        }

        private long getAndAdvanceFirst() {
            long result = firstHead;
            hasFirst = first.hasNext();
            firstHead = hasFirst ? first.next() : 0;
            return result;
        }

        private long getAndAdvanceOther() {
            long result = otherHead;
            hasOther = other.hasNext();
            otherHead = hasOther ? other.next() : 0;
            return result;
        }

        @Override
        protected boolean fetchNext() {
            if (hasFirst && hasOther) {
                if (firstHead < otherHead) {
                    return next(getAndAdvanceFirst());
                } else if (otherHead < firstHead) {
                    return next(getAndAdvanceOther());
                } else {
                    getAndAdvanceFirst();
                    return next(getAndAdvanceOther());
                }
            }
            if (hasFirst) {
                return next(getAndAdvanceFirst());
            }
            if (hasOther) {
                return next(getAndAdvanceOther());
            }
            return false;
        }
    }
}

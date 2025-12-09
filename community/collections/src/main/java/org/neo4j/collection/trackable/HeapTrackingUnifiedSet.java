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
package org.neo4j.collection.trackable;

import static java.util.Objects.requireNonNull;
import static org.neo4j.memory.HeapEstimator.ARRAY_HEADER_BYTES;
import static org.neo4j.memory.HeapEstimator.OBJECT_REFERENCE_BYTES;
import static org.neo4j.memory.HeapEstimator.alignObjectSize;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;

import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.VisibleForTesting;

@SuppressWarnings("ExternalizableWithoutPublicNoArgConstructor")
public class HeapTrackingUnifiedSet<T> extends UnifiedSet<T> implements AutoCloseable {
    private static final long SHALLOW_SIZE = shallowSizeOfInstance(HeapTrackingUnifiedSet.class);
    private static final long SHALLOW_SIZE_OF_CHAINED_BUCKET = shallowSizeOfInstance(SimulatedChainedBucket.class);
    private final MemoryTracker memoryTracker;
    private long trackedHeap;

    public static <T> HeapTrackingUnifiedSet<T> createUnifiedSet(MemoryTracker memoryTracker) {
        int initialSizeToAllocate = DEFAULT_INITIAL_CAPACITY << 1;
        long trackedHeap = arrayHeapSize(initialSizeToAllocate);
        memoryTracker.allocateHeap(SHALLOW_SIZE + trackedHeap);
        return new HeapTrackingUnifiedSet<>(memoryTracker, trackedHeap);
    }

    public static <T> HeapTrackingUnifiedSet<T> createUnifiedSet(MemoryTracker memoryTracker, Iterable<T> elements) {
        HeapTrackingUnifiedSet<T> set = HeapTrackingUnifiedSet.createUnifiedSet(memoryTracker);
        set.addAllIterable(elements);
        return set;
    }

    private HeapTrackingUnifiedSet(MemoryTracker memoryTracker, long trackedHeap) {
        this.memoryTracker = requireNonNull(memoryTracker);
        this.trackedHeap = trackedHeap;
    }

    @Override
    public boolean addAllIterable(Iterable<? extends T> iterable) {
        // The copySet case in the base class does not optimize for size == 0 by pre-sizing
        if (iterable instanceof UnifiedSet<? extends T> unifiedSet && size() == 0) {
            var size = unifiedSet.size();
            if (size > this.maxSize) {
                var newCapacity = fastCeil(size / loadFactor);
                this.init(newCapacity);
                allocateEstimatedHeapUsageForChains();
            }
        }
        return super.addAllIterable(iterable);
    }

    @Override
    protected void allocateTable(int sizeToAllocate) {
        if (memoryTracker != null) {
            long heapToAllocate = arrayHeapSize(sizeToAllocate);
            memoryTracker.allocateHeap(heapToAllocate);
            memoryTracker.releaseHeap(trackedHeap);
            trackedHeap = heapToAllocate;
        }
        super.allocateTable(sizeToAllocate);
    }

    @Override
    protected void rehash(int newCapacity) {
        super.rehash(newCapacity);
        allocateEstimatedHeapUsageForChains();
    }

    @Override
    public void close() {
        memoryTracker.releaseHeap(SHALLOW_SIZE + trackedHeap);
    }

    private void allocateEstimatedHeapUsageForChains() {
        // NOTE: To be called on table growth _after_ a new maxSize has been computed.
        // This heuristic is similar to the one in HeapTrackingUnifiedMap, which also uses the same hash function.
        // But UnifiedSet store collisions differently, and we do not have the getCollidingBuckets method we use
        // for HeapTrackingUnifiedMap, so we just estimate the number of chains based on the current size plus
        // headroom for growth up to the maximum size before the next rehash.
        // We estimate the heap usage of the internal ChainedBucket, which holds the chains of colliding buckets
        // in a linked-list of fixed nodes of up to 4 elements (or 3 elements and a pointer to the next node).
        // The number of collisions is lowest just after rehash and can then grow substantially before the next rehash.
        // The heuristic aims to produce a fair trade-off between underestimation just before rehash and overestimation
        // just after rehash, with a tunable bias toward more overestimation after rehash (overestimationWeight).

        // Experimentally derived parameters:
        // These are based on experiments on random data,
        // which showed the colliding bucket fraction to be about 23% just before rehash
        // and about 15% just after rehash, the average chain length to be around 2.1-2.3,
        // with most collision chains fitting within a single ChainedBucket node (99.3 - 99.9%).
        double minEstimatedCollidingBucketFraction = 0.15;
        double maxEstimatedCollidingBucketFraction = 0.23;
        double minMultipleBucketFactor = 1.001;
        double maxMultipleBucketFactor = 1.007;

        double overestimationWeight = 0.57; // Tuning parameter between 0.0 and 1.0

        int maxSize = this.maxSize;
        double minSizeBeforeNextRehash = (maxSize >> 1) + 1;
        double maxSizeBeforeNextRehash = maxSize;
        double minEstimatedNumberOfChains =
                minSizeBeforeNextRehash * minEstimatedCollidingBucketFraction * minMultipleBucketFactor;
        double maxEstimatedNumberOfChains =
                maxSizeBeforeNextRehash * maxEstimatedCollidingBucketFraction * maxMultipleBucketFactor;

        long estimatedNumberOfChains = (long) Math.ceil((1.0d - overestimationWeight) * minEstimatedNumberOfChains
                + overestimationWeight * maxEstimatedNumberOfChains);
        long estimatedHeapUsageForChains = estimatedNumberOfChains * SHALLOW_SIZE_OF_CHAINED_BUCKET;
        memoryTracker.allocateHeap(estimatedHeapUsageForChains);
        trackedHeap += estimatedHeapUsageForChains;
    }

    private int fastCeil(float v) {
        int possibleResult = (int) v;
        if (v - possibleResult > 0.0f) {
            possibleResult++;
        }
        return possibleResult;
    }

    @VisibleForTesting
    public static long arrayHeapSize(int arrayLength) {
        return alignObjectSize(ARRAY_HEADER_BYTES + (long) arrayLength * OBJECT_REFERENCE_BYTES);
    }

    // NOTE: Only used for heap estimation, instead of using reflection to get hold of the private class ChainedBucket
    private static final class SimulatedChainedBucket {
        private Object zero;
        private Object one;
        private Object two;
        private Object three;
    }
}

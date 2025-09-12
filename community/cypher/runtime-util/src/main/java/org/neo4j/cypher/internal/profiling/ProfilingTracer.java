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
package org.neo4j.cypher.internal.profiling;

import java.util.HashMap;
import org.neo4j.cypher.internal.util.attribution.Id;
import org.neo4j.cypher.result.OperatorProfile;
import org.neo4j.cypher.result.QueryProfile;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.impl.query.statistic.StatisticProvider;

public class ProfilingTracer implements QueryProfiler, QueryProfile {
    public interface Clock {
        long nanoTime();

        Clock SYSTEM_TIMER = System::nanoTime;
    }

    private final Clock clock;
    private final StatisticProvider statisticProvider;
    private final HashMap<Integer, ProfilingTracerData> data = new HashMap<>();

    public ProfilingTracer(StatisticProvider statisticProvider) {
        this(Clock.SYSTEM_TIMER, statisticProvider);
    }

    ProfilingTracer(Clock clock, StatisticProvider statisticProvider) {
        this.clock = clock;
        this.statisticProvider = statisticProvider;
    }

    @Override
    public OperatorProfile operatorProfile(int operatorId) {
        ProfilingTracerData value = data.get(operatorId);
        if (value == null) {
            return OperatorProfile.ZERO;
        } else {
            value.sanitize();
            return value;
        }
    }

    @Override
    public long maxAllocatedMemory() {
        return OperatorProfile.NO_DATA;
    }

    @Override
    public int numberOfAvailableWorkers() {
        return (int) OperatorProfile.NO_DATA;
    }

    @Override
    public int numberOfAvailableProcessors() {
        return (int) OperatorProfile.NO_DATA;
    }

    public long timeOf(Id operatorId) {
        return operatorProfile(operatorId.x()).time();
    }

    public long dbHitsOf(Id operatorId) {
        return operatorProfile(operatorId.x()).dbHits();
    }

    public long rowsOf(Id operatorId) {
        return operatorProfile(operatorId.x()).rows();
    }

    @Override
    public OperatorProfileEvent executeOperator(Id operatorId) {
        return executeOperator(operatorId, true);
    }

    @Override
    public OperatorProfileEvent executeOperator(Id operatorId, boolean trackAll) {
        ProfilingTracerData operatorData = this.data.computeIfAbsent(operatorId.x(), k -> new ProfilingTracerData());
        if (trackAll) {
            return new TrackingExecutionEvent(operatorData, clock, statisticProvider);
        } else {
            return new ExecutionEvent(operatorData);
        }
    }

    @Override
    public String toString() {
        return String.format("ProfilingTracer { %s }", data);
    }

    private static class ExecutionEvent extends OperatorProfileEvent {
        final ProfilingTracerData data;
        long hitCount;
        long rowCount;
        HashMap<IndexDescriptor, Integer> indexHits = new HashMap<>();

        ExecutionEvent(ProfilingTracerData data) {
            this.data = data;
        }

        @Override
        public void close() {
            data.update(
                    OperatorProfile.NO_DATA,
                    hitCount,
                    rowCount,
                    OperatorProfile.NO_DATA,
                    OperatorProfile.NO_DATA,
                    OperatorProfile.NO_DATA,
                    indexHits.keySet().toArray(new IndexDescriptor[0]),
                    indexHits.values().stream().mapToInt(v -> v).toArray());
        }

        @Override
        public void dbHit() {
            hitCount++;
        }

        @Override
        public void dbHits(long hits) {
            hitCount += hits;
        }

        @Override
        public void row() {
            rowCount++;
        }

        @Override
        public void rows(long n) {
            rowCount += n;
        }

        @Override
        public void indexHit(IndexDescriptor index) {
            indexHits.put(index, indexHits.getOrDefault(index, 0) + 1);
        }
    }

    private static class TrackingExecutionEvent extends ExecutionEvent {
        private final Clock clock;
        private final StatisticProvider statisticProvider;

        private final long start;
        private final long pageCacheHitsStart;
        private final long pageCacheMissesStart;

        TrackingExecutionEvent(ProfilingTracerData data, Clock clock, StatisticProvider statisticProvider) {
            super(data);
            this.clock = clock;
            this.statisticProvider = statisticProvider;

            this.start = clock.nanoTime();
            this.pageCacheHitsStart = statisticProvider.getPageCacheHits();
            this.pageCacheMissesStart = statisticProvider.getPageCacheMisses();
        }

        @Override
        public void close() {
            long executionTime = clock.nanoTime() - start;
            long pageCacheHits = statisticProvider.getPageCacheHits();
            long pageCacheMisses = statisticProvider.getPageCacheMisses();
            data.update(
                    executionTime,
                    hitCount,
                    rowCount,
                    pageCacheHits - pageCacheHitsStart,
                    pageCacheMisses - pageCacheMissesStart,
                    OperatorProfile.NO_DATA,
                    indexHits.keySet().toArray(new IndexDescriptor[0]),
                    indexHits.values().stream().mapToInt(v -> v).toArray());
        }
    }
}

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
import org.neo4j.cypher.result.OperatorProfile;
import org.neo4j.internal.schema.IndexDescriptor;

public class ProfilingTracerData implements OperatorProfile {
    private long time;
    private long dbHits;
    private long rows;
    private long pageCacheHits;
    private long pageCacheMisses;
    private long maxAllocatedMemory;
    private final HashMap<IndexDescriptor, Integer> indexHits = new HashMap<>();

    public void update(
            long time,
            long dbHits,
            long rows,
            long pageCacheHits,
            long pageCacheMisses,
            long maxAllocatedMemory,
            IndexDescriptor[] indexesUsed,
            int[] indexUseCount) {
        this.time += time;
        this.dbHits += dbHits;
        this.rows += rows;
        this.pageCacheHits += pageCacheHits;
        this.pageCacheMisses += pageCacheMisses;
        this.maxAllocatedMemory += maxAllocatedMemory;

        if (indexesUsed != null && indexUseCount != null) {
            IndexDescriptor index;
            for (int i = 0; i < indexesUsed.length; i++) {
                index = indexesUsed[i];
                this.indexHits.put(index, this.indexHits.getOrDefault(index, 0) + indexUseCount[i]);
            }
        }
    }

    @Override
    public long time() {
        return time;
    }

    @Override
    public long dbHits() {
        return dbHits;
    }

    @Override
    public long rows() {
        return rows;
    }

    @Override
    public long pageCacheHits() {
        return pageCacheHits;
    }

    @Override
    public long pageCacheMisses() {
        return pageCacheMisses;
    }

    @Override
    public long maxAllocatedMemory() {
        return maxAllocatedMemory;
    }

    @Override
    public IndexDescriptor[] indexesUsed() {
        return this.indexHits.keySet().toArray(new IndexDescriptor[0]);
    }

    @Override
    public int[] indexUseCount() {
        return this.indexHits.values().stream().mapToInt(Integer::intValue).toArray();
    }

    public void sanitize() {
        if (time < OperatorProfile.NO_DATA) {
            time = OperatorProfile.NO_DATA;
        }
        if (dbHits < OperatorProfile.NO_DATA) {
            dbHits = OperatorProfile.NO_DATA;
        }
        if (rows < OperatorProfile.NO_DATA) {
            rows = OperatorProfile.NO_DATA;
        }
        if (pageCacheHits < OperatorProfile.NO_DATA) {
            pageCacheHits = OperatorProfile.NO_DATA;
        }
        if (pageCacheMisses < OperatorProfile.NO_DATA) {
            pageCacheMisses = OperatorProfile.NO_DATA;
        }
        if (maxAllocatedMemory < OperatorProfile.NO_DATA) {
            maxAllocatedMemory = OperatorProfile.NO_DATA;
        }
    }

    @Override
    public int hashCode() {
        return OperatorProfile.hashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return OperatorProfile.equals(this, o);
    }

    @Override
    public String toString() {
        return OperatorProfile.toString(this);
    }
}

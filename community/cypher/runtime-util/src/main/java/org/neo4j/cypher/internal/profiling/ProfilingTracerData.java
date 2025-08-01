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

import org.neo4j.cypher.result.OperatorProfile;
import org.neo4j.internal.schema.IndexDescriptor;

public class ProfilingTracerData implements OperatorProfile {
    private long time;
    private long dbHits;
    private long rows;
    private long pageCacheHits;
    private long pageCacheMisses;
    private long maxAllocatedMemory;

    public void update(
            long time, long dbHits, long rows, long pageCacheHits, long pageCacheMisses, long maxAllocatedMemory) {
        this.time += time;
        this.dbHits += dbHits;
        this.rows += rows;
        this.pageCacheHits += pageCacheHits;
        this.pageCacheMisses += pageCacheMisses;
        this.maxAllocatedMemory += maxAllocatedMemory;
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
        // TODO: implement index usage tracking in pipelined
        return new IndexDescriptor[0];
    }

    @Override
    public int[] indexUseCount() {
        // TODO: implement index usage tracking in pipelined
        return new int[0];
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

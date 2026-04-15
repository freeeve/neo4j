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
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs;

import java.util.BitSet;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
import org.neo4j.memory.MemoryTracker;

/**
 * Tracks the presence of entities (nodes or relationships) at different depths in the signpost stack.
 * Used by both Trail mode (tracking relationship IDs) and Acyclic mode (tracking node IDs).
 */
final class DepthPresenceTracker {
    private final HeapTrackingLongObjectHashMap<BitSet> presenceAtDepth;

    DepthPresenceTracker(MemoryTracker memoryTracker) {
        this.presenceAtDepth = HeapTrackingLongObjectHashMap.createLongObjectHashMap(memoryTracker);
    }

    void add(long id, int depth) {
        var depths = presenceAtDepth.get(id);
        if (depths == null) {
            depths = new BitSet();
            presenceAtDepth.put(id, depths);
        }
        depths.set(depth);
    }

    void remove(long id, int depth) {
        var depths = presenceAtDepth.get(id);
        depths.clear(depth);
        if (depths.isEmpty()) {
            presenceAtDepth.remove(id);
        }
    }

    boolean isPresent(long id, int depth) {
        var depths = presenceAtDepth.get(id);
        return depths != null && depths.get(depth);
    }

    /**
     * Returns true if the entity is present at any depth beyond the given one.
     */
    boolean isPresentBeyond(long id, int depth) {
        var depths = presenceAtDepth.get(id);
        return depths != null && depths.length() > depth + 1;
    }

    /**
     * Returns the distance from the last (highest) depth to the previous occurrence,
     * or 0 if there is no previous occurrence.
     */
    int distanceToDuplicate(long id) {
        var depths = presenceAtDepth.get(id);
        if (depths == null) {
            return 0;
        }
        int last = depths.length();
        if (last == 0) {
            return 0;
        }
        int next = depths.previousSetBit(last - 2);
        if (next == -1) {
            return 0;
        }
        return last - 1 - next;
    }

    void clear() {
        presenceAtDepth.clear();
    }
}

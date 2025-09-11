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
package org.neo4j.internal.kernel.api.helpers.traversal.shortestloop;

import java.util.Iterator;
import org.neo4j.internal.kernel.api.DefaultCloseListenable;
import org.neo4j.lang.AutoCloseablePlus;
import org.neo4j.values.virtual.PathReference;

public abstract class UndirectedShortestLoopCursor extends DefaultCloseListenable implements AutoCloseablePlus {
    public abstract boolean next();

    public abstract PathReference path();

    public abstract Iterator<PathReference> shortestPathIterator();

    // We save start-relation as that is what needs to be unique to create a loop.
    record Trace(long relId, long prevNode) {}

    enum Loop {
        LOOP_IN_CURRENT_FRONTIER {
            @Override
            int loopLength(int depth) {
                return 2 * (depth + 1) - 1;
            }
        },
        LOOP_IN_NEXT_FRONTIER {
            @Override
            int loopLength(int depth) {
                return 2 * (depth + 1);
            }
        },
        ALREADY_SEEN {
            @Override
            int loopLength(int depth) {
                throw new IllegalStateException("There is no loop");
            }
        },
        NO_LOOP {
            @Override
            int loopLength(int depth) {
                throw new IllegalStateException("There is no loop");
            }
        };

        abstract int loopLength(int depth);
    }
}

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
package org.neo4j.kernel.impl.transaction.log.enveloped;

import java.io.IOException;
import java.nio.file.Path;

public interface PruneStrategy {
    PruneStrategy NEVER_PRUNE = (long currentEntry, long currentOffset, Path currentLogFile) -> path -> false;
    PruneStrategy ALWAYS_PRUNE = (long currentEntry, long currentOffset, Path currentLogFile) -> path -> true;

    /**
     * Creates a new constraint that is used for this prune event. The current file is never pruned but its state is
     * valuable to calculate the prune events correctly,
     * @param currentEntry last index of the log
     * @param currentOffset the offset of current file. This is needed beause pre-allocation will give the wrong value for
     *                     a size check
     * @param currentLogFile the path of the current file
     * @return a {@link PruneConstraint} used for this prune event.
     * @throws IOException
     */
    PruneConstraint newConstraint(long currentEntry, long currentOffset, Path currentLogFile) throws IOException;

    interface PruneConstraint {
        /**
         * Check if a file can be pruned or not. This check is never done on the current file since that file is treated
         * special and passed in when creating this constraint.
         * @param path the log file the check
         * @return if this file should be pruned. If this file fulfils the constraint it should return false for this file
         * and true for next. This is because it means that the constraint was met somewhere within this file and we always
         * prune entire files.x
         * @throws IOException
         */
        boolean shouldPrune(Path path) throws IOException;
    }
}

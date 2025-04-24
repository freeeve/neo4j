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

import static org.neo4j.kernel.impl.transaction.log.entry.LogHeaderReader.readLogHeader;

import java.io.IOException;
import java.nio.file.Path;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.memory.EmptyMemoryTracker;

class LogPruningByEntryStrategy implements PruneStrategy {
    private final FileSystemAbstraction fs;
    private final long entriesToKeep;

    public LogPruningByEntryStrategy(FileSystemAbstraction fs, long entriesToKeep) {
        this.fs = fs;
        this.entriesToKeep = entriesToKeep;
    }

    @Override
    public PruneConstraint newConstraint(long currentEntry, long currentOffset, Path currentLogFile)
            throws IOException {
        var lastAppendIndex =
                readLogHeader(fs, currentLogFile, EmptyMemoryTracker.INSTANCE).getLastAppendIndex();
        var entriesToKeep = this.entriesToKeep - (currentEntry - lastAppendIndex);
        return new LogPruningByEntry(fs, entriesToKeep, lastAppendIndex);
    }

    static class LogPruningByEntry implements PruneConstraint {
        private final FileSystemAbstraction fs;
        private long entriesLeft;
        private long lastIndex;

        LogPruningByEntry(FileSystemAbstraction fs, long entriesLeft, long lastIndex) {
            this.fs = fs;
            this.entriesLeft = entriesLeft;
            this.lastIndex = lastIndex;
        }

        @Override
        public boolean shouldPrune(Path path) throws IOException {
            long prev = lastIndex;
            lastIndex = readLogHeader(fs, path, EmptyMemoryTracker.INSTANCE).getLastAppendIndex();
            if (prev == -1) {
                return false;
            }
            var shouldPrune = entriesLeft <= 0;
            entriesLeft -= (prev - lastIndex);
            return shouldPrune;
        }
    }
}

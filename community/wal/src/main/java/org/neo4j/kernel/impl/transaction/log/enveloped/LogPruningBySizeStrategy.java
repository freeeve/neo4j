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
import org.neo4j.io.fs.FileSystemAbstraction;

class LogPruningBySizeStrategy implements PruneStrategy {
    private final FileSystemAbstraction fs;
    private final long pruneSizeThreshold;

    LogPruningBySizeStrategy(FileSystemAbstraction fs, long pruneSizeThreshold) {
        this.fs = fs;
        this.pruneSizeThreshold = pruneSizeThreshold;
    }

    @Override
    public PruneConstraint newConstraint(long currentEntry, long currentOffset, Path currentLogFile) {
        return new LogPruningBySize(fs, pruneSizeThreshold, currentOffset);
    }

    private static class LogPruningBySize implements PruneConstraint {

        private final FileSystemAbstraction fs;
        private long remainingBytes;

        LogPruningBySize(FileSystemAbstraction fs, long pruneSizeThreshold, long startOffset) {
            this.fs = fs;
            this.remainingBytes = pruneSizeThreshold - startOffset;
        }

        @Override
        public boolean shouldPrune(Path path) throws IOException {
            if (!fs.fileExists(path)) {
                throw new IllegalArgumentException("Can't get size for non-existing file: " + path);
            }
            var shouldPrune = remainingBytes <= 0;
            remainingBytes -= fs.getFileSize(path);
            return shouldPrune;
        }
    }
}

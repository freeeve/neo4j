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

class LogFilesPruner {
    private final LogsRepository logsRepository;
    private final PruneStrategy pruneStrategy;

    public LogFilesPruner(LogsRepository logsRepository, PruneStrategy pruneStrategy) {
        this.logsRepository = logsRepository;
        this.pruneStrategy = pruneStrategy;
    }

    /**
     * Pruned files if they are below or equal to the desired index and it is allowed by the prune strategy. The first file
     * that fulfills the strategy is never pruned otherwise the contract with the strategy is broken.
     * @param desiredVersionToPrune will prune all files up to this version if strategy allows it
     * @param currentIndex last append index in the log currently
     * @param currentOffset current tail position in the last log file.
     * @param version log version for the log tail file (due to pre-allocation we cannot assume that the last existing
     *               file is the tail)
     * @return the actual highest pruned version
     */
    long pruneUpTo(long desiredVersionToPrune, long currentIndex, long currentOffset, long version) throws IOException {
        long allowedVersion = -1;

        var pruneConstraint =
                this.pruneStrategy.newConstraint(currentIndex, currentOffset, logsRepository.pathFor(version));

        var logVersions = logsRepository.logVersions(true);
        // start at 1 since the context of the first file is passed in
        for (var i = 1; i < logVersions.length; i++) {
            var v = logVersions[i];
            var shouldPrune = pruneConstraint.shouldPrune(logsRepository.pathFor(v));
            if (desiredVersionToPrune >= v && shouldPrune) {
                allowedVersion = v;
                break;
            }
        }
        if (allowedVersion != -1) {
            logsRepository.deleteLogFilesTo(allowedVersion);
        }
        return allowedVersion;
    }
}

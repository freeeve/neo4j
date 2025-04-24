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
import org.neo4j.collection.RawIterator;
import org.neo4j.memory.EmptyMemoryTracker;

public class LogFilesMetadata implements RawIterator<LogFileMetadata, IOException> {
    private final LogsRepository logsRepository;
    private final long[] versions;
    private int currentVersionIndex = -1;
    private LogFileMetadata nextMetadata = null;

    LogFilesMetadata(LogsRepository logsRepository) throws IOException {
        this(logsRepository, false);
    }

    LogFilesMetadata(LogsRepository logsRepository, boolean reversed) throws IOException {
        this.logsRepository = logsRepository;
        this.versions = logsRepository.logVersions(reversed);
    }

    @Override
    public boolean hasNext() {
        return versions.length != 0 && (currentVersionIndex == -1 || nextMetadata != null);
    }

    @Override
    public LogFileMetadata next() throws IOException {
        if (!hasNext()) {
            throw new IllegalStateException("No more versions available");
        }
        var currentMetadata = nextMetadata;
        setNext();
        if (currentVersionIndex == 0) {
            // this was the first entry
            return next();
        }
        return currentMetadata;
    }

    private void setNext() throws IOException {
        if (++currentVersionIndex != versions.length) {
            var version = versions[currentVersionIndex];
            try (var logChannel = logsRepository.openReadChannel(version)) {
                var currentPath = logChannel.path();
                var logHeader = readLogHeader(logChannel.channel(), true, null, EmptyMemoryTracker.INSTANCE);
                if (logHeader != null) {
                    nextMetadata = new LogFileMetadata(logHeader, version, currentPath);
                    return;
                }
            }
        }
        nextMetadata = null;
    }
}

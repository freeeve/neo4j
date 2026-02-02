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

public class EnvelopeLogFilesRangeReader implements EnvelopeLogRangeReader {
    private final EnvelopedLogFiles envelopedLogFiles;

    public EnvelopeLogFilesRangeReader(EnvelopedLogFiles envelopedLogFiles) {
        this.envelopedLogFiles = envelopedLogFiles;
    }

    @Override
    public StoreChannelsForTransfer storeChannels(long fromIndex, long desiredToIndex) throws IOException {
        if (fromIndex == -1) {
            var logFilesMetadata = envelopedLogFiles.logFilesMetadata(false);
            logFilesMetadata.next();
            fromIndex = logFilesMetadata.get().logHeader().getLastAppendIndex() + 1;
        }
        return envelopedLogFiles.storeChannels(fromIndex, desiredToIndex);
    }

    @Override
    public long term(long index) throws IOException {
        if (index == -1) {
            return -1;
        }
        try (var readChannel = envelopedLogFiles.openReadChannel(index)) {
            readChannel.goToEntry(index);
            return readChannel.currentTerm();
        }
    }
}

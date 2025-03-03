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
package org.neo4j.kernel.impl.transaction.log.reverse;

import static org.neo4j.kernel.impl.transaction.log.LogVersionBridge.NO_MORE_CHANNELS;
import static org.neo4j.kernel.impl.transaction.log.reverse.EagerlyReversedCommandBatchCursor.eagerlyReverse;

import java.io.IOException;
import java.util.Optional;
import org.neo4j.kernel.impl.transaction.log.CommandBatchCursor;
import org.neo4j.kernel.impl.transaction.log.CommittedCommandBatchCursor;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.ReadAheadLogChannel;
import org.neo4j.kernel.impl.transaction.log.ReadableLogChannel;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryReader;
import org.neo4j.kernel.impl.transaction.log.enveloped.EnvelopeReadChannel;
import org.neo4j.kernel.impl.transaction.log.files.LogFile;

public final class DefaultReverseCommandBatchCursors implements CommandBatchCursors {

    private final LogFile logFile;
    private final LogPosition beginning;
    private final LogEntryReader reader;
    private final boolean failOnCorruptedLogFiles;
    private final ReversedTransactionCursorMonitor monitor;
    private long currentVersion;

    public DefaultReverseCommandBatchCursors(
            LogFile logFile,
            LogPosition beginning,
            LogEntryReader reader,
            boolean failOnCorruptedLogFiles,
            ReversedTransactionCursorMonitor monitor) {
        this.logFile = logFile;
        this.beginning = beginning;
        this.reader = reader;
        this.failOnCorruptedLogFiles = failOnCorruptedLogFiles;
        this.monitor = monitor;
        this.currentVersion = logFile.getHighestLogVersion();
    }

    @Override
    public Optional<CommandBatchCursor> next() {
        if (currentVersion < beginning.getLogVersion()) {
            return Optional.empty();
        }

        try {
            LogPosition position = getCursorStartPosition();
            CommandBatchCursor cursor = createCursor(position);
            currentVersion--;
            return Optional.of(cursor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LogPosition getCursorStartPosition() throws IOException {
        while (currentVersion > beginning.getLogVersion()) {
            if (logFile.hasAnyEntries(currentVersion)) {
                return logFile.extractHeader(currentVersion).getStartPosition();
            } else {
                currentVersion--;
            }
        }
        return beginning;
    }

    private CommandBatchCursor createCursor(LogPosition position) throws IOException {
        ReadableLogChannel channel = logFile.getReader(position, NO_MORE_CHANNELS);
        try {
            return switch (channel) {
                case ReadAheadLogChannel aheadChannel -> new ReversedSingleFileCommandBatchCursor(
                        aheadChannel, reader, failOnCorruptedLogFiles, monitor);
                case EnvelopeReadChannel readChannel -> new ReversedEnvelopedCommandBatchCursor(
                        readChannel, reader, failOnCorruptedLogFiles, monitor, (EnvelopeReadChannel)
                                logFile.getReader(position));
                default -> eagerlyReverse(new CommittedCommandBatchCursor(channel, reader));
            };
        } catch (Exception e) {
            // sketchOut may fail as part of construction of reversed channels, and if that is happening channel will
            // never be closed otherwise
            if (channel != null) {
                channel.close();
            }
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        // Nothing to close
    }
}

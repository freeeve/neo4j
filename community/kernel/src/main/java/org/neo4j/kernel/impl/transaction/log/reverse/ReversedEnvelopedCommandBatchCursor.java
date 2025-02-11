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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_LONG_ARRAY;

import java.io.IOException;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.neo4j.io.fs.ReadPastEndException;
import org.neo4j.kernel.impl.transaction.CommittedCommandBatchRepresentation;
import org.neo4j.kernel.impl.transaction.log.CommandBatchCursor;
import org.neo4j.kernel.impl.transaction.log.CommittedCommandBatchCursor;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryReader;
import org.neo4j.kernel.impl.transaction.log.entry.UnsupportedLogVersionException;
import org.neo4j.kernel.impl.transaction.log.enveloped.EnvelopeReadChannel;
import org.neo4j.kernel.impl.transaction.log.enveloped.IncompleteEnvelopeReadException;
import org.neo4j.kernel.impl.transaction.log.enveloped.InvalidEndOfFileReadException;

/**
 * Returns command batches in reverse order in an enveloped log file. It tries to keep peak memory consumption to a minimum
 * by first sketching out the offsets of all transactions in the log. Then it starts from the end and moves backwards.
 * Since a transaction can stretch over multiple files this cursor has to handle having read to subsequent files when
 * jumping backwards.
 *
 * This cursor handles all transactions beginning in the sent in file, potentially reading over several files to
 * assemble the last transaction seen (first returned).
 */
public class ReversedEnvelopedCommandBatchCursor implements CommandBatchCursor {
    private EnvelopeReadChannel currentChannel;
    private final boolean failOnCorruptedLogFiles;
    private final ReversedTransactionCursorMonitor monitor;
    private EnvelopeReadChannel unbridgedChannel;
    private final LogEntryReader logEntryReader;
    private CommandBatchCursor commandBatchCursor;
    private final long logVersion;
    private CommittedCommandBatchRepresentation currentCommandBatch;
    private final LongIterator offsets;

    ReversedEnvelopedCommandBatchCursor(
            EnvelopeReadChannel channel,
            LogEntryReader logEntryReader,
            boolean failOnCorruptedLogFiles,
            ReversedTransactionCursorMonitor monitor,
            EnvelopeReadChannel bridgedChannel)
            throws IOException {
        this.unbridgedChannel = channel;
        this.currentChannel = bridgedChannel;
        this.failOnCorruptedLogFiles = failOnCorruptedLogFiles;
        this.monitor = monitor;
        this.commandBatchCursor = new CommittedCommandBatchCursor(bridgedChannel, logEntryReader);
        this.logVersion = channel.getLogVersion();
        this.offsets = sketchOutEnvelopeStartOffsets();
        this.logEntryReader = logEntryReader;
    }

    private LongIterator sketchOutEnvelopeStartOffsets() throws IOException {
        LongArrayList entryStartPositions = new LongArrayList(10_000);

        try {
            long prevPos = -1;
            long pos;
            while (prevPos < (pos = unbridgedChannel.goToNextEntry())) {
                entryStartPositions.add(pos);
                prevPos = pos;
            }
            // Should have read to the end of the file.. Something is wrong
            throw new IOException("Failed to read to end of log file version %d. Last seen byte offset %d"
                    .formatted(logVersion, pos));
        } catch (ReadPastEndException | InvalidEndOfFileReadException | IncompleteEnvelopeReadException e) {
            // Expected
            // Or well, IncompleteEnvelopeReadException isn't really expected, but it is signaling that the last
            // entry is broken. Last broken entry is not considered a corrupted log, and is recoverable.
            // The logs will be truncated after forward recovery.
        } catch (IOException | RuntimeException e) {
            boolean first = entryStartPositions.isEmpty();
            monitor.transactionalLogRecordReadFailure(
                    first ? EMPTY_LONG_ARRAY : new long[] {entryStartPositions.getLast()}, first ? 0 : 1, logVersion);
            if (failOnCorruptedLogFiles) {
                throw e;
            }
        }

        if (unbridgedChannel.getLogVersion() != logVersion) {
            throw new IllegalArgumentException(
                    "The channel which was passed in bridged multiple log versions, it started at version " + logVersion
                            + ", but continued through to version " + unbridgedChannel.getLogVersion()
                            + ". This isn't supported");
        }

        return entryStartPositions.asReversed().longIterator();
    }

    @Override
    public boolean next() throws IOException {
        if (!offsets.hasNext()) {
            return false;
        }

        // If a tx split over several files was read, a new cursor starting on the correct file is needed.
        if (commandBatchCursor.position().getLogVersion() != logVersion) {
            resetToUnbridgedBatchCursor();
        }
        long next = offsets.next();
        try {
            currentChannel.setPositionUnsafe(next);
            if (!commandBatchCursor.next()) {
                resetToUnbridgedBatchCursor();
                // For a last broken entry it could have gotten the offset but then later found incomplete envelopes.
                // Need to continue to the previous one
                return next();
            }
        } catch (IllegalStateException | IOException | UnsupportedLogVersionException e) {
            // Since the content is never read while sketching out the offsets any corruption can be found at this point
            // This means batches after the corruption can already have been returned, but that should be fine since we
            // are in reverse recovery
            monitor.transactionalLogRecordReadFailure(new long[] {next}, 1, logVersion);
            if (failOnCorruptedLogFiles) {
                throw e;
            }
            resetToUnbridgedBatchCursor();
            return next();
        }
        currentCommandBatch = commandBatchCursor.get();
        return true;
    }

    private void resetToUnbridgedBatchCursor() throws IOException {
        if (unbridgedChannel != null) {
            commandBatchCursor.close();
            currentChannel = unbridgedChannel;
            unbridgedChannel = null;
            commandBatchCursor = new CommittedCommandBatchCursor(currentChannel, logEntryReader);
        }
    }

    @Override
    public void close() throws IOException {
        commandBatchCursor.close(); // closes the channel too
        if (unbridgedChannel != null) {
            unbridgedChannel.close();
        }
    }

    @Override
    public CommittedCommandBatchRepresentation get() {
        return currentCommandBatch;
    }

    @Override
    public LogPosition position() {
        return commandBatchCursor.position();
    }
}

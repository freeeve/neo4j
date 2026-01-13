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

import static org.neo4j.internal.helpers.Numbers.safeCastLongToInt;
import static org.neo4j.io.ByteUnit.kibiBytes;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader.UNSPECIFIED_INDEX;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader.UNSPECIFIED_TERM;
import static org.neo4j.kernel.impl.transaction.log.entry.TailUtils.checkTail;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_CHECKSUM;

import java.io.IOException;
import org.neo4j.io.fs.ReadPastEndException;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeaderReader;
import org.neo4j.kernel.impl.transaction.log.entry.TailUtils;
import org.neo4j.kernel.impl.transaction.log.entry.UnknownLogFormatException;
import org.neo4j.kernel.impl.transaction.log.entry.UnsupportedLogVersionException;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.memory.MemoryTracker;

public class EnvelopedLogTailChecker {
    private final LogsRepository logsRepository;
    private final EnvelopedReadChannelAllocator readChannelAllocator;
    private final MemoryTracker memoryTracker;
    private final InternalLog log;

    @FunctionalInterface
    public interface EnvelopedReadChannelAllocator {
        EnvelopeReadChannel envelopedReadChannel(long logFileVersion) throws IOException;
    }

    public EnvelopedLogTailChecker(
            LogsRepository logsRepository,
            EnvelopedReadChannelAllocator readChannelAllocator,
            MemoryTracker memoryTracker,
            InternalLogProvider logProvider) {
        this.logsRepository = logsRepository;
        this.readChannelAllocator = readChannelAllocator;
        this.memoryTracker = memoryTracker;
        this.log = logProvider.getLog(EnvelopedLogTailChecker.class);
    }

    public record EnvelopedLogTailInfo(
            LogPosition lastValidatedPosition,
            long lastValidAppendIndex,
            long lastValidTerm,
            int lastValidChecksum,
            int segmentOffset,
            boolean createInitial,
            boolean brokenLastEntry) {}

    public EnvelopedLogTailInfo checkEnvelopedLogTail() throws IOException {
        checkEnvelopedLogVersionSequence();
        long[] versions = logsRepository.logVersions(true);
        for (long version : versions) {
            try (EnvelopeReadChannel readChannel = readChannelAllocator.envelopedReadChannel(version)) {
                LogHeader logHeader = readChannel.logHeader();
                long lastValidAppendIndex = logHeader.getLastAppendIndex();
                long lastValidTerm = logHeader.getLastTerm();
                int lastValidChecksum = logHeader.getPreviousLogFileChecksum();
                LogPosition lastGoodPosition = null;
                boolean brokenLastEntry = false;
                try {
                    while (true) {
                        lastGoodPosition = readChannel.goToEndOfEntry();
                        lastValidAppendIndex = readChannel.entryIndex();
                        lastValidTerm = readChannel.currentTerm();
                        lastValidChecksum = readChannel.getChecksum();
                    }
                } catch (ReadPastEndException e) {
                    // reached end
                } catch (UnsupportedLogVersionException | IllegalStateException | InvalidLogEnvelopeReadException e) {
                    throw e;
                } catch (IncompleteEnvelopeReadException e) {
                    // This exception signals that the tail is already checked and the last entry is broken.
                    brokenLastEntry = true;
                } catch (IOException | RuntimeException e) {
                    LogPosition currentLogPosition = readChannel.getCurrentLogPosition();
                    // check if error was in the last entry, or is there anything else after that
                    checkTail(readChannel, currentLogPosition, e);
                    brokenLastEntry = true;
                }

                if (lastGoodPosition == null) { // haven't read a valid entry
                    if (version > versions[versions.length - 1]) {
                        // can go back further
                        continue;
                    }
                    // first entry we have is incomplete, so just preserve header values and rebuild
                    return new EnvelopedLogTailInfo(
                            new LogPosition(
                                    version, logHeader.getStartPosition().getByteOffset()),
                            logHeader.getLastAppendIndex(),
                            logHeader.getLastTerm(),
                            logHeader.getPreviousLogFileChecksum(),
                            0,
                            true,
                            false);
                }

                return new EnvelopedLogTailInfo(
                        lastGoodPosition,
                        lastValidAppendIndex,
                        lastValidTerm,
                        lastValidChecksum,
                        readChannel.getSegmentOffset(lastGoodPosition.getByteOffset()),
                        false,
                        brokenLastEntry);
            }
        }
        return new EnvelopedLogTailInfo(
                new LogPosition(0L, 0L), UNSPECIFIED_INDEX, UNSPECIFIED_TERM, BASE_TX_CHECKSUM, 0, true, false);
    }

    private void checkEnvelopedLogVersionSequence() throws IOException {
        long[] versions = logsRepository.logVersions(false);
        if (versions.length == 0) {
            return;
        }

        long expectedLogVersion = versions[0];
        long lastHeaderTerm = -1L;
        long lastHeaderAppendIndex = -1L;
        LogFormat lastLogFormat = LogFormat.V10;

        for (int index = 0; index < versions.length; index++) {
            long version = versions[index];
            if (version != expectedLogVersion) { // file name sequence must be complete
                throw new InconsistentLogFilesException("Missing log file: "
                        + logsRepository.pathFor(expectedLogVersion) + " expected for version " + expectedLogVersion);
            }
            try (LogChannelContext<StoreChannel> channel = logsRepository.openReadChannel(version)) {
                LogHeader logHeader;
                try {
                    logHeader = LogHeaderReader.readLogHeader(channel.channel(), true, channel.path(), memoryTracker);
                } catch (UnknownLogFormatException e) {
                    throw new IllegalStateException(
                            "Log File: " + logsRepository.pathFor(version) + " doesn't have a recognised LogFormat", e);
                } catch (IOException e) {
                    logHeader = null;
                }
                if (logHeader == null) {
                    // preallocated file, or corrupt?
                    TailUtils.checkNonZerosAfterOffset(
                            LogFormat.BIGGEST_HEADER,
                            channel.channel(),
                            memoryTracker,
                            safeCastLongToInt(kibiBytes(64)),
                            true,
                            (offset, data) -> {
                                throw new IllegalStateException(
                                        "Log File: " + logsRepository.pathFor(version)
                                                + " doesn't have a valid LogHeader, but also contains non-zero data and may be corrupted");
                            });
                    if (index != versions.length - 1) {
                        // only last file can have a partial header
                        throw new InconsistentLogFilesException("Log File: " + logsRepository.pathFor(version)
                                + " has incomplete header, or is preallocated, but is not last in the log file sequence ending with version "
                                + versions[versions.length - 1]);
                    }

                    log.info("Removing last partial header/preallocated log file: " + logsRepository.pathFor(version));
                    // truncate away the additional file to stop it causing problems later
                    logsRepository.deleteLogFilesFrom(version);
                    return;
                }

                // must be enveloped
                if (!logHeader.getLogFormatVersion().usesSegments()) {
                    throw new InconsistentLogFilesException(
                            "Log File: " + logsRepository.pathFor(version) + " is not using Envelopes as required");
                }

                // No format downgrades allowed
                if (logHeader.getLogFormatVersion().getVersionByte() < lastLogFormat.getVersionByte()) {
                    throw new InconsistentLogFilesException("Log File: " + logsRepository.pathFor(version)
                            + " uses LogFormat: " + logHeader.getLogFormatVersion()
                            + " but previous file used higher LogFormat: " + lastLogFormat);
                }
                lastLogFormat = logHeader.getLogFormatVersion();

                // Enveloped files must contain at least the header segment
                if (channel.channel().size() < logHeader.getSegmentBlockSize()) {
                    throw new IllegalStateException("Log File: " + logsRepository.pathFor(version)
                            + " does not contain a complete initial segment");
                }

                // we require matching header log version
                if (logHeader.getLogVersion() != expectedLogVersion) {
                    throw new InconsistentLogFilesException(
                            "Log File: " + logsRepository.pathFor(version) + " contains header " + logHeader
                                    + " with mismatched logVersion. Expected: " + expectedLogVersion);
                }

                // appendIndex must be increasing
                if (logHeader.getLastAppendIndex() < lastHeaderAppendIndex) {
                    throw new InconsistentLogFilesException("Log File: " + logsRepository.pathFor(version)
                            + " has lower previousAppendIndex: " + logHeader.getLastAppendIndex()
                            + " than previous file with header appendIndex: " + lastHeaderAppendIndex);
                }
                lastHeaderAppendIndex = logHeader.getLastAppendIndex();

                // term must be increasing
                if (logHeader.getLastTerm() < lastHeaderTerm) {
                    throw new InconsistentLogFilesException("Log File: " + logsRepository.pathFor(version)
                            + " has lower previousTerm: " + logHeader.getLastTerm()
                            + " than previous file with header previousTerm: " + lastHeaderTerm);
                }
                lastHeaderTerm = logHeader.getLastTerm();
            }
            ++expectedLogVersion;
        }
    }
}

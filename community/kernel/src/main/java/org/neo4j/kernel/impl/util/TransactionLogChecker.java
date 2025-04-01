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
package org.neo4j.kernel.impl.util;

import static org.neo4j.kernel.impl.util.TransactionLogChecker.FileType.CHECKPOINT_LOG;
import static org.neo4j.kernel.impl.util.TransactionLogChecker.FileType.TX_LOG;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.io.IOException;
import java.util.Optional;
import org.neo4j.configuration.Config;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.ReadPastEndException;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.BinarySupportedKernelVersions;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.KernelVersionProvider;
import org.neo4j.kernel.impl.transaction.log.LogVersionBridge;
import org.neo4j.kernel.impl.transaction.log.LogVersionedStoreChannel;
import org.neo4j.kernel.impl.transaction.log.PhysicalLogVersionedStoreChannel;
import org.neo4j.kernel.impl.transaction.log.ReadableLogChannel;
import org.neo4j.kernel.impl.transaction.log.entry.AbstractVersionAwareLogEntry;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntry;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryReader;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeaderReader;
import org.neo4j.kernel.impl.transaction.log.entry.VersionAwareLogEntryReader;
import org.neo4j.kernel.impl.transaction.log.enveloped.EnvelopeReadChannel;
import org.neo4j.kernel.impl.transaction.log.files.LogFiles;
import org.neo4j.kernel.impl.transaction.log.files.LogFilesBuilder;
import org.neo4j.kernel.impl.transaction.log.files.VersionedFile;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.CommandReaderFactory;
import org.neo4j.storageengine.api.StorageEngineFactory;

public class TransactionLogChecker {
    private TransactionLogChecker() {}

    /**
     * Verify that the transaction log content and headers are correct in respect to
     * version changes and rotations. Each file should only contain transactions of a single kernel version.
     */
    public static void verifyCorrectTransactionLogUpgrades(
            FileSystemAbstraction fs, DatabaseLayout layout, Config config)
            throws IOException, InconsistentTransactionLogException {
        LogFiles logFiles = LogFilesBuilder.readOnlyBuilder(layout, fs, KernelVersionProvider.THROWING_PROVIDER)
                .build();

        Optional<StorageEngineFactory> storageEngineFactory = StorageEngineFactory.selectStorageEngine(fs, layout);
        CommandReaderFactory commandReaderFactory = storageEngineFactory
                .orElseThrow(() -> new IllegalStateException("Couldn't figure out storage engine from store files"))
                .commandReaderFactory();

        verifyLogFiles(fs, config, logFiles.getLogFile(), commandReaderFactory, TX_LOG);
        verifyLogFiles(fs, config, logFiles.getCheckpointFile(), commandReaderFactory, CHECKPOINT_LOG);
    }

    private static void verifyLogFiles(
            FileSystemAbstraction fs,
            Config config,
            VersionedFile logFile,
            CommandReaderFactory commandReaderFactory,
            FileType fileType)
            throws IOException {

        KernelVersion versionSeen = KernelVersion.EARLIEST;
        for (long i = logFile.getLowestLogVersion(); i <= logFile.getHighestLogVersion(); i++) {
            LogHeader logHeader = LogHeaderReader.readLogHeader(fs, logFile.getLogFileForVersion(i), INSTANCE);
            if (logHeader == null) {
                throw new InconsistentTransactionLogException(
                        "Could not read log header of %s file version %d".formatted(fileType.lowerCase, i));
            }

            KernelVersion logHeaderKernelVersion = logHeader.getKernelVersion();
            if (versionLessThan(logHeaderKernelVersion, versionSeen)) {
                throw new InconsistentTransactionLogException(
                        "%s file version %d contains entry with lower kernel version (%s) than version seen in file with version %d (%s)"
                                .formatted(
                                        fileType.capitalized,
                                        i,
                                        logHeaderKernelVersion.name(),
                                        i - 1,
                                        versionSeen.name()));
            }

            versionSeen = verifyVersionInOneFile(
                    logHeader, logFile, logHeaderKernelVersion, versionSeen, commandReaderFactory, config, fileType);
        }
    }

    private static KernelVersion verifyVersionInOneFile(
            LogHeader logHeader,
            VersionedFile logFile,
            KernelVersion logHeaderKernelVersion,
            KernelVersion previouslySeenVersion,
            CommandReaderFactory commandReaderFactory,
            Config config,
            FileType fileType)
            throws IOException {
        KernelVersion versionSeenInFile = logHeader.getLogFormatVersion().usesSegments()
                ? verifyVersionInSegmentedFile(logFile, logHeader, logHeaderKernelVersion, fileType)
                : verifyVersionInOldFile(
                        logFile, logHeader, logHeaderKernelVersion, commandReaderFactory, config, fileType);

        // If there was no version in the header we have only checked that the file contains a single version so far.
        // Let's check that the version in the file is at least as great as the version seen in the previous file.
        if (logHeaderKernelVersion == null && versionLessThan(versionSeenInFile, previouslySeenVersion)) {
            throw new InconsistentTransactionLogException(
                    "%s file version %d contains entry with lower kernel version (%s) than version seen in previous file (%s)"
                            .formatted(
                                    fileType.capitalized,
                                    logHeader.getLogVersion(),
                                    versionSeenInFile.name(),
                                    previouslySeenVersion.name()));
        }
        // File with just header is allowed. Keep the latest version we've seen for further comparisons if that happens.
        return versionSeenInFile != null
                ? versionSeenInFile
                : (logHeaderKernelVersion != null ? logHeaderKernelVersion : previouslySeenVersion);
    }

    private static boolean versionLessThan(KernelVersion version, KernelVersion comparable) {
        return version != null && comparable != null && version.isLessThan(comparable);
    }

    private static KernelVersion verifyVersionInSegmentedFile(
            VersionedFile logFile, LogHeader logHeader, KernelVersion expectedVersion, FileType fileType)
            throws IOException {
        PhysicalLogVersionedStoreChannel logChannel = logFile.openForVersion(logHeader.getLogVersion());
        logChannel.position(logHeader.getStartPosition().getByteOffset());

        try (VersionCheckingEnvelopeReadChannel versionCheckingEnvelopeReadChannel =
                new VersionCheckingEnvelopeReadChannel(
                        logChannel,
                        logHeader.getSegmentBlockSize(),
                        LogVersionBridge.NO_MORE_CHANNELS,
                        INSTANCE,
                        false,
                        expectedVersion,
                        fileType)) {

            if (findEnvelopeVersionErrors(versionCheckingEnvelopeReadChannel)) {
                throw new InconsistentTransactionLogException("%s file version %d malformed, could not read until end"
                        .formatted(fileType.capitalized, logHeader.getLogVersion()));
            }
            return versionCheckingEnvelopeReadChannel.expectedVersion;
        }
    }

    private static boolean findEnvelopeVersionErrors(
            VersionCheckingEnvelopeReadChannel versionCheckingEnvelopeReadChannel) throws IOException {
        long prevPos = -1;
        long pos;
        try {
            while (prevPos < (pos = versionCheckingEnvelopeReadChannel.goToNextEntry())) {
                prevPos = pos;
            }
        } catch (ReadPastEndException e) {
            // Got to the end - good
            return false;
        }
        return true;
    }

    private static KernelVersion verifyVersionInOldFile(
            VersionedFile logFile,
            LogHeader logHeader,
            KernelVersion expectedVersion,
            CommandReaderFactory commandReaderFactory,
            Config config,
            FileType fileType)
            throws IOException {
        KernelVersion seenVersion = expectedVersion;
        try (ReadableLogChannel reader =
                logFile.getReader(logHeader.getStartPosition(), LogVersionBridge.NO_MORE_CHANNELS)) {
            LogEntryReader entryReader = new VersionAwareLogEntryReader(
                    commandReaderFactory, new BinarySupportedKernelVersions(config), INSTANCE);

            LogEntry entry;
            while ((entry = entryReader.readLogEntry(reader)) != null) {
                if (entry instanceof AbstractVersionAwareLogEntry versionedEntry) {
                    KernelVersion startVersion = versionedEntry.kernelVersion();
                    if (seenVersion == null) {
                        seenVersion = startVersion;
                    } else if (seenVersion != startVersion) {
                        throw new InconsistentTransactionLogException(
                                "%s file version %d contains entry with other kernel version (%s) than version seen earlier in the file (%s)"
                                        .formatted(
                                                fileType.capitalized,
                                                logHeader.getLogVersion(),
                                                startVersion.name(),
                                                seenVersion.name()));
                    }
                }
            }
        }
        return seenVersion;
    }

    static class VersionCheckingEnvelopeReadChannel extends EnvelopeReadChannel {

        private final long logVersion;
        private KernelVersion expectedVersion;
        private byte expectedVersionByte;
        private final FileType fileType;

        protected VersionCheckingEnvelopeReadChannel(
                LogVersionedStoreChannel startingChannel,
                int segmentBlockSize,
                LogVersionBridge bridge,
                MemoryTracker memoryTracker,
                boolean raw,
                KernelVersion expectedVersion,
                FileType fileType)
                throws IOException {
            super(startingChannel, segmentBlockSize, bridge, memoryTracker, raw);
            this.logVersion = startingChannel.getLogVersion();
            this.expectedVersion = expectedVersion;
            this.expectedVersionByte = expectedVersion != null ? expectedVersion.version() : -1;
            this.fileType = fileType;
        }

        @Override
        protected void readEnvelopeHeader() throws IOException {
            super.readEnvelopeHeader();
            if (expectedVersion == null) {
                expectedVersion = KernelVersion.getForVersion(payloadVersion);
                expectedVersionByte = payloadVersion;
            } else if (expectedVersionByte != payloadVersion) {
                throw new InconsistentTransactionLogException(
                        "%s file version %d contains entry with other kernel version (%s) than version seen earlier in the file (%s)"
                                .formatted(
                                        fileType.capitalized,
                                        logVersion,
                                        KernelVersion.getForVersion(payloadVersion)
                                                .name(),
                                        expectedVersion.name()));
            }
        }
    }

    enum FileType {
        TX_LOG("Log", "log"),
        CHECKPOINT_LOG("Checkpoint", "checkpoint");

        final String capitalized;
        final String lowerCase;

        FileType(String capitalized, String lowerCase) {
            this.capitalized = capitalized;
            this.lowerCase = lowerCase;
        }
    }
}

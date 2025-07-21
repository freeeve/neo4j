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
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.ReadPastEndException;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.memory.HeapScopedBuffer;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.ChannelNativeAccessor;
import org.neo4j.kernel.impl.transaction.log.LogTracers;
import org.neo4j.kernel.impl.transaction.log.LogVersionBridge;
import org.neo4j.kernel.impl.transaction.log.LogVersionedStoreChannel;
import org.neo4j.kernel.impl.transaction.log.PhysicalLogVersionedStoreChannel;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.kernel.impl.transaction.log.rotation.LogRotateEvents;
import org.neo4j.kernel.impl.transaction.log.rotation.LogRotation;
import org.neo4j.memory.MemoryTracker;

public class EnvelopedLogFiles implements EnvelopeReadChannelProvider, AutoCloseable {

    public static final int INITIAL_CHECKSUM = 0; // TODO: change later
    public static final long BASE_INDEX = 0;
    public static final int MINIMUM_SEGMENTS = 2;
    private final int segmentBlockSize;
    private final int totalSegments;
    private final int writerBufferedBlocks;
    private final MemoryTracker memoryTracker;
    private final LogRotation logRotation;
    private final LogTracers logTracers = LogTracers.NULL; // TODO: Use these when log is merged
    private final LogsRepository logsRepository;
    private final long maxFileSize;
    private final LogHeaderFactory logHeaderFactory;
    private final LogFilesPruner logFilesPruner;
    private final LogFilesPreAllocator logFilesPreAllocator;
    private LogChannelContext<StoreChannel> currentWriteChannel;
    private EnvelopeWriteChannel appendingChannel;

    public EnvelopedLogFiles(
            FileSystemAbstraction fs,
            Path directory,
            String baseFileName,
            LogHeaderFactory logHeaderFactory,
            int segmentBlockSize,
            int writerBufferedBlocks,
            int totalSegments,
            MemoryTracker memoryTracker,
            PruneStrategy pruneStrategy,
            LogFilesPreAllocator logFilesPreAllocator) {
        if (totalSegments < MINIMUM_SEGMENTS) {
            throw new IllegalArgumentException(
                    String.format("Must have at least %d segments. Got %d", MINIMUM_SEGMENTS, totalSegments));
        }
        this.logFilesPreAllocator = logFilesPreAllocator;
        this.logHeaderFactory = logHeaderFactory;
        this.logsRepository = new LogsRepository(fs, directory, baseFileName);
        this.segmentBlockSize = segmentBlockSize;
        this.totalSegments = totalSegments;
        this.writerBufferedBlocks = writerBufferedBlocks;
        this.memoryTracker = memoryTracker;
        this.maxFileSize = totalSegments * (long) segmentBlockSize;
        this.logRotation = new EnvelopedLogRotation(this, maxFileSize);
        this.logFilesPruner = new LogFilesPruner(logsRepository, pruneStrategy);
    }

    public EnvelopeWriteChannel currentWriteChannel() {
        if (appendingChannel == null) {
            throw new IllegalStateException("Writer channel has not been initialised");
        }
        return appendingChannel;
    }

    @Override
    public EnvelopeReadChannel openReadChannel() throws IOException {
        if (logsRepository.isEmpty()) {
            throw new IllegalStateException("No log files found " + logsRepository);
        }
        var version = logsRepository.logVersions(false)[0];
        return envelopedReadChannel(logsRepository.openReadChannel(version), version);
    }

    @Override
    public EnvelopeReadChannel openReadChannel(long entryIndex) throws IOException {
        var longRange = logsRepository.logVersionsRange();

        if (!longRange.isEmpty()) {
            var logFileBinarySearch = new LogFileBinarySearch(
                    logsRepository, longRange.from(), longRange.to() - longRange.from() + 1, memoryTracker);
            var fileVersion = LogBinarySearch.binarySearch(logFileBinarySearch, entryIndex);

            if (fileVersion != -1) {
                return envelopedReadChannel(logsRepository.openReadChannel(fileVersion), fileVersion);
            }
        }
        return null;
    }

    public long initialise() throws IOException {
        logsRepository.initialise();
        if (!logsRepository.isEmpty()) {
            var versions = logsRepository.logVersions(true);
            for (var version : versions) {
                try (var readChannel = envelopedReadChannel(logsRepository.openReadChannel(version), version)) {
                    if (readChannel.logHeader() != null) {
                        try {
                            while (true) {
                                readChannel.goToNextEntry();
                            }
                        } catch (ReadPastEndException ignore) {
                            // we reached the end
                        }
                        var prevChecksum = readChannel.getChecksum();
                        var logChannelCtx = openWriteChannel(readChannel.getLogVersion(), readChannel.position());

                        var isLogBoundary = readChannel.entryIndex() == -1;
                        var latestLogIndex =
                                isLogBoundary ? readChannel.logHeader().getLastAppendIndex() : readChannel.entryIndex();

                        updateState(logChannelCtx, prevChecksum, latestLogIndex);
                        return latestLogIndex;
                    }
                }
            }
        }
        // no existing data found, either no log files or only pre-allocated logs
        var logChannelCtx = createNewStoreChannel(
                LogsRepository.BASE_VERSION,
                logHeaderFactory.createLogHeader(LogsRepository.BASE_VERSION, -1, INITIAL_CHECKSUM, segmentBlockSize));
        updateState(logChannelCtx, INITIAL_CHECKSUM, BASE_INDEX - 1);
        return -1;
    }

    /**
     * Truncates the envelope log files.
     *
     * @param fromIndex the index to truncate from (inclusive)
     * @return the current write channel after the truncate.
     * @throws IllegalArgumentException if fromIndex is negative, higher than current index or if it has been pruned
     */
    public EnvelopeWriteChannel truncate(long fromIndex) throws IOException {
        if (fromIndex < 0) {
            throw new IllegalArgumentException("Negative values is not allowed " + fromIndex);
        }
        long lastAppendedIndex = appendingChannel == null ? -1 : appendingChannel.currentIndex();
        if (appendingChannel != null && fromIndex > lastAppendedIndex) {
            throw new IllegalArgumentException(
                    "Cannot truncate at index " + fromIndex + " when last appended index is " + lastAppendedIndex);
        }

        long position;
        long version;
        int prevChecksum;
        long prevTerm;
        int offset;
        try (var readChannel = openReadChannel(fromIndex)) {
            if (readChannel == null) {
                throw new IllegalArgumentException(fromIndex + " has been pruned");
            }
            version = readChannel.getLogVersion();
            prevTerm = readChannel.currentTerm();
            position = readChannel.position();
            prevChecksum = readChannel.logHeader().getPreviousLogFileChecksum();
            while (readChannel.entryIndex() < fromIndex) {
                prevChecksum = readChannel.getChecksum();
                prevTerm = readChannel.currentTerm();
                version = readChannel.getLogVersion();
                position = readChannel.goToNextEnvelope();
            }
            offset = readChannel.getSegmentOffset(position);
        }
        if (currentWriteChannel.version() != version) {
            appendingChannel = null;
            currentWriteChannel.channel().close();
            currentWriteChannel = null;
            logsRepository.deleteLogFilesFrom(version + 1);
            currentWriteChannel = openWriteChannel(version, position);
            appendingChannel = envelopedWriteChannel(currentWriteChannel, -1, Integer.MAX_VALUE);
        }
        appendingChannel.truncateToPosition(position, prevChecksum, fromIndex - 1, prevTerm);
        if (offset > 0) {
            appendingChannel.insertStartOffset(offset);
        }
        appendingChannel.prepareForFlush().flush();
        return appendingChannel;
    }

    /**
     * Truncates the envelope log files after the last written entry.
     * No written entries are truncated, this should be called only
     * when we want to free the pre-allocated data in the log.
     */
    public void forceRotate() throws IOException {
        appendingChannel.truncateToPosition(
                appendingChannel.position(),
                appendingChannel.currentChecksum(),
                appendingChannel.currentIndex(),
                appendingChannel.currentTerm());
    }

    /**
     * Skip in the log to the desired index. This will delete all log files and create a new empty file with a header
     * pointing to the provided index and checksum.
     * <p>
     * This method only makes sense if the log is distributed as it is effectively creating a new log starting at
     * some higher entry. Henece only the "other log" can provide the correct context for the initial state of
     * the new log.
     *
     * @param index    the index to skip to. This will be the log header index and the next written entry will be index+1
     * @param checksum the checksum for the provided index.
     * @param offset   the offset for the provided index.
     */
    public void skip(long index, int checksum, int offset) throws IOException {
        if (index > appendingChannel.currentIndex()) {
            var prunedVersion = logsRepository.logVersionsRange().to();
            logsRepository.deleteLogFilesTo(prunedVersion);
            long nextVersion = prunedVersion + 1;
            var newStoreChannel = createNewStoreChannel(
                    nextVersion, logHeaderFactory.createLogHeader(nextVersion, index, checksum, segmentBlockSize));
            updateState(newStoreChannel, checksum, index);
            if (offset > 0) {
                currentWriteChannel().insertStartOffset(offset);
                currentWriteChannel().prepareForFlush().flush();
            }
        }
    }

    /**
     * Prunes the envelope files. Only entire files can be removed by prune.
     *
     * @param index the desired index to prune up to (exclusive)
     * @return the highest index that was removed after the prune event, or -1 if there is nothing to prune
     */
    public long prune(long index) throws IOException {
        long versionToPrune;
        try (var reader = openReadChannel(index)) {
            if (reader == null) {
                return -1; // index already pruned
            }
            var logVersion = reader.getLogVersion();
            versionToPrune = logVersion - 1;
            if (!logsRepository.logVersionsRange().isWithinRange(versionToPrune)) {
                return -1;
            }
        }
        var envelopeWriteChannel = currentWriteChannel();
        var prunedVersion = logFilesPruner.pruneUpTo(
                versionToPrune,
                envelopeWriteChannel.currentIndex(),
                envelopeWriteChannel.position(),
                currentWriteChannel.version());
        if (prunedVersion == -1) {
            return -1;
        }
        assert !logsRepository.isEmpty();
        var logFilesMetadata = logFilesMetadata();
        logFilesMetadata.next();
        return logFilesMetadata.get().logHeader().getLastAppendIndex();
    }

    private void rotateCurrentFile(long lastAppendIndex, int checksum) throws IOException {
        if (appendingChannel == null) {
            throw new IllegalStateException("Cannot rotate if not initialised");
        } else {
            var nextVersion = currentWriteChannel.version() + 1;
            var newStoreChannel = createNewStoreChannel(
                    nextVersion,
                    logHeaderFactory.createLogHeader(nextVersion, lastAppendIndex, checksum, segmentBlockSize));
            appendingChannel.prepareForFlush().flush();
            currentWriteChannel.channel().truncate(currentWriteChannel.channel().position());
            updateState(newStoreChannel, checksum, lastAppendIndex);
        }
    }

    @Override
    public void close() throws IOException {
        if (appendingChannel != null) {
            appendingChannel.close();
            currentWriteChannel = null;
        }
    }

    private void updateState(LogChannelContext<StoreChannel> logChannelCtx, int checksumAtPosition, long prevIndex)
            throws IOException {
        if (appendingChannel == null) {
            appendingChannel = envelopedWriteChannel(logChannelCtx, checksumAtPosition, prevIndex);
        } else {
            appendingChannel.prepareForFlush().flush();
            currentWriteChannel.channel().flush();
            if (prevIndex != appendingChannel.currentIndex()) {
                appendingChannel = envelopedWriteChannel(logChannelCtx, checksumAtPosition, prevIndex);
            } else {
                appendingChannel.setChannel(logChannelCtx.channel());
            }
            currentWriteChannel.channel().close();
        }
        currentWriteChannel = logChannelCtx;
    }

    private LogChannelContext<StoreChannel> createNewStoreChannel(long version, LogHeader logHeader)
            throws IOException {
        var logChannelCtx = logsRepository.createWriteChannel(version);
        logFilesPreAllocator.preAllocateLogFile(logChannelCtx, maxFileSize);
        logChannelCtx.channel().position(0);
        LogFormat.writeLogHeader(logChannelCtx.channel(), logHeader, memoryTracker);
        logChannelCtx.channel().flush(); // ensure header and metadata is flushed to disk
        logChannelCtx.channel().position(segmentBlockSize);
        return logChannelCtx;
    }

    private LogChannelContext<StoreChannel> openWriteChannel(long version, long position) throws IOException {
        var logChannelCtx = logsRepository.openWriteChannel(version);
        position = position == 0 ? segmentBlockSize : position;
        logChannelCtx.channel().position(position);
        return logChannelCtx;
    }

    private EnvelopeWriteChannel envelopedWriteChannel(
            LogChannelContext<StoreChannel> logChannelCtx, int checksumAtPosition, long prevIndex) throws IOException {
        return new EnvelopeWriteChannel(
                logChannelCtx.channel(),
                scoopedBuffer(),
                segmentBlockSize,
                checksumAtPosition,
                prevIndex,
                logTracers,
                logRotation);
    }

    private HeapScopedBuffer scoopedBuffer() {
        return new HeapScopedBuffer(writerBufferedBlocks * segmentBlockSize, ByteOrder.LITTLE_ENDIAN, memoryTracker);
    }

    EnvelopeReadChannel envelopedReadChannel(LogChannelContext<StoreChannel> logChannelCtx, long version)
            throws IOException {
        var logVersionedChannel = logVersionedChannel(logChannelCtx, version);
        return new EnvelopeReadChannel(
                logVersionedChannel,
                segmentBlockSize,
                totalSegments,
                new EnvelopedLogVersionBridge(this),
                memoryTracker,
                false);
    }

    private PhysicalLogVersionedStoreChannel logVersionedChannel(
            LogChannelContext<StoreChannel> logChannelCtx, long version) throws IOException {
        return new PhysicalLogVersionedStoreChannel(
                logChannelCtx.channel(),
                version,
                LogFormat.V9, // TODO Format version should be in the header of the log, can't know before!?
                logChannelCtx.path(),
                ChannelNativeAccessor.EMPTY_ACCESSOR, // TODO: should probably enable this
                logTracers);
    }

    private LogVersionedStoreChannel safeOpenChannel(long version) throws IOException {
        var longRange = logsRepository.logVersionsRange();
        if (longRange.isWithinRange(version)) {
            return logVersionedChannel(logsRepository.openReadChannel(version), version);
        }
        return null;
    }

    public LogFilesMetadata logFilesMetadata() throws IOException {
        return logFilesMetadata(false);
    }

    public LogFilesMetadata logFilesMetadata(boolean reversed) throws IOException {
        return new LogFilesMetadata(logsRepository, reversed);
    }

    public void remove() throws IOException {
        close();
        logsRepository.deleteLogFilesFrom(0);
    }

    private static class EnvelopedLogRotation implements LogRotation {
        private final EnvelopedLogFiles envelopedLogFiles;
        private final long maxFileSize;

        EnvelopedLogRotation(EnvelopedLogFiles envelopedLogFiles, long maxFileSize) {
            this.envelopedLogFiles = envelopedLogFiles;
            this.maxFileSize = maxFileSize;
        }

        @Override
        public boolean rotateLogIfNeeded(LogRotateEvents logRotateEvents) {
            throw new UnsupportedOperationException("envelope channel rotation checks are done internally");
        }

        @Override
        public boolean locklessBatchedRotateLogIfNeeded(
                LogRotateEvents logRotateEvents,
                long lastAppendIndex,
                KernelVersion kernelVersion,
                int checksum,
                LogFormat logFormat) {
            throw new UnsupportedOperationException("envelope channel rotation checks are done internally");
        }

        @Override
        public boolean locklessRotateLogIfNeeded(LogRotateEvents logRotateEvents) {
            return rotateLogIfNeeded(logRotateEvents);
        }

        @Override
        public boolean locklessRotateLogIfNeeded(
                LogRotateEvents logRotateEvents, KernelVersion kernelVersion, boolean force) {
            throw new UnsupportedOperationException("envelope channel rotation checks are done internally");
        }

        @Override
        public void rotateLogFile(LogRotateEvents logRotateEvents) throws IOException {
            throw new UnsupportedOperationException("envelope channel rotation checks are done internally");
        }

        @Override
        public void rotateLogFile(LogRotateEvents logRotateEvents, long lastAppendIndex, int previousChecksum)
                throws IOException {
            try (var event = logRotateEvents.beginLogRotate()) {
                envelopedLogFiles.rotateCurrentFile(lastAppendIndex, previousChecksum);
                event.rotationCompleted(0); // TODO add clock
            }
        }

        @Override
        public void locklessRotateLogFile(
                LogRotateEvents logRotateEvents,
                KernelVersion kernelVersion,
                long lastAppendIndex,
                int previousChecksum) {
            throw new UnsupportedOperationException("envelope channel rotation checks are done internally");
        }

        @Override
        public void locklessRotateLogFile(
                LogRotateEvents logRotateEvents,
                KernelVersion kernelVersion,
                long lastAppendIndex,
                int previousChecksum,
                LogFormat logFormat) {
            throw new UnsupportedOperationException("envelope channel rotation checks are done internally");
        }

        @Override
        public long rotationSize() {
            return maxFileSize;
        }
    }

    private static class EnvelopedLogVersionBridge implements LogVersionBridge {
        private final EnvelopedLogFiles envelopedLogFiles;

        public EnvelopedLogVersionBridge(EnvelopedLogFiles envelopedLogFiles) {

            this.envelopedLogFiles = envelopedLogFiles;
        }

        @Override
        public LogVersionedStoreChannel next(LogVersionedStoreChannel channel, boolean raw) throws IOException {
            var nextChannel = envelopedLogFiles.safeOpenChannel(channel.getLogVersion() + 1);
            if (nextChannel != null) {
                channel.close();
                return nextChannel;
            }
            return channel;
        }
    }
}

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
import java.nio.ByteBuffer;
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
    private final int segmentBlockSize;
    private final int writerBufferedBlocks;
    private final MemoryTracker memoryTracker;
    private final LogRotation logRotation;
    private final LogTracers logTracers = LogTracers.NULL; // TODO: Use these when log is merged
    private final LogsRepository logsRepository;
    private final long maxFileSize;
    private final LogHeaderFactory logHeaderFactory;
    private final LogFilesPruner logFilesPruner;
    private LogChannelContext<StoreChannel> currentWriteChannel;
    private EnvelopeWriteChannel appendingChannel;

    public EnvelopedLogFiles(
            FileSystemAbstraction fs,
            Path fileName,
            LogHeaderFactory logHeaderFactory,
            int segmentBlockSize,
            int writerBufferedBlocks,
            int totalSegments,
            MemoryTracker memoryTracker,
            PruneStrategy pruneStrategy) {
        if (totalSegments < 2) {
            throw new IllegalArgumentException("Must have at least 2 segments. Got " + totalSegments);
        }
        this.logHeaderFactory = logHeaderFactory;
        this.logsRepository = new LogsRepository(
                fs, fileName.getParent(), fileName.getFileName().toString());
        this.segmentBlockSize = segmentBlockSize;
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
    public EnvelopeReadChannel openReadChannel(long fileWithIndex) throws IOException {
        var logFileVersions = logsRepository.logVersions(false);
        int low = 0;
        int high = logFileVersions.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            long midVersion = logFileVersions[mid];
            LogHeader midLogHeader;
            try (var channel = logsRepository.openReadChannel(midVersion)) {
                midLogHeader = readLogHeader(channel.channel(), true, null, memoryTracker);
            }
            // null header means empty pre-allocated file
            long midVal = midLogHeader == null ? Long.MAX_VALUE : midLogHeader.getLastAppendIndex();
            if (midVal < fileWithIndex) {
                low = mid + 1;
            } else if (midVal > fileWithIndex) {
                high = mid - 1;
            } else {
                low = mid;
                break;
            }
        }
        if (low == 0) {
            // 0 means that the prev index has been pruned.
            return null;
        } else {
            // low will point to the version that either has the closest index higher than or equal to what we are
            // looking for. In either case, since logHeaders append index points to prev files last index, we want
            // the index before low
            // note! if the index has been truncated or is higher than what exists in the log, then we still get the
            // last file, but that should never have been attempted and should be caught higher up the stack.
            var fileVersion = logFileVersions[low - 1];
            return envelopedReadChannel(logsRepository.openReadChannel(fileVersion), fileVersion);
        }
    }

    public long initialise() throws IOException {
        logsRepository.initialise();
        if (!logsRepository.isEmpty()) {
            try (var envelopeReadChannel = openReadChannel()) {
                if (envelopeReadChannel.logHeader() != null) {

                    try {
                        while (true) {
                            envelopeReadChannel.goToNextEntry();
                        }
                    } catch (ReadPastEndException ignore) {
                        // we reached the end
                    }
                    var prevChecksum = envelopeReadChannel.getChecksum();
                    var prevIndex = envelopeReadChannel.entryIndex();
                    var logChannelCtx =
                            openWriteChannel(envelopeReadChannel.getLogVersion(), envelopeReadChannel.position());
                    updateState(logChannelCtx, prevChecksum, prevIndex);
                    return prevIndex;
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
        long currentTerm;
        try (var readChannel = openReadChannel(fromIndex)) {
            if (readChannel == null) {
                throw new IllegalArgumentException(fromIndex + " has been pruned");
            }
            currentTerm = readChannel.currentTerm();
            position = readChannel.position();
            prevChecksum = readChannel.logHeader().getPreviousLogFileChecksum();
            while (readChannel.entryIndex() < fromIndex) {
                prevChecksum = readChannel.getChecksum();
                position = readChannel.goToNextEnvelope();
                currentTerm = readChannel.currentTerm();
            }
            version = readChannel.getLogVersion();
        }
        if (currentWriteChannel.version() != version) {
            appendingChannel = null;
            currentWriteChannel.channel().close();
            currentWriteChannel = null;
            logsRepository.deleteLogFilesFrom(version + 1);
            currentWriteChannel = openWriteChannel(version, position);
            appendingChannel = envelopedWriteChannel(currentWriteChannel, -1, Integer.MAX_VALUE);
        }
        appendingChannel.truncateToPosition(position, prevChecksum, fromIndex - 1, currentTerm);
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
     * Prunes the envelope files. Only entire files can be removed by prune.
     * @param index the desired index to prune up to (exclusive)
     * @return the highest index that was removed after the prune event, or -1 if there is nothing to prune
     */
    public long prune(long index) throws IOException {
        long prunedVersion;
        if (index > appendingChannel.currentIndex()) {
            // ignoring pruning strategy since this index will create a gap in the log and there is therefor no
            // reason to store old log files.
            prunedVersion = logsRepository.logVersionsRange().to();
            logsRepository.deleteLogFilesTo(prunedVersion);
        } else {
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
            prunedVersion = logFilesPruner.pruneUpTo(
                    versionToPrune,
                    envelopeWriteChannel.currentIndex(),
                    envelopeWriteChannel.position(),
                    currentWriteChannel.version());
        }
        if (prunedVersion == -1) {
            return -1;
        }
        if (logsRepository.isEmpty()) {
            long nextVersion = prunedVersion + 1;
            var newStoreChannel = createNewStoreChannel(
                    nextVersion,
                    logHeaderFactory.createLogHeader(nextVersion, index, INITIAL_CHECKSUM, segmentBlockSize));
            updateState(
                    newStoreChannel,
                    INITIAL_CHECKSUM,
                    index); // TODO: this will reset the checksum which will not work. If we prune the log past current
            // index we must provide a checksum. In Raft, this can be provided by the snapshot
            return index;
        } else {
            try (var reader = openReadChannel()) {
                return reader.logHeader().getLastAppendIndex();
            }
        }
    }

    private void rotateCurrentFile() throws IOException {
        if (appendingChannel == null) {
            throw new IllegalStateException("Cannot rotate if not initialised");
        } else {
            var nextVersion = currentWriteChannel.version() + 1;
            var newStoreChannel = createNewStoreChannel(
                    nextVersion,
                    logHeaderFactory.createLogHeader(
                            nextVersion,
                            appendingChannel.currentIndex(),
                            appendingChannel.currentChecksum(),
                            segmentBlockSize));
            appendingChannel.prepareForFlush().flush();
            currentWriteChannel.channel().truncate(currentWriteChannel.channel().position());
            updateState(newStoreChannel, appendingChannel.currentChecksum(), appendingChannel.currentIndex());
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
        preallocate(logChannelCtx);
        logChannelCtx.channel().position(0);
        LogFormat.writeLogHeader(logChannelCtx.channel(), logHeader, memoryTracker);
        logChannelCtx.channel().flush(); // ensure header and metadata is flushed to disk
        logChannelCtx.channel().position(segmentBlockSize);
        return logChannelCtx;
    }

    private void preallocate(LogChannelContext<StoreChannel> logChannelCtx) throws IOException {
        // TODO - Ugly pre-allocation
        if (logChannelCtx.channel().size() == maxFileSize) {
            // already pre-allocated
            return;
        }
        var buffer = ByteBuffer.wrap(new byte[segmentBlockSize]);
        long preallocated = 0;
        while (preallocated != maxFileSize) {
            buffer.position(0);
            logChannelCtx.channel().write(buffer);
            preallocated += segmentBlockSize;
        }
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
                logVersionedChannel, segmentBlockSize, new EnvelopedLogVersionBridge(this), memoryTracker, false);
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
                LogRotateEvents logRotateEvents, long lastAppendIndex, KernelVersion kernelVersion, int checksum) {
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
            try (var event = logRotateEvents.beginLogRotate()) {
                envelopedLogFiles.rotateCurrentFile();
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

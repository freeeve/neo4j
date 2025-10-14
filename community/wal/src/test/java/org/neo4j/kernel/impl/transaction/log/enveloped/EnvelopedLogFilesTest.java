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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.neo4j.kernel.impl.transaction.log.enveloped.LogsRepository.BASE_VERSION;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.LongRange;
import org.neo4j.internal.helpers.collection.Pair;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.ReadPastEndException;
import org.neo4j.kernel.DatabaseVersion;
import org.neo4j.kernel.impl.transaction.log.LogPositionMarker;
import org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader;
import org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader.EnvelopeType;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@RandomSupportExtension
class EnvelopedLogFilesTest {

    @Inject
    RandomSupport randomSupport;

    private static final String EIGHT_BYTES_MESSAGE = "message!";
    public static final int writeBufferedBlocks = 2;
    private final int segmentBlockSize = 256;
    private final int totalSegments = 3;
    private final int totalFileDataSize = segmentBlockSize * (totalSegments - 1);
    private PruneStrategy pruneStrategy = PruneStrategy.ALWAYS_PRUNE;

    @Inject
    TestDirectory testDirectory;

    @Inject
    FileSystemAbstraction fs;

    private EnvelopedLogFiles envelopedLogFiles;

    private LogsRepository mirroringRepository;

    private static void writeData(EnvelopeWriteChannel writeChannel, byte[] data) throws IOException {
        writeData(writeChannel, data, -1);
    }

    private static void writeData(EnvelopeWriteChannel writeChannel, byte[] data, long term) throws IOException {
        writeChannel.beginChecksumForWriting();
        if (term >= 0) {
            writeChannel.putTerm(term);
        }
        writeChannel.putVersion(DatabaseVersion.V1.identifier());
        writeChannel.putContentType(LogEnvelopeHeader.KERNEL_CONTENT_TYPE);
        writeChannel.put(data, data.length);
        writeChannel.endCurrentEntry();
    }

    @BeforeEach
    void setUp() {
        recreateEnvelopedLogFiles();
    }

    private void recreateEnvelopedLogFiles() {

        var baseFileName = "raft-log";
        var baseFolder = testDirectory.directory("logsFolder");
        mirroringRepository = new LogsRepository(fs, baseFolder, baseFileName);
        envelopedLogFiles = new EnvelopedLogFiles(
                fs,
                baseFolder,
                baseFileName,
                (fileVersion, preFileIndex, preFileChecksum, segmentSize, lastTerm) -> LogFormat.fromByteVersion(
                                DatabaseVersion.V1.getLogFormatHeader())
                        .newRaftHeader(
                                fileVersion,
                                preFileIndex,
                                lastTerm,
                                StoreId.UNKNOWN,
                                segmentSize,
                                preFileChecksum,
                                DatabaseVersion.V1),
                segmentBlockSize,
                writeBufferedBlocks,
                totalSegments,
                EmptyMemoryTracker.INSTANCE,
                (currentEntry, currentOffset, currentLogFile) ->
                        pruneStrategy.newConstraint(currentEntry, currentOffset, currentLogFile),
                new LogFilesPreAllocator(NullLogProvider.getInstance()));
    }

    @AfterEach
    void tearDown() throws Exception {
        envelopedLogFiles.close();
    }

    @Test
    void shouldFailOnGettingChannelBeforeInitialise() {
        assertThrows(IllegalStateException.class, () -> envelopedLogFiles.currentWriteChannel());
    }

    @Test
    void shouldCreateNewFileWhenInitialising() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION);

        try (var reader = envelopedLogFiles.openReadChannel()) {
            assertThat(reader.getLogVersion()).isEqualTo(0);
            assertThat(reader.logHeader().getLogVersion()).isEqualTo(0);
        }
    }

    @Test
    void shouldReInitializeCorrectlyAfterRestart() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, data);
        writeData(writeChannel, data);
        writeChannel.prepareForFlush().flush();

        // when
        recreateEnvelopedLogFiles();
        var latestLogIndex = envelopedLogFiles.initialise();

        // then
        assertThat(latestLogIndex).isEqualTo(1);
    }

    @Test
    void shouldReInitializeCorrectlyAfterLogRotation() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, data); // index 0
        writeData(writeChannel, data); // index 1
        writeChannel.prepareForFlush().flush();

        envelopedLogFiles.forceRotate();
        writeData(writeChannel, data); // index 2
        writeData(writeChannel, data); // index 3
        writeChannel.prepareForFlush().flush();

        // when
        recreateEnvelopedLogFiles();
        var latestLogIndex = envelopedLogFiles.initialise();

        // then
        assertThat(latestLogIndex).isEqualTo(3);
    }

    @Test
    void shouldReInitializeCorrectlyIfAtLogBoundary() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, data); // index 0
        writeData(writeChannel, data); // index 1
        writeChannel.prepareForFlush().flush();

        envelopedLogFiles.forceRotate();
        writeChannel.prepareForFlush().flush();

        // when
        recreateEnvelopedLogFiles();
        var latestLogIndex = envelopedLogFiles.initialise();

        // then
        assertThat(latestLogIndex).isEqualTo(1);
    }

    @Test
    void shouldReInitializeCorrectlyWhenLastEntrySpansMultipleFiles() throws IOException {
        var smallData = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var largeData = new byte[(int) (segmentBlockSize * ((2 * totalSegments) - 0.5))];

        envelopedLogFiles.initialise();

        var writeChannel = envelopedLogFiles.currentWriteChannel();

        writeData(writeChannel, smallData); // index 0
        writeData(writeChannel, largeData); // index 1
        writeData(writeChannel, largeData); // index 2
        writeData(writeChannel, smallData); // index 3
        writeData(writeChannel, largeData); // index 4
        writeChannel.prepareForFlush().flush();

        // when
        recreateEnvelopedLogFiles();
        var latestLogIndex = envelopedLogFiles.initialise();

        // then
        assertThat(latestLogIndex).isEqualTo(4);
    }

    @Test
    void shouldReInitializeCorrectlyWithPreallocatedFiles() throws IOException {
        // given - preallocated files
        for (int i = 0; i < 3; i++) {
            try (var channel = mirroringRepository.createWriteChannel(i).channel()) {
                var zeros = ByteBuffer.wrap(new byte[segmentBlockSize]);
                channel.writeAll(zeros);
                zeros.position(0);
                channel.writeAll(zeros);
                zeros.position(0);
                channel.flush();
            }
        }

        envelopedLogFiles.initialise();
        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, data); // index 0
        writeData(writeChannel, data); // index 1
        writeData(writeChannel, data); // index 2
        writeChannel.prepareForFlush().flush();

        // when
        recreateEnvelopedLogFiles();
        var latestLogIndex = envelopedLogFiles.initialise();

        // then
        assertThat(latestLogIndex).isEqualTo(2);
    }

    @Test
    void shouldWriteAndReadData() throws IOException {
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, data);
        writeChannel.prepareForFlush().flush();

        var readData = new byte[data.length];
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            envelopeReadChannel.reReadSegment();
            envelopeReadChannel.get(readData, readData.length);
        }
        assertThat(data).isEqualTo(readData);
    }

    @Test
    void shouldReadCorrectlyFromFileStartingWithNotANewEnvelope() throws IOException {
        var smallData = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var largeData = new byte[(int) (segmentBlockSize * (totalSegments - 0.5))];

        envelopedLogFiles.initialise();

        var writeChannel = envelopedLogFiles.currentWriteChannel();

        writeData(writeChannel, largeData); // will cause rotation
        writeData(writeChannel, smallData); // first entry in new file
        writeChannel.prepareForFlush().flush();

        var readData = new byte[smallData.length];
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel(1)) {
            envelopeReadChannel.alignWithStartEntry();
            envelopeReadChannel.get(readData, readData.length);
        }
        assertThat(smallData).isEqualTo(readData);
    }

    @Test
    void shouldReadCorrectlyFromFileContainingEntrySpanningMultipleFiles() throws IOException {
        var smallData = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var largeData = new byte[(int) (segmentBlockSize * ((2 * totalSegments) - 0.5))];

        envelopedLogFiles.initialise();

        var writeChannel = envelopedLogFiles.currentWriteChannel();

        writeData(writeChannel, largeData);
        writeData(writeChannel, smallData);
        writeChannel.prepareForFlush().flush();

        var readData = new byte[smallData.length];
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel(1)) {
            envelopeReadChannel.alignWithStartEntry();
            envelopeReadChannel.get(readData, readData.length);
        }
        assertThat(smallData).isEqualTo(readData);
    }

    @Test
    void shouldHandlePreAllocatedFileWhenOpeningReadChannel() throws IOException {

        var baseData = new byte[segmentBlockSize / 3];

        envelopedLogFiles.initialise();

        var writeChannel = envelopedLogFiles.currentWriteChannel();

        int maxIndex = 0;
        while (mirroringRepository.logVersionsRange().to() < 10) {
            baseData[0] = (byte) maxIndex++; // set unique data index
            writeData(writeChannel, baseData);
        }
        writeChannel.prepareForFlush().flush();
        // rollback by one as new logic won't create a new file until it has to
        // Subsequent logic will overwrite that last envelope/file
        --maxIndex;
        // create pre-allocated files
        for (int i = 10; i < 20; i++) {
            try (var channel = mirroringRepository.createWriteChannel(i).channel()) {
                var zeros = ByteBuffer.wrap(new byte[segmentBlockSize]);
                for (int j = 0; j < totalSegments; ++j) {
                    channel.writeAll(zeros);
                    zeros.position(0);
                }
                channel.flush();
            }
        }

        for (int i = 0; i < maxIndex; i++) {
            try (var readChannel = envelopedLogFiles.openReadChannel(i)) {
                var currentLogHeader = readChannel.logHeader();
                var readData = new byte[baseData.length];
                while (readChannel.entryIndex() != i) {
                    readChannel.goToNextEntry();
                }
                readChannel.get(readData, readData.length);
                // assert it did not switch file
                assertThat(currentLogHeader).isEqualTo(readChannel.logHeader());
                // and that we found the expected entry
                assertThat(readData[0]).isEqualTo((byte) i);
            }
        }
    }

    @Test
    void shouldHandleOnlyPreAllocatedFilesWithLogHeaderMetadata() throws IOException {
        for (int i = 0; i < 2; i++) {
            try (var channel = mirroringRepository.createWriteChannel(i).channel()) {
                var zeros = ByteBuffer.wrap(new byte[segmentBlockSize]);
                channel.writeAll(zeros);
                zeros.position(0);
                channel.writeAll(zeros);
                zeros.position(0);
                channel.flush();
            }
        }

        envelopedLogFiles.initialise();
        var itr = envelopedLogFiles.logFilesMetadata();
        // envelopedLogFiles.initialise() will write a header to the first file
        assertThat(itr.next()).isTrue();
        var logHeaderMetadata = itr.get();
        assertThat(logHeaderMetadata.logHeader().getLastAppendIndex()).isEqualTo(-1);
        assertThat(logHeaderMetadata.version()).isEqualTo(0);

        // the next file exits but is an empty pre-allocatd file and should be ignore
        assertThat(itr.next()).isFalse();
    }

    @Test
    void shouldHandleOnlyPreAllocatedFiles() throws IOException {
        for (int i = 0; i < 2; i++) {
            try (var channel = mirroringRepository.createWriteChannel(i).channel()) {
                var zeros = ByteBuffer.wrap(new byte[segmentBlockSize]);
                channel.writeAll(zeros);
                zeros.position(0);
                channel.writeAll(zeros);
                zeros.position(0);
                channel.flush();
            }
        }

        envelopedLogFiles.initialise();
        assertThat(envelopedLogFiles.currentWriteChannel().currentIndex()).isEqualTo(-1);
        byte[] data = {1, 2, 3};
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();
        try (EnvelopeReadChannel channel = envelopedLogFiles.openReadChannel()) {
            readData(data, channel, 0);
        }
    }

    @Test
    void shouldRotateWhenWritingMoreThanFileSize() throws IOException {
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        var totalMessages = writeManyMessages(data);

        assertThat(mirroringRepository.logVersions(false)).hasSizeGreaterThan(2);

        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < totalMessages; i++) {
                var readData = new byte[data.length];
                envelopeReadChannel.get(readData, readData.length);
                assertThat(data).isEqualTo(readData);
            }
        }
    }

    @Test
    void shouldRotateWithCorrectHeaderState() throws IOException {
        envelopedLogFiles.initialise();

        int dataSize = segmentBlockSize - LogEnvelopeHeader.HEADER_SIZE - 4;
        var data = new byte[dataSize];
        writeData(envelopedLogFiles.currentWriteChannel(), data, 0); // file 1
        writeData(envelopedLogFiles.currentWriteChannel(), data, 1); // file 1
        writeData(envelopedLogFiles.currentWriteChannel(), data, 2); // file 2
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            envelopeReadChannel.alignWithStartEntry();
            var firstHeader = envelopeReadChannel.logHeader();
            envelopeReadChannel.goToNextEntry();
            assertThat(firstHeader.getLogVersion()).isEqualTo(envelopeReadChannel.getLogVersion());
            assertThat(envelopeReadChannel.entryIndex()).isEqualTo(1);

            envelopeReadChannel.goToNextEntry();
            var secondHeader = envelopeReadChannel.logHeader();
            assertThat(envelopeReadChannel.entryIndex()).isEqualTo(2);
            assertThat(secondHeader.getLogVersion()).isEqualTo(firstHeader.getLogVersion() + 1);

            assertThat(secondHeader.getLastAppendIndex()).isEqualTo(1);
            assertThat(secondHeader.getLastTerm()).isEqualTo(1);
        }
    }

    @Test
    void shouldReturnNullIfNoFileMatches() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION);

        try (var reader = envelopedLogFiles.openReadChannel(-5)) {
            assertThat(reader).isNull();
        }
    }

    @Test
    void shouldSetCorrectLogIndexInHeader() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var largeMessage = new byte[segmentBlockSize];
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, largeMessage); // 0, start in v0, end in v1
        writeData(writeChannel, largeMessage); // 1, start in v1, end in v2
        writeData(writeChannel, largeMessage); // 2, start in v2, end in v3
        writeData(writeChannel, largeMessage); // 3  start in v3, end in v4
        writeChannel.prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            reader.alignWithStartEntry();
            assertThat(reader.entryIndex()).isEqualTo(0); // points to first entry
            LogHeader currentLogHeader = null;
            for (int i = 0; i < 3; i++) {
                assertThat(reader.entryIndex()).isEqualTo(i);
                var logHeader = reader.logHeader();
                if (logHeader != currentLogHeader) {
                    currentLogHeader = logHeader;
                    int expectedPrevIndex = i - 1;
                    assertThat(logHeader).matches(metadata -> metadata.getLastAppendIndex() == expectedPrevIndex);
                }
                reader.goToNextEntry();
            }
        }
    }

    @Test
    void shouldOpenLowestExistingFile() throws IOException {
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes(StandardCharsets.UTF_8);
        writeManyMessages(data);

        assertThat(mirroringRepository.logVersions(false)).hasSizeGreaterThan(2);

        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            assertThat(envelopeReadChannel.getLogVersion()).isEqualTo(0);
        }

        mirroringRepository.deleteLogFilesTo(2);

        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            assertThat(envelopeReadChannel.getLogVersion()).isEqualTo(3);
        }
    }

    @Test
    void shouldNotPruneLogFilesContainingEntriesNotToPrune() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize / 2];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData); // spills over to next file
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION, BASE_VERSION + 1);

        // 1 is completed in first file, so we expect it to be removed
        assertThat(envelopedLogFiles.prune(2)).isEqualTo(1);

        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION + 1);

        try (var reader = envelopedLogFiles.openReadChannel()) {
            reader.alignWithStartEntry();
            for (int i = 2; i < 4; i++) {
                var readData = new byte[data.length];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(readData).containsExactly(data);
                assertThat(reader.entryIndex()).isEqualTo(i);
            }
        }
    }

    @Test
    void shouldNotPruneCurrentFileIfIndexIsSamesAsAhead() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION);

        assertThat(envelopedLogFiles.prune(
                        envelopedLogFiles.currentWriteChannel().currentIndex()))
                .isEqualTo(-1);

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < 3; i++) {
                var readData = new byte[data.length];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(readData).containsExactly(data);
                assertThat(reader.entryIndex()).isEqualTo(i);
            }
        }
    }

    @Test
    void shouldNotPruneFilesIfStrategyDoesNotAllow() throws IOException {
        pruneStrategy = PruneStrategy.NEVER_PRUNE;
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize / 2];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData); // spills over to next file
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION, BASE_VERSION + 1);

        assertThat(envelopedLogFiles.prune(3)).isEqualTo(-1);
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION, BASE_VERSION + 1);
    }

    @Test
    void shouldPruneFileIfStrategyDoesAllow() throws IOException {
        pruneStrategy = PruneStrategy.ALWAYS_PRUNE;
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize / 2];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData); // spills over to next file
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION, BASE_VERSION + 1);

        assertThat(envelopedLogFiles.prune(3)).isEqualTo(1);
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION + 1);
    }

    @Test
    void shouldNotPruneLogFiles() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION);

        assertThat(envelopedLogFiles.prune(1)).isEqualTo(-1);

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < 3; i++) {
                var readData = new byte[data.length];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(readData).containsExactly(data);
                assertThat(reader.entryIndex()).isEqualTo(i);
            }
        }
    }

    @Test
    void shouldNotPruneAgain() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize / 2];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData); // spills over to next file
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION, BASE_VERSION + 1);

        // 1 is completed in first file, so we expect it to be removed
        assertThat(envelopedLogFiles.prune(2)).isEqualTo(1);
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION + 1);
        assertThat(envelopedLogFiles.prune(2)).isEqualTo(-1);
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION + 1);
    }

    @Test
    void shouldNotNotThrowOnAlreadyPrunedIndex() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize / 2];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData); // spills over to next file
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        assertThat(mirroringRepository.isEmpty()).isFalse();
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION, BASE_VERSION + 1);

        // 1 is completed in first file, so we expect it to be removed
        assertThat(envelopedLogFiles.prune(2)).isEqualTo(1);
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION + 1);
        assertThat(envelopedLogFiles.prune(0)).isEqualTo(-1);
        assertThat(mirroringRepository.logVersions(false)).containsExactly(BASE_VERSION + 1);
    }

    @Test
    void shouldTruncateAndSetCorrectChecksum() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize - 20];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < 2; i++) {
                readData(largeData, reader, i);
            }
        }

        envelopedLogFiles.truncate(1);

        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            readData(largeData, reader, 0);
            for (int i = 1; i < 4; i++) {
                readData(data, reader, i);
            }
        }
    }

    private static void readData(byte[] expectedData, EnvelopeReadChannel reader, int expectedIndex)
            throws IOException {
        var readData = new byte[expectedData.length];
        reader.read(ByteBuffer.wrap(readData));
        assertThat(readData).isEqualTo(expectedData);
        assertThat(reader.entryIndex()).isEqualTo(expectedIndex);
    }

    @Test
    void shouldTruncateEntries() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var messagesBefore = new String[] {"beforeTruncate1", "beforeTruncate2", "beforeTruncate3"};
        var messagesAfter = new String[] {"afterTruncate1", "afterTruncate2"};
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, messagesBefore[0].getBytes(), 0);
        writeData(writeChannel, messagesBefore[1].getBytes(), 1);
        writeData(writeChannel, messagesBefore[2].getBytes(), 2);
        writeChannel.prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < 3; i++) {
                var message = messagesBefore[i];
                var readData = new byte[message.length()];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(new String(readData)).isEqualTo(message);
                assertThat(reader.entryIndex()).isEqualTo(i);
            }
        }

        envelopedLogFiles.truncate(2);

        writeData(writeChannel, messagesAfter[0].getBytes(), writeChannel.currentTerm() + 1);
        writeData(writeChannel, messagesAfter[1].getBytes(), writeChannel.currentTerm() + 1);
        writeChannel.prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < 2; i++) {
                var currentMessage = messagesBefore[i];
                var readData = new byte[currentMessage.length()];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(new String(readData)).isEqualTo(currentMessage);
                assertThat(reader.entryIndex()).isEqualTo(i);
                assertThat(reader.currentTerm()).isEqualTo(i);
            }
            for (int i = 0; i < 2; i++) {
                var currentMessage = messagesAfter[i];
                var readData = new byte[currentMessage.length()];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(new String(readData)).isEqualTo(currentMessage);
                assertThat(reader.entryIndex()).isEqualTo(i + 2);
                assertThat(reader.currentTerm()).isEqualTo(i + 2);
            }
        }
    }

    @Test
    void shouldTruncateAll() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();

        envelopedLogFiles.initialise();

        var message1 = "one";
        var message2 = "two";
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, message1.getBytes());
        writeData(writeChannel, message1.getBytes());
        writeData(writeChannel, message1.getBytes());
        writeChannel.prepareForFlush().flush();

        envelopedLogFiles.truncate(0);
        writeChannel = envelopedLogFiles.currentWriteChannel();

        writeData(writeChannel, message2.getBytes());
        writeChannel.prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            var readData = new byte[message2.length()];
            reader.read(ByteBuffer.wrap(readData));
            assertThat(new String(readData)).isEqualTo(message2);
            assertThat(reader.entryIndex()).isEqualTo(0);
        }
    }

    @Test
    void shouldFailToTruncateEntriesThatHasBeenPruned() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var largeMessage = new byte[segmentBlockSize / 2];
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, largeMessage);
        writeData(writeChannel, largeMessage);
        writeData(writeChannel, largeMessage);
        writeChannel.prepareForFlush().flush();

        mirroringRepository.deleteLogFilesTo(1);

        assertThrows(IllegalArgumentException.class, () -> envelopedLogFiles.truncate(1));
    }

    @Test
    void shouldTruncateEntriesSpanningOverMultipleFiles() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var largeMessage = new byte[segmentBlockSize / 2];
        var message1 = "beforeTruncate";
        var message2 = "afterTruncate";
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, message1.getBytes());
        writeData(writeChannel, largeMessage);
        writeData(writeChannel, largeMessage);
        writeData(writeChannel, largeMessage);
        writeData(writeChannel, largeMessage);
        writeData(writeChannel, largeMessage);
        writeChannel.prepareForFlush().flush();

        envelopedLogFiles.truncate(1);

        writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, message2.getBytes());
        writeChannel.prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            var readData = new byte[message1.length()];
            reader.read(ByteBuffer.wrap(readData));
            assertThat(new String(readData)).isEqualTo(message1);
            assertThat(reader.entryIndex()).isEqualTo(0);

            readData = new byte[message2.length()];
            reader.read(ByteBuffer.wrap(readData));
            assertThat(new String(readData)).isEqualTo(message2);
            assertThat(reader.entryIndex()).isEqualTo(1);
        }
    }

    // Generate a wide range of message sizes to exercise corner cases
    private int shuffledMessageSize(int index) {
        return 1 + ((97 * index) % (3 * segmentBlockSize));
    }

    @Test
    void positionsShouldMatchWhenReadingAndWritingManyPacketSizes() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        final var N = 1000;
        var positions = new ArrayList<Pair<Long, Long>>();
        try (var writeChannel = envelopedLogFiles.currentWriteChannel()) {
            for (int count = 0; count < N; ++count) {
                var messageSize = shuffledMessageSize(count);
                var data = new byte[messageSize];
                data[0] = (byte) (count & 0xFF);
                data[messageSize - 1] = data[0];
                writeData(writeChannel, data);
                writeChannel.prepareForFlush().flush();
                positions.add(Pair.of(mirroringRepository.logVersionsRange().to(), writeChannel.position()));
            }
        }

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int count = 0; count < N; ++count) {
                var messageSize = shuffledMessageSize(count);
                var readData = new byte[messageSize];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(readData[0]).isEqualTo((byte) (count & 0xFF));
                assertThat(readData[messageSize - 1]).isEqualTo(readData[0]);
                var pos = Pair.of(reader.getLogVersion(), reader.position());
                assertThat(pos).isEqualTo(positions.get(count));
            }
        }
    }

    @Test
    void readPositionsShouldBeValidSeekPoints() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();
        final var N = 1000;
        try (var writeChannel = envelopedLogFiles.currentWriteChannel()) {
            for (int count = 0; count < N; ++count) {
                var messageSize = shuffledMessageSize(count);
                var data = new byte[messageSize];
                data[0] = (byte) (count & 0xFF);
                data[messageSize - 1] = data[0];
                writeData(writeChannel, data);
                writeChannel.prepareForFlush().flush();
            }
        }

        var positions = new ArrayList<Pair<Long, Long>>();
        positions.add(Pair.of(0L, (long) segmentBlockSize));
        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int count = 0; count < N; ++count) {
                var messageSize = shuffledMessageSize(count);
                var readData = new byte[messageSize];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(readData[0]).isEqualTo((byte) (count & 0xFF));
                assertThat(readData[messageSize - 1]).isEqualTo(readData[0]);
                positions.add(Pair.of(reader.getLogVersion(), reader.position()));
            }
        }
        positions.removeLast();

        verifyReadingAtPositions(positions);
    }

    @Test
    void writePositionsShouldBeValidSeekPoints() throws IOException {
        assertThat(mirroringRepository.isEmpty()).isTrue();
        envelopedLogFiles.initialise();

        var positions = new ArrayList<Pair<Long, Long>>();
        positions.add(Pair.of(0L, (long) segmentBlockSize));
        final var N = 1000;
        try (var writeChannel = envelopedLogFiles.currentWriteChannel()) {
            for (int count = 0; count < N; ++count) {
                var messageSize = shuffledMessageSize(count);
                var data = new byte[messageSize];
                data[0] = (byte) (count & 0xFF);
                data[messageSize - 1] = data[0];
                writeData(writeChannel, data);
                writeChannel.prepareForFlush().flush();
                positions.add(Pair.of(mirroringRepository.logVersionsRange().to(), writeChannel.position()));
            }
        }
        positions.removeLast();

        verifyReadingAtPositions(positions);
    }

    @Test
    void shouldRemoveAllFiles() throws IOException {
        envelopedLogFiles.initialise();
        var envelopeWriteChannel = envelopedLogFiles.currentWriteChannel();

        for (int i = 0; i < totalSegments * 4; i++) {
            writeData(envelopeWriteChannel, new byte[segmentBlockSize]);
        }
        envelopeWriteChannel.prepareForFlush().flush();

        assertThat(mirroringRepository.logVersionsRange()).isEqualTo(LongRange.range(0, 7));

        envelopedLogFiles.remove();

        assertThat(mirroringRepository.isEmpty()).isTrue();
    }

    @Test
    void shouldSetCorrectChecksumOnSkip() throws Exception {
        envelopedLogFiles.initialise();
        var messageOne = new byte[randomSupport.nextInt(1, segmentBlockSize)];
        var messageTwo = new byte[randomSupport.nextInt(1, segmentBlockSize)];
        var messageThree = new byte[randomSupport.nextInt(1, segmentBlockSize)];

        // create the log
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, messageOne);
        writeData(writeChannel, messageTwo);
        writeData(writeChannel, messageThree);
        writeChannel.prepareForFlush().flush();

        var entryChecksums = new int[3];
        int offset;
        // read checksums and get the offset
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            gotToEndOfNextEntry(envelopeReadChannel);
            entryChecksums[0] = envelopeReadChannel.getChecksum();

            gotToEndOfNextEntry(envelopeReadChannel);
            entryChecksums[1] = envelopeReadChannel.getChecksum();
            long position = envelopeReadChannel.goToNextEnvelope();
            offset = envelopeReadChannel.getSegmentOffset(position);

            if (!(envelopeReadChannel.payloadType == EnvelopeType.FULL
                    || envelopeReadChannel.payloadType == EnvelopeType.END)) {
                gotToEndOfNextEntry(envelopeReadChannel);
            }
            entryChecksums[2] = envelopeReadChannel.getChecksum();
        }

        // recreate the log
        envelopedLogFiles.close();
        mirroringRepository.deleteLogFilesTo(Long.MAX_VALUE);
        setUp();
        envelopedLogFiles.initialise();

        // simulated a joining member by skipping and writing the same entry
        envelopedLogFiles.skip(1, entryChecksums[1], offset, -1);
        writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, messageThree);
        writeChannel.prepareForFlush().flush();

        // validate log tails are the same
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            var logHeader = envelopeReadChannel.logHeader();
            var logHeaderChecksum = logHeader.getPreviousLogFileChecksum();
            assertThat(logHeaderChecksum).isEqualTo(entryChecksums[1]);
            gotToEndOfNextEntry(envelopeReadChannel);
            assertThat(envelopeReadChannel.getChecksum()).isEqualTo(entryChecksums[2]);
        }
    }

    @Test
    void shouldSetCorrectChecksumOnTruncate() throws Exception {
        envelopedLogFiles.initialise();
        var messageOne = new byte[randomSupport.nextInt(1, segmentBlockSize)];
        var messageTwo = new byte[randomSupport.nextInt(1, segmentBlockSize)];
        var messageThree = new byte[randomSupport.nextInt(1, segmentBlockSize)];
        var messageFour = new byte[randomSupport.nextInt(segmentBlockSize, segmentBlockSize * 3)];

        var messages = shuffledMessages(messageOne, messageTwo, messageThree, messageFour);

        // create the log
        var writeChannel = envelopedLogFiles.currentWriteChannel();
        writeData(writeChannel, messages[0], 0);
        writeData(writeChannel, messages[1]);
        writeData(writeChannel, messages[2]);
        writeChannel.prepareForFlush().flush();

        var entryChecksums = new ArrayList<Integer>();
        // read checksums
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            while (true) {
                try {
                    envelopeReadChannel.goToNextEnvelope();
                    entryChecksums.add(envelopeReadChannel.getChecksum());
                } catch (ReadPastEndException ignore) {
                    break;
                }
            }
        }

        // truncate log - should not impact the checksum chain
        var truncateAt = randomSupport.nextInt(0, 3);
        envelopedLogFiles.truncate(truncateAt);
        writeChannel = envelopedLogFiles.currentWriteChannel();
        for (var i = truncateAt; i < messages.length; i++) {
            writeData(writeChannel, messages[i], 0);
        }
        writeChannel.prepareForFlush().flush();

        // validate logs are the same
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel()) {
            for (Integer entryChecksum : entryChecksums) {
                envelopeReadChannel.goToNextEnvelope();
                assertThat(envelopeReadChannel.getChecksum()).isEqualTo(entryChecksum);
            }
        }
    }

    private byte[][] shuffledMessages(byte[] messageOne, byte[] messageTwo, byte[] messageThree, byte[] messageFour) {
        var list = new ArrayList<>(List.of(messageOne, messageTwo, messageThree, messageFour));
        Collections.shuffle(list, randomSupport.random());

        return list.toArray(new byte[0][]);
    }

    private static long gotToEndOfNextEntry(EnvelopeReadChannel envelopeReadChannel) throws IOException {
        long position;
        do {
            position = envelopeReadChannel.goToNextEnvelope();
        } while (envelopeReadChannel.payloadType != EnvelopeType.FULL
                && envelopeReadChannel.payloadType != EnvelopeType.END);
        return position;
    }

    private void verifyReadingAtPositions(ArrayList<Pair<Long, Long>> positions) throws IOException {
        int readCount = 0;
        for (var pos : positions) {
            var channel = mirroringRepository.openReadChannel(pos.first());
            try (var reader = envelopedLogFiles.envelopedReadChannel(channel, pos.first())) {
                var posMarker = new LogPositionMarker();
                posMarker.mark(pos.first(), pos.other());
                reader.setLogPosition(posMarker);
                var messageSize = shuffledMessageSize(readCount);
                var readData = new byte[messageSize];
                reader.get(readData, messageSize);
                assertThat(readData[0]).isEqualTo((byte) (readCount & 0xFF));
                assertThat(readData[messageSize - 1]).isEqualTo(readData[0]);
                readCount++;
            }
        }
    }

    private int writeManyMessages(byte[] data) throws IOException {
        var messagesPerFile = totalFileDataSize / data.length; // does not include envelope overhead
        var minimumFiles = 3;
        var totalMessages = messagesPerFile * minimumFiles;

        for (int i = 0; i < totalMessages; i++) {
            writeData(envelopedLogFiles.currentWriteChannel(), data);
        }
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();
        return totalMessages;
    }
}

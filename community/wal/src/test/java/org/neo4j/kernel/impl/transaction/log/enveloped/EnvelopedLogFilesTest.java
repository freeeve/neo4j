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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.helpers.collection.LongRange;
import org.neo4j.internal.helpers.collection.Pair;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.LogPositionMarker;
import org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class EnvelopedLogFilesTest {

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
        writeChannel.beginChecksumForWriting();
        writeChannel.putVersion(KernelVersion.GLORIOUS_FUTURE.version());
        writeChannel.putContentType(LogEnvelopeHeader.KERNEL_CONTENT_TYPE);
        writeChannel.put(data, data.length);
        writeChannel.endCurrentEntry();
    }

    @BeforeEach
    void setUp() {

        var baseFile = testDirectory.directory("logsFolder").resolve("raftLog");
        mirroringRepository = new LogsRepository(
                fs, baseFile.getParent(), baseFile.getFileName().toString());
        envelopedLogFiles = new EnvelopedLogFiles(
                fs,
                baseFile,
                (fileVersion, preFileIndex, preFileChecksum, segmentSize) -> LogFormat.fromKernelVersion(
                                KernelVersion.GLORIOUS_FUTURE)
                        .newHeader(
                                fileVersion,
                                preFileIndex,
                                LogHeader.UNKNOWN_TERM,
                                StoreId.UNKNOWN,
                                segmentSize,
                                preFileChecksum,
                                KernelVersion.GLORIOUS_FUTURE),
                segmentBlockSize,
                writeBufferedBlocks,
                totalSegments,
                EmptyMemoryTracker.INSTANCE,
                (currentEntry, currentOffset, currentLogFile) ->
                        pruneStrategy.newConstraint(currentEntry, currentOffset, currentLogFile),
                new LogFilesPreAllocator());
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
    void shouldFindFileContainingIndexDirectly() throws IOException {
        var baseData = new byte[segmentBlockSize / 3];

        envelopedLogFiles.initialise();

        var writeChannel = envelopedLogFiles.currentWriteChannel();

        for (int i = 0; i < 20; i++) {
            baseData[0] = (byte) i; // set unique data index
            writeData(writeChannel, baseData);
        }
        writeChannel.prepareForFlush().flush();

        for (int i = 0; i < 20; i++) {
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
    void shouldFindFileContainingIndexDirectlyEvenAfterPrune() throws IOException {
        var baseData = new byte[segmentBlockSize / 3];

        envelopedLogFiles.initialise();

        var writeChannel = envelopedLogFiles.currentWriteChannel();

        for (int i = 0; i < 40; i++) {
            baseData[0] = (byte) i; // set unique data index
            writeData(writeChannel, baseData);
        }
        writeChannel.prepareForFlush().flush();

        long prunedIndex = envelopedLogFiles.prune(20);

        for (int i = (int) (prunedIndex + 1); i < 40; i++) {
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
    void shouldReturnNullIfIndexHasPruned() throws IOException {
        envelopedLogFiles.initialise();

        var data = EIGHT_BYTES_MESSAGE.getBytes();
        var largeData = new byte[totalFileDataSize / 2];
        writeData(envelopedLogFiles.currentWriteChannel(), largeData);
        writeData(envelopedLogFiles.currentWriteChannel(), largeData); // spills over to next file
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        writeData(envelopedLogFiles.currentWriteChannel(), data);
        envelopedLogFiles.currentWriteChannel().prepareForFlush().flush();

        // 1 is completed in first file, so we expect it to be removed
        assertThat(envelopedLogFiles.prune(2)).isEqualTo(1);

        // 0 has been pruned entirley
        assertThat(envelopedLogFiles.openReadChannel(0)).isNull();
        // 1 spills over but should still be considered pruned
        assertThat(envelopedLogFiles.openReadChannel(1)).isNull();
        // 2 has not been pruned and should open a file
        try (var envelopeReadChannel = envelopedLogFiles.openReadChannel(2)) {
            envelopeReadChannel.alignWithStartEntry();
            assertThat(envelopeReadChannel.entryIndex()).isEqualTo(2);
        }
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
        writeData(writeChannel, messagesBefore[0].getBytes());
        writeData(writeChannel, messagesBefore[1].getBytes());
        writeData(writeChannel, messagesBefore[2].getBytes());
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

        writeData(writeChannel, messagesAfter[0].getBytes());
        writeData(writeChannel, messagesAfter[1].getBytes());
        writeChannel.prepareForFlush().flush();

        try (var reader = envelopedLogFiles.openReadChannel()) {
            for (int i = 0; i < 2; i++) {
                var currentMessage = messagesBefore[i];
                var readData = new byte[currentMessage.length()];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(new String(readData)).isEqualTo(currentMessage);
                assertThat(reader.entryIndex()).isEqualTo(i);
            }
            for (int i = 0; i < 2; i++) {
                var currentMessage = messagesAfter[i];
                var readData = new byte[currentMessage.length()];
                reader.read(ByteBuffer.wrap(readData));
                assertThat(new String(readData)).isEqualTo(currentMessage);
                assertThat(reader.entryIndex()).isEqualTo(i + 2);
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

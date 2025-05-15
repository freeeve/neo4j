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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.nativeimpl.LinuxNativeAccess;
import org.neo4j.internal.nativeimpl.NativeAccessProvider;
import org.neo4j.internal.nativeimpl.NativeCallResult;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.pagecache.OutOfDiskSpaceException;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class LogFilesPreAllocatorTest {

    @Inject
    TestDirectory testDirectory;

    private final int fileSize = 1024;
    LogsRepository logsRepository;
    private LogChannelContext<StoreChannel> writeChannel;

    @BeforeEach
    void setUp() {
        logsRepository =
                new LogsRepository(testDirectory.getFileSystem(), testDirectory.directory("logsFolder"), "raftLog");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (writeChannel != null) {
            writeChannel.channel().close();
        }
    }

    @Test
    void shouldThrowOOD() throws IOException {
        var nativeAccess = new StubbedNativeAccess(new NativeCallResult(28, "OOD"));
        var logProvider = new AssertableLogProvider();

        var logFilesPreAllocator = new LogFilesPreAllocator(nativeAccess, logProvider);

        writeChannel = logsRepository.createWriteChannel(0);
        assertThatThrownBy(() -> logFilesPreAllocator.preAllocateLogFile(writeChannel, fileSize))
                .isInstanceOf(OutOfDiskSpaceException.class)
                .hasMessageContaining("System is out of disk space for log file at: " + writeChannel.path());
        LogAssertions.assertThat(logProvider).doesNotHaveAnyLogs();
    }

    @Test
    void shouldTrowIOExceptionForOtherError() throws IOException {
        var nativeAccess = new StubbedNativeAccess(new NativeCallResult(1, "OOD"));
        var logProvider = new AssertableLogProvider();

        var logFilesPreAllocator = new LogFilesPreAllocator(nativeAccess, logProvider);

        writeChannel = logsRepository.createWriteChannel(0);
        logFilesPreAllocator.preAllocateLogFile(writeChannel, fileSize);
        LogAssertions.assertThat(logProvider)
                .containsMessageWithAll("Fail to preallocate additional space for log file at:");
    }

    @Test
    void shouldPreAllocate() throws IOException {
        var nativeAccess = NativeAccessProvider.getNativeAccess();
        assumeTrue(nativeAccess.isAvailable());
        var logProvider = new AssertableLogProvider();

        var logFilesPreAllocator = new LogFilesPreAllocator(nativeAccess, logProvider);

        writeChannel = logsRepository.createWriteChannel(0);
        assertThat(testDirectory.getFileSystem().getFileSize(writeChannel.path()))
                .isZero();

        logFilesPreAllocator.preAllocateLogFile(writeChannel, fileSize);

        assertThat(testDirectory.getFileSystem().getFileSize(writeChannel.path()))
                .isEqualTo(fileSize);
        LogAssertions.assertThat(logProvider).doesNotHaveAnyLogs();
    }

    @Test
    void shouldIgnoreIfFileAlreadyHasSize() throws IOException {
        var nativeAccess = new StubbedNativeAccess(null);
        var logProvider = new AssertableLogProvider();

        var logFilesPreAllocator = new LogFilesPreAllocator(nativeAccess, logProvider);

        writeChannel = logsRepository.createWriteChannel(0);

        writeChannel.channel().write(ByteBuffer.wrap(new byte[1]));
        assertThat(testDirectory.getFileSystem().getFileSize(writeChannel.path()))
                .isEqualTo(1);

        logFilesPreAllocator.preAllocateLogFile(writeChannel, fileSize);

        assertThat(nativeAccess.preAllocatedWasCalled).isFalse();
        LogAssertions.assertThat(logProvider).doesNotHaveAnyLogs();
    }

    private static class StubbedNativeAccess extends LinuxNativeAccess {
        private final NativeCallResult stubbedResult;
        private boolean preAllocatedWasCalled;

        public StubbedNativeAccess(NativeCallResult stubbedResult) {
            this.stubbedResult = stubbedResult;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public NativeCallResult tryPreallocateSpace(int fileVersion, long bytesToAllocate) {
            preAllocatedWasCalled = true;
            return stubbedResult;
        }
    }
}

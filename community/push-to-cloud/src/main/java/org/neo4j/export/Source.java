/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.export;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.zip.CRC32;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.memory.NativeScopedBuffer;
import org.neo4j.memory.EmptyMemoryTracker;

public record Source(FileSystemAbstraction fs, Path path, long size) {
    private static final long CRC32_BUFFER_SIZE = ByteUnit.mebiBytes(4);
    private static final long CRC32_LARGE_FILE_THRESHOLD = ByteUnit.mebiBytes(100);
    private static final long CRC32_SEGMENT_SIZE = ByteUnit.mebiBytes(100);
    private static final long CRC32_SAMPLE_SIZE = ByteUnit.mebiBytes(1);

    long crc32Sum() throws IOException {
        CRC32 crc = new CRC32();
        long fileSize = fs.getFileSize(path);

        if (fileSize < CRC32_LARGE_FILE_THRESHOLD) {
            try (var channel = fs.read(path);
                    var buffer = new NativeScopedBuffer(
                            CRC32_BUFFER_SIZE, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE)) {
                var byteBuffer = buffer.getBuffer();
                while ((channel.read(byteBuffer)) != -1) {
                    byteBuffer.flip();
                    crc.update(byteBuffer);
                    byteBuffer.clear();
                }
            }
        } else {
            // For files over threshold (100 MB), only read first 1MB of each 100 MB
            try (var channel = fs.read(path);
                    var buffer = new NativeScopedBuffer(
                            CRC32_BUFFER_SIZE, ByteOrder.LITTLE_ENDIAN, EmptyMemoryTracker.INSTANCE)) {
                var byteBuffer = buffer.getBuffer();

                long numSegments = (fileSize + CRC32_SEGMENT_SIZE - 1) / CRC32_SEGMENT_SIZE;

                for (long segment = 0; segment < numSegments; segment++) {
                    long segmentStart = segment * CRC32_SEGMENT_SIZE;
                    long bytesToRead = Math.min(CRC32_SAMPLE_SIZE, fileSize - segmentStart);

                    if (bytesToRead <= 0) {
                        break;
                    }

                    channel.position(segmentStart);
                    byteBuffer.limit((int) Math.min(byteBuffer.capacity(), bytesToRead));

                    int read = channel.read(byteBuffer);
                    if (read == -1) {
                        break;
                    }

                    byteBuffer.flip();
                    crc.update(byteBuffer);
                    byteBuffer.clear();
                }
            }
        }

        return crc.getValue();
    }
}

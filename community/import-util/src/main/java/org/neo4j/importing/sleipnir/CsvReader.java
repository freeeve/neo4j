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
package org.neo4j.importing.sleipnir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.neo4j.importing.sleipnir.csv.CsvStream;
import org.neo4j.importing.sleipnir.csv.SWARCsvStream;

public class CsvReader {
    private static final int CHUNK_SIZE = 1024 * 64;

    private final Path path;

    public CsvReader(Path path) {
        this.path = path;
    }

    long numberOfLines() throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = channel.size();

            ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK_SIZE);
            CsvStream csvStream = new SWARCsvStream();
            long[] indexes = new long[CHUNK_SIZE];

            long separatorCount = 0;

            long position = 0;
            while (position < fileSize) {
                buffer.clear();
                int read = channel.read(buffer, position);
                buffer.flip();
                separatorCount += csvStream.indexSeparators(buffer, position, indexes);
                position += read;
            }
            return separatorCount;
        }
    }
}

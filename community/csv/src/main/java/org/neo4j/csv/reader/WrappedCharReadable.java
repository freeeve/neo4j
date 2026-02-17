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
package org.neo4j.csv.reader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

/**
 * Wraps a {@link Reader} into a {@link CharReadable}.
 */
class WrappedCharReadable extends CharReadable.Adapter {
    private final long length;
    private final Reader reader;
    private long position;
    private final String sourceDescription;
    private final Path file;
    private long lineNumber;

    WrappedCharReadable(long length, Reader reader, String sourceDescription, Path file) {
        this.length = length;
        this.reader = reader;
        this.sourceDescription = sourceDescription;
        this.file = file;
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public SectionedCharBuffer read(SectionedCharBuffer buffer, int from) throws IOException {
        buffer.compact(buffer, from);
        buffer.readFrom(reader);
        position += buffer.available();
        return buffer;
    }

    @Override
    public int read(char[] into, int offset, int length) throws IOException {
        int totalRead = 0;
        boolean eof = false;
        while (totalRead < length) {
            int read = reader.read(into, offset + totalRead, length - totalRead);
            if (read == -1) {
                eof = true;
                break;
            }
            totalRead += read;
        }
        position += totalRead;
        lineNumber += countLines(into, offset, length);
        return totalRead == 0 && eof ? -1 : totalRead;
    }

    private long countLines(char[] buffer, int offset, int length) {
        long lines = 0;
        for (int i = 0; i < length; i++) {
            if (buffer[offset + i] == '\n') {
                lines++;
            }
        }
        return lines;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long lineNumber() {
        return lineNumber;
    }

    @Override
    public String sourceDescription() {
        return sourceDescription;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public String toString() {
        return sourceDescription;
    }
}

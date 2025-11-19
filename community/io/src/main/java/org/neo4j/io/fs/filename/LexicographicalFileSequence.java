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
package org.neo4j.io.fs.filename;

import static java.lang.String.format;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.neo4j.util.Preconditions;

/// A utility for generating sequentially incrementing file names that are lexicographically sortable.
///
/// The file name sequence begins at 1, and will contain the specified number of
/// digits (default: 3 digits, `001` - `999`).
///
///   - file.01
///   - file.02
///   - ...
///   - file.03
///   - ...
///
/// where the suffix represents a strictly monotonic sequence number.
///
/// Note: This class provides an alternative to SequentialFilesHelper.
public class LexicographicalFileSequence implements Iterable<String> {
    private static final String DEFAULT_DELIMITER = ".";
    private static final int DEFAULT_NUM_DIGITS = 3;

    private final int maxNumFiles;
    private final String fmt;

    public static LexicographicalFileSequence of(String name) {
        return new LexicographicalFileSequence(name, DEFAULT_DELIMITER, DEFAULT_NUM_DIGITS);
    }

    public static LexicographicalFileSequence of(String name, String delimiter) {
        return new LexicographicalFileSequence(name, delimiter, DEFAULT_NUM_DIGITS);
    }

    public static LexicographicalFileSequence of(String name, String delimiter, int numDigits) {
        return new LexicographicalFileSequence(name, delimiter, numDigits);
    }

    private LexicographicalFileSequence(String name, String delimiter, int numDigits) {
        Preconditions.requireNonNull(name, "Name must not be null");
        Preconditions.requireNonNull(delimiter, "Delimiter must not be null");
        Preconditions.requirePositive(numDigits);
        this.maxNumFiles = (int) Math.pow(10, numDigits);
        this.fmt = formatString(name, delimiter, numDigits);
    }

    @Override
    public Iterator<String> iterator() {
        return new LexicographicalFilenameSequenceIterator();
    }

    public Stream<String> stream() {
        int flags = Spliterator.NONNULL
                | Spliterator.SORTED
                | Spliterator.ORDERED
                | Spliterator.DISTINCT
                | Spliterator.IMMUTABLE;
        var spliterator = Spliterators.spliteratorUnknownSize(iterator(), flags);
        return StreamSupport.stream(spliterator, false);
    }

    private static String formatString(String name, String delimiter, int numDigits) {
        return name + delimiter + "%0" + numDigits + "d";
    }

    private class LexicographicalFilenameSequenceIterator implements Iterator<String> {
        private long sequenceNumber = 1;

        @Override
        public String toString() {
            return "LexicographicalFilenameSequence{fmt=" + fmt + ", sequenceNumber=" + sequenceNumber + '}';
        }

        @Override
        public boolean hasNext() {
            return sequenceNumber < maxNumFiles;
        }

        @Override
        public String next() {
            return format(fmt, sequenceNumber++);
        }
    }
}

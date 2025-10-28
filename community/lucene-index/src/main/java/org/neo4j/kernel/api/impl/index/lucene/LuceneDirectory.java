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
package org.neo4j.kernel.api.impl.index.lucene;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import org.neo4j.io.ByteUnit;
import org.neo4j.logging.Log;

public interface LuceneDirectory extends Closeable {
    /**
     * Returns names of all files stored in this directory. The output must be in sorted (UTF-16,
     * java's {@link String#compareTo}) order.
     *
     * @throws IOException in case of I/O error
     */
    String[] listAll() throws IOException;

    /**
     * Removes an existing file in the directory.
     *
     * <p>This method must throw either {@link NoSuchFileException} or {@link FileNotFoundException}
     * if {@code name} points to a non-existing file.
     *
     * @param name the name of an existing file.
     * @throws IOException in case of I/O error
     */
    void deleteFile(String name) throws IOException;

    /**
     * Check if the directory contains an existing index.
     *
     * @return {@code true} if the directory contains an index, {@code false} otherwise.
     * @throws IOException in case of I/O error
     */
    boolean indexExists() throws IOException;

    LuceneDirectoryReader open() throws IOException;

    boolean checkIndexIsClean() throws IOException;

    /**
     * Check if the index in the directory is of a version compatible with it.
     *
     * @return {@code true} if the index is compatible, {@code false} otherwise.
     * @throws IOException
     */
    boolean validVersion() throws IOException;

    boolean hasCommits() throws IOException;

    Collection<String> latestCommitFileNames() throws IOException;

    LuceneIndexWriter newWriter(LuceneIndexWriterConfig writerConfig) throws IOException;

    LuceneContext getLuceneContext();

    class DelegatingLuceneDirectory implements LuceneDirectory {
        private final LuceneDirectory delegate;

        public DelegatingLuceneDirectory(LuceneDirectory delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public String[] listAll() throws IOException {
            return delegate.listAll();
        }

        @Override
        public void deleteFile(String name) throws IOException {
            delegate.deleteFile(name);
        }

        @Override
        public boolean indexExists() throws IOException {
            return delegate.indexExists();
        }

        @Override
        public LuceneDirectoryReader open() throws IOException {
            return delegate.open();
        }

        @Override
        public boolean checkIndexIsClean() throws IOException {
            return delegate.checkIndexIsClean();
        }

        @Override
        public boolean validVersion() throws IOException {
            return delegate.validVersion();
        }

        @Override
        public boolean hasCommits() throws IOException {
            return delegate.hasCommits();
        }

        @Override
        public Collection<String> latestCommitFileNames() throws IOException {
            return delegate.latestCommitFileNames();
        }

        @Override
        public LuceneIndexWriter newWriter(LuceneIndexWriterConfig writerConfig) throws IOException {
            return delegate.newWriter(writerConfig);
        }

        @Override
        public LuceneContext getLuceneContext() {
            return delegate.getLuceneContext();
        }
    }

    record LogMergeListener(Log log, String trigger) implements AutoCloseable {
        public LogMergeListener {
            log.debug("%s Merge [%s]: starting merge", trigger, System.identityHashCode(this));
        }

        public void individualMergeInfo(int numberOfDocuments, long inputSize, long estimatedMergedSize) {
            log.debug(
                    "%s Merge [%s]: merging %d documents of %s with estimated merged size %s",
                    trigger,
                    System.identityHashCode(this),
                    numberOfDocuments,
                    ByteUnit.bytesToString(inputSize),
                    ByteUnit.bytesToString(estimatedMergedSize));
        }

        public void thrown(Throwable throwable) {
            log.error("%s Merge [%s]: failed".formatted(trigger, System.identityHashCode(this)), throwable);
        }

        @Override
        public void close() {
            log.debug("%s Merge [%s]: finishing merge", trigger, System.identityHashCode(this));
        }
    }
}

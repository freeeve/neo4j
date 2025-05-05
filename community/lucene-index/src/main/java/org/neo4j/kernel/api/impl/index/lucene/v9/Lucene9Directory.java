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
package org.neo4j.kernel.api.impl.index.lucene.v9;

import static java.lang.Math.toIntExact;

import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Version;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectoryReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriter;

public class Lucene9Directory implements LuceneDirectory {
    final Directory directory;

    public Lucene9Directory(Directory directory) {
        this.directory = directory;
    }

    @Override
    public String[] listAll() throws IOException {
        return directory.listAll();
    }

    @Override
    public void deleteFile(String name) throws IOException {
        directory.deleteFile(name);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
        directory.sync(names);
    }

    @Override
    public boolean indexExists() throws IOException {
        return DirectoryReader.indexExists(directory);
    }

    @Override
    public LuceneDirectoryReader open() throws IOException {
        return new Lucene9DirectoryReader(DirectoryReader.open(directory));
    }

    @Override
    public boolean checkIndexIsClean() throws IOException {
        try (CheckIndex checkIndex = new CheckIndex(directory)) {
            CheckIndex.Status status = checkIndex.checkIndex();
            return status.clean;
        }
    }

    @Override
    public boolean validVersion() throws IOException {
        int createdMajorVersion = new CreatedMajorVersion().run();
        return Version.MIN_SUPPORTED_MAJOR <= createdMajorVersion && createdMajorVersion <= Version.LATEST.major;
    }

    @Override
    public boolean hasCommits() throws IOException {
        return indexExists() && SegmentInfos.readLatestCommit(directory) != null;
    }

    @Override
    public Collection<String> latestCommitFileNames() throws IOException {
        return DirectoryReader.listCommits(directory).getLast().getFileNames();
    }

    @Override
    public LuceneIndexWriter newWriter(IndexWriterConfig writerConfig) throws IOException {
        IndexWriter indexWriter = new IndexWriter(directory, writerConfig);
        return new Lucene9IndexWriter(indexWriter);
    }

    @Override
    public void close() throws IOException {
        directory.close();
    }

    byte[] readFile(String name) throws IOException {
        try (IndexInput in = directory.openInput(name, IOContext.DEFAULT)) {
            byte[] bytes = new byte[toIntExact(in.length())];
            in.readBytes(bytes, 0, bytes.length);
            return bytes;
        }
    }

    private final class CreatedMajorVersion extends SegmentInfos.FindSegmentsFile<Integer> {
        private CreatedMajorVersion() {
            super(directory);
        }

        @Override
        protected Integer doBody(String segmentFileName) throws IOException {
            try (IndexInput input = directory.openInput(segmentFileName, IOContext.DEFAULT)) {
                CodecUtil.readIndexHeader(input);
                input.readVInt(); // Version.major
                input.readVInt(); // Version.minor
                input.readVInt(); // Version.bugfix
                return input.readVInt();
            }
        }
    }
}

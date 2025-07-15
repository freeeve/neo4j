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
package org.neo4j.kernel.api.impl.index.backup;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.configuration.Config;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.io.IOUtils;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigBuilder;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigMode;
import org.neo4j.kernel.api.impl.index.lucene.LuceneContext;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriter;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriterConfig;
import org.neo4j.kernel.api.impl.index.storage.DirectoryFactory;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
public class ReadOnlyIndexSnapshotFileIteratorTest {
    @Inject
    private TestDirectory testDir;

    Path indexDir;
    protected LuceneDirectory dir;

    @AfterEach
    public void tearDown() throws IOException {
        IOUtils.closeAll(dir);
    }

    @ParameterizedTest
    @EnumSource
    void shouldReturnRealSnapshotIfIndexAllowsIt(LuceneContext luceneContext) throws IOException {
        indexDir = testDir.homePath();
        dir = DirectoryFactory.persistent(luceneContext).open(indexDir);
        prepareIndex();

        Set<String> files = listDir(dir);
        assertFalse(files.isEmpty());

        try (ResourceIterator<Path> snapshot = makeSnapshot()) {
            Set<String> snapshotFiles =
                    snapshot.stream().map(Path::getFileName).map(Path::toString).collect(toSet());
            assertEquals(files, snapshotFiles);
        }
    }

    @ParameterizedTest
    @EnumSource
    void shouldReturnEmptyIteratorWhenNoCommitsHaveBeenMade(LuceneContext luceneContext) throws IOException {
        indexDir = testDir.homePath();
        dir = DirectoryFactory.persistent(luceneContext).open(indexDir);
        try (ResourceIterator<Path> snapshot = makeSnapshot()) {
            assertFalse(snapshot.hasNext());
        }
    }

    private void prepareIndex() throws IOException {
        Config config = Config.defaults();
        LuceneIndexWriterConfig writerConfig = new IndexWriterConfigBuilder(IndexWriterConfigMode.TEXT, config).build();
        try (LuceneIndexWriter writer = dir.newWriter(writerConfig)) {
            insertRandomDocuments(writer);
        }
    }

    protected ResourceIterator<Path> makeSnapshot() throws IOException {
        return LuceneIndexSnapshots.forIndex(indexDir, dir);
    }

    private static void insertRandomDocuments(LuceneIndexWriter writer) throws IOException {
        LuceneDocument doc = writer.newDocument();
        doc.addStringField("a", "b", true);
        doc.addStringField("c", "d", false);
        writer.addDocument(doc);
        writer.commit();
    }

    private static Set<String> listDir(LuceneDirectory dir) throws IOException {
        String[] files = dir.listAll();
        return Stream.of(files)
                .filter(file -> !LuceneIndexWriter.WRITE_LOCK_NAME.equals(file))
                .collect(toSet());
    }
}

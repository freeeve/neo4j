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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.logging.LogAssertions.assertThat;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.AssertableLogProvider.Level;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;

@TestDirectoryExtension
public class LuceneDirectoryTest {

    @ParameterizedTest
    @EnumSource(LuceneContext.class)
    void shouldLogMergesWhenScheduled(LuceneContext luceneContext) throws IOException {
        var logProvider = new AssertableLogProvider();

        try (var directoryFactory = luceneContext.directoryFactory();
                var directory = directoryFactory.inMemoryDirectory()) {

            var indexWriterConfig = new LuceneIndexWriterConfig(new KeywordAnalyzer());
            indexWriterConfig
                    .setLogProvider(logProvider)
                    .setMergingParameters(1.0, 0.1, 32, 1024); // merge factor 1024 to avoid merges during indexing

            try (var indexWriter = directory.newWriter(indexWriterConfig)) {

                var documentsFactory = luceneContext.documentsFactory();

                for (String id : List.of("1", "2")) {
                    var doc = documentsFactory.newDocument();
                    doc.addStringField("id", id, true);
                    indexWriter.addDocument(doc);
                    indexWriter.commit();
                }

                indexWriter.forceMerge(1);
            }
        }

        assertThat(logProvider)
                .forLevel(Level.DEBUG)
                .containsMessages("starting merge")
                .containsMessages("merging 2 documents")
                .containsMessages("finishing merge");
    }

    @TempDir
    Path tmpDir;

    @ParameterizedTest
    @EnumSource(LuceneContext.class)
    void shouldLogMergeErrors(LuceneContext luceneContext) throws IOException {
        var logProvider = new AssertableLogProvider();

        try (var directoryFactory = luceneContext.directoryFactory();
                var directory = directoryFactory.openPersistent(tmpDir)) {

            var indexWriterConfig = new LuceneIndexWriterConfig(new KeywordAnalyzer());
            indexWriterConfig
                    .setLogProvider(logProvider)
                    .setMergingParameters(1.0, 0.1, 32, 1024); // merge factor 1024 to avoid merges during indexing

            try (var indexWriter = directory.newWriter(indexWriterConfig)) {

                var documentsFactory = luceneContext.documentsFactory();

                for (String id : List.of("1", "2")) {
                    var doc = documentsFactory.newDocument();
                    doc.addStringField("id", id, true);
                    indexWriter.addDocument(doc);
                    indexWriter.commit();
                }

                // remove file permissions so that merging will fail
                Files.walkFileTree(tmpDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.setPosixFilePermissions(dir, Set.of());
                        return super.postVisitDirectory(dir, exc);
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.setPosixFilePermissions(file, Set.of());
                        return super.visitFile(file, attrs);
                    }
                });

                assertThatThrownBy(() -> indexWriter.forceMerge(1)).hasRootCauseInstanceOf(AccessDeniedException.class);
            }
        }

        assertThat(logProvider).forLevel(Level.ERROR).containsMessages("failed");
        assertThat(logProvider)
                .forLevel(Level.DEBUG)
                .containsMessages("starting merge")
                .containsMessages("finishing merge")
                .doesNotContainMessage("merging 2 documents");
    }
}

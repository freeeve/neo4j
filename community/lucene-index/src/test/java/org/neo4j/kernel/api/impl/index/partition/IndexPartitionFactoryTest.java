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
package org.neo4j.kernel.api.impl.index.partition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.configuration.Config;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigBuilder;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigMode;
import org.neo4j.kernel.api.impl.index.SearcherReference;
import org.neo4j.kernel.api.impl.index.lucene.LuceneContext;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriter;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class IndexPartitionFactoryTest {
    @Inject
    private TestDirectory testDirectory;

    @ParameterizedTest
    @EnumSource
    void createReadOnlyPartition(LuceneContext luceneContext) throws Exception {
        try (LuceneDirectory directory = luceneContext.directoryFactory().openPersistent(testDirectory.homePath())) {
            prepareIndex(luceneContext);
            try (AbstractIndexPartition indexPartition =
                    new ReadOnlyIndexPartitionFactory().createPartition(testDirectory.homePath(), directory)) {
                assertThrows(UnsupportedOperationException.class, indexPartition::getIndexWriter);
            }
        }
    }

    @ParameterizedTest
    @EnumSource
    void createWritablePartition(LuceneContext luceneContext) throws Exception {
        try (LuceneDirectory directory = luceneContext.directoryFactory().openPersistent(testDirectory.homePath());
                AbstractIndexPartition indexPartition = new WritableIndexPartitionFactory(() -> {
                            Config config = Config.defaults();
                            return new IndexWriterConfigBuilder(IndexWriterConfigMode.TEXT, config).build();
                        })
                        .createPartition(testDirectory.homePath(), directory)) {

            try (LuceneIndexWriter indexWriter = indexPartition.getIndexWriter()) {
                indexWriter.addDocument(indexWriter.newDocument());
                indexWriter.commit();
                indexPartition.maybeRefreshBlocking();
                try (SearcherReference searcher = indexPartition.acquireSearcher()) {
                    assertEquals(
                            1, searcher.getIndexSearcher().numDocs(), "We should be able to see newly added document ");
                }
            }
        }
    }

    private void prepareIndex(LuceneContext luceneContext) throws IOException {
        Path location = testDirectory.homePath();
        try (AbstractIndexPartition ignored = new WritableIndexPartitionFactory(() -> {
                    Config config = Config.defaults();
                    return new IndexWriterConfigBuilder(IndexWriterConfigMode.TEXT, config).build();
                })
                .createPartition(location, luceneContext.directoryFactory().openPersistent(location))) {
            // empty
        }
    }
}

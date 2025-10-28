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
package org.neo4j.kernel.api.impl.schema.fulltext;

import org.apache.lucene.analysis.Analyzer;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.database.readonly.DatabaseReadOnlyChecker;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.api.impl.index.DatabaseIndex;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigBuilder;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigMode;
import org.neo4j.kernel.api.impl.index.builder.AbstractLuceneIndexBuilder;
import org.neo4j.kernel.api.impl.index.partition.WritableIndexPartitionFactory;
import org.neo4j.logging.LogProvider;
import org.neo4j.token.api.TokenHolder;

public class FulltextIndexBuilder extends AbstractLuceneIndexBuilder<FulltextIndexBuilder> {
    private final IndexDescriptor descriptor;
    private final TokenHolder propertyKeyTokenHolder;
    private final Analyzer analyzer;
    private final String[] propertyNames;
    private boolean populating;
    private IndexUpdateSink indexUpdateSink = NullIndexUpdateSink.INSTANCE;
    private final Config config;

    private FulltextIndexBuilder(
            IndexDescriptor descriptor,
            Config config,
            DatabaseReadOnlyChecker readOnlyChecker,
            TokenHolder propertyKeyTokenHolder,
            Analyzer analyzer,
            String[] propertyNames,
            LogProvider logProvider) {
        super(readOnlyChecker, logProvider);
        this.config = config;
        this.descriptor = descriptor;
        this.analyzer = analyzer;
        this.propertyNames = propertyNames;
        this.propertyKeyTokenHolder = propertyKeyTokenHolder;
    }

    /**
     * Create new lucene fulltext index builder.
     *
     * @param descriptor The descriptor for this index
     * @param propertyKeyTokenHolder A token holder used to look up property key token names by id.
     * @return new FulltextIndexBuilder
     */
    public static FulltextIndexBuilder create(
            IndexDescriptor descriptor,
            Config config,
            DatabaseReadOnlyChecker readOnlyChecker,
            TokenHolder propertyKeyTokenHolder,
            Analyzer analyzer,
            String[] propertyNames,
            LogProvider logProvider) {
        return new FulltextIndexBuilder(
                descriptor, config, readOnlyChecker, propertyKeyTokenHolder, analyzer, propertyNames, logProvider);
    }

    /**
     * Whether to create the index in a {@link IndexWriterConfigMode#TEXT_POPULATION populating} mode,
     * if {@code true}, or in a {@link IndexWriterConfigMode#TEXT standard} mode, if {@code false}.
     *
     * @param isPopulating {@code true} if the index should be created in a populating mode.
     * @return this index builder.
     */
    FulltextIndexBuilder withPopulatingMode(boolean isPopulating) {
        this.populating = isPopulating;
        return this;
    }

    FulltextIndexBuilder withIndexUpdateSink(IndexUpdateSink indexUpdateSink) {
        this.indexUpdateSink = indexUpdateSink;
        return this;
    }

    /**
     * Build lucene schema index with specified configuration
     *
     * @return lucene schema index
     */
    public DatabaseIndex<FulltextIndexReader> build() {
        IndexWriterConfigMode mode = populating ? IndexWriterConfigMode.TEXT_POPULATION : IndexWriterConfigMode.TEXT;
        IndexWriterConfigBuilder writerConfigBuilder = new IndexWriterConfigBuilder(mode, config)
                .withLogProvider(logProvider)
                .withAnalyzer(analyzer);

        WritableIndexPartitionFactory partitionFactory = new WritableIndexPartitionFactory(writerConfigBuilder::build);
        FulltextIndex fulltextIndex = new FulltextIndex(
                storageBuilder.build(),
                partitionFactory,
                descriptor,
                propertyKeyTokenHolder,
                config,
                analyzer,
                propertyNames,
                logProvider);
        return new WritableFulltextDatabaseIndex(indexUpdateSink, fulltextIndex, readOnlyChecker, permanentlyReadOnly);
    }
}

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
package org.neo4j.kernel.api.impl.schema.reader;

import static org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory.ENTITY_ID_KEY;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.impl.index.SearcherReference;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexSearcher;
import org.neo4j.kernel.api.impl.index.lucene.LuceneQueryContext;
import org.neo4j.kernel.api.impl.schema.AbstractTextIndexReader;
import org.neo4j.kernel.api.impl.schema.LuceneQueryFactory;
import org.neo4j.kernel.api.impl.schema.TaskCoordinator;
import org.neo4j.kernel.api.index.IndexSampler;
import org.neo4j.kernel.impl.api.index.IndexSamplingConfig;
import org.neo4j.kernel.impl.index.schema.IndexUsageTracking;
import org.neo4j.logging.LogProvider;
import org.neo4j.values.storable.Value;

/**
 * Schema index reader that is able to read/sample a single partition of a partitioned Lucene index.
 *
 * @see PartitionedValueIndexReader
 */
public class TextIndexReader extends AbstractTextIndexReader {
    private final IndexSamplingConfig samplingConfig;
    private final TaskCoordinator taskCoordinator;

    public TextIndexReader(
            SearcherReference searcherReference,
            IndexDescriptor descriptor,
            IndexSamplingConfig samplingConfig,
            TaskCoordinator taskCoordinator,
            IndexUsageTracking usageTracker,
            LogProvider logProvider) {
        super(descriptor, searcherReference, usageTracker, LuceneQueryFactory.TextQueryFactory.INSTANCE, logProvider);
        this.samplingConfig = samplingConfig;
        this.taskCoordinator = taskCoordinator;
    }

    @Override
    public IndexSampler createSampler() {
        return getIndexSearcher().newIndexSampler(taskCoordinator, samplingConfig);
    }

    @Override
    protected String entityIdFieldKey() {
        return ENTITY_ID_KEY;
    }

    @Override
    protected boolean needStoreFilter(PropertyIndexQuery predicate) {
        return false;
    }

    @Override
    public long countIndexedEntities(
            long entityId, CursorContext cursorContext, int[] propertyKeyIds, Value... propertyValues) {
        LuceneIndexSearcher luceneIndexSearcher = getIndexSearcher();
        LuceneQueryContext queryContext = luceneIndexSearcher.newQueryContext();
        queryContext.addMustTerm(ENTITY_ID_KEY, String.valueOf(entityId));
        queryContext.addMustSeek(propertyValues);

        try {
            return luceneIndexSearcher.count(queryContext);
            // A <label,propertyKeyId,nodeId> tuple should only match at most a single propertyValue
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

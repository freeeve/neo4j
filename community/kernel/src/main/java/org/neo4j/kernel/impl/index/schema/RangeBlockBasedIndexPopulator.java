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
package org.neo4j.kernel.impl.index.schema;

import static org.neo4j.kernel.impl.index.schema.IndexUsageTracking.NO_USAGE_TRACKING;

import java.nio.file.OpenOption;
import org.eclipse.collections.api.set.ImmutableSet;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.configuration.Config;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.memory.ByteBufferFactory;
import org.neo4j.kernel.api.index.IndexPopulator;
import org.neo4j.kernel.api.index.IndexValueValidator;
import org.neo4j.logging.LogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.values.ElementIdMapper;

class RangeBlockBasedIndexPopulator extends BlockBasedIndexPopulator<RangeKey> {
    private final ElementIdMapper elementIdMapper;

    RangeBlockBasedIndexPopulator(
            DatabaseIndexContext databaseIndexContext,
            IndexFiles indexFiles,
            IndexLayout<RangeKey> layout,
            IndexDescriptor descriptor,
            boolean archiveFailedIndex,
            ByteBufferFactory bufferFactory,
            Config config,
            MemoryTracker memoryTracker,
            TokenNameLookup tokenNameLookup,
            ElementIdMapper elementIdMapper,
            Monitor monitor,
            ImmutableSet<OpenOption> openOptions,
            LogProvider logProvider,
            IndexPopulator.Configuration configuration) {
        super(
                databaseIndexContext,
                indexFiles,
                layout,
                descriptor,
                archiveFailedIndex,
                bufferFactory,
                config,
                memoryTracker,
                monitor,
                openOptions,
                logProvider,
                tokenNameLookup,
                configuration);
        this.elementIdMapper = elementIdMapper;
    }

    @Override
    NativeIndexReader<RangeKey> newReader() {
        return new RangeIndexReader(tree, layout, descriptor, NO_USAGE_TRACKING, logProvider);
    }

    @Override
    protected IndexValueValidator instantiateValueValidator() {
        return new GenericIndexKeyValidator(
                tree.keyValueSizeCap(), descriptor, layout, tokenNameLookup, elementIdMapper);
    }
}

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
package org.neo4j.internal.batchimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.index_population_batch_max_byte_size;
import static org.neo4j.internal.schema.AllIndexProviderDescriptors.RANGE_DESCRIPTOR;
import static org.neo4j.io.ByteUnit.kibiBytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.collections.api.factory.Sets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.configuration.Config;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.api.index.IndexPopulator;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.impl.api.index.IndexProviderMap;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

class ImportIndexBuilderTest {
    private final IndexDescriptor indexDescriptor = IndexPrototype.forSchema(
                    SchemaDescriptors.forLabel(0, 0), RANGE_DESCRIPTOR)
            .withName("test")
            .materialise(0);
    private final IndexPopulator populator = mock(IndexPopulator.class);
    private final IndexAccessor accessor = mock(IndexAccessor.class);

    @Test
    void shouldFlushIndexAdditionsSoonerIfLarge() throws IOException, IndexEntryConflictException {
        // given
        var indexProviders = mockedIndexProviders();
        long maxBatchByteSize = kibiBytes(10);
        try (var indexBuilder = indexBuilder(indexProviders, maxBatchByteSize)) {
            // when
            List<IndexEntryUpdate> expectedUpdates = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                verify(populator, never()).add(any(), any());
                var indexUpdate = addition((int) kibiBytes(1));
                indexBuilder.add(indexUpdate);
                expectedUpdates.add(indexUpdate);
            }

            // then
            var batch = ArgumentCaptor.forClass(Collection.class);
            verify(populator).add(batch.capture(), any());
            assertThat(batch.getValue()).isEqualTo(expectedUpdates);
        }
    }

    @Test
    void shouldFlushIndexAdditionsOnFullBatchIfSmall() throws IOException, IndexEntryConflictException {
        // given
        var indexProviders = mockedIndexProviders();
        long maxBatchByteSize = kibiBytes(120);
        List<IndexEntryUpdate> expectedUpdates = new ArrayList<>();
        try (var indexBuilder = indexBuilder(indexProviders, maxBatchByteSize)) {
            // when
            for (int i = 0; i < ImportIndexBuilder.BATCH_SIZE; i++) {
                verify(populator, never()).add(any(), any());
                var indexUpdate = addition(10);
                indexBuilder.add(indexUpdate);
                expectedUpdates.add(indexUpdate);
            }
        }

        // then
        var batch = ArgumentCaptor.forClass(Collection.class);
        verify(populator).add(batch.capture(), any());
        assertThat(batch.getValue()).isEqualTo(expectedUpdates);
    }

    @Test
    void shouldFlushIndexChangesSoonerIfLarge() throws IOException {
        // given
        var indexProviders = mockedIndexProviders();
        long maxBatchByteSize = kibiBytes(10);
        try (var indexBuilder = indexBuilder(indexProviders, maxBatchByteSize)) {
            // when
            for (int i = 0; i < 10; i++) {
                verify(accessor, never()).newUpdater(any(), any(), anyBoolean());
                var indexUpdate = change((int) kibiBytes(1));
                indexBuilder.add(indexUpdate);
            }

            // then
            verify(accessor, times(1)).newUpdater(any(), any(), anyBoolean());
        }
    }

    @Test
    void shouldFlushIndexChangesOnFullBatchIfSmall() throws IOException {
        // given
        var indexProviders = mockedIndexProviders();
        long maxBatchByteSize = kibiBytes(200);
        try (var indexBuilder = indexBuilder(indexProviders, maxBatchByteSize)) {
            // when
            for (int i = 0; i < ImportIndexBuilder.BATCH_SIZE; i++) {
                verify(accessor, never()).newUpdater(any(), any(), anyBoolean());
                var indexUpdate = change(10);
                indexBuilder.add(indexUpdate);
            }
        }

        // then
        verify(accessor, times(1)).newUpdater(any(), any(), anyBoolean());
    }

    private static ImportIndexBuilder indexBuilder(IndexProviderMap indexProviders, long maxBatchByteSize) {
        return new ImportIndexBuilder(
                new DefaultFileSystemAbstraction(),
                indexProviders,
                indexProviders,
                mock(TokenNameLookup.class),
                Sets.immutable.empty(),
                mock(PopulationWorkJobScheduler.class),
                id -> id,
                id -> id,
                Configuration.DEFAULT,
                mock(IndexStatisticsStore.class),
                StorageEngineIndexingBehaviour.EMPTY,
                index -> false,
                Config.defaults(index_population_batch_max_byte_size, maxBatchByteSize),
                IndexPopulator.DEFAULT_CONFIGURATION);
    }

    private IndexProviderMap mockedIndexProviders() throws IOException {
        var indexProviders = mock(IndexProviderMap.class);
        var indexProvider = mock(IndexProvider.class);
        when(indexProviders.lookup(any(IndexProviderDescriptor.class))).thenReturn(indexProvider);
        when(indexProvider.getPopulator(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(populator);
        when(indexProvider.getOnlineAccessor(any(), any(), any(), any(), any(), any()))
                .thenReturn(accessor);
        var updater = mock(IndexUpdater.class);
        when(accessor.newUpdater(any(), any(), anyBoolean())).thenReturn(updater);
        return indexProviders;
    }

    private IndexEntryUpdate addition(int roughByteSize) {
        return ValueIndexEntryUpdate.add(0, indexDescriptor, Values.byteArray(new byte[roughByteSize]));
    }

    private IndexEntryUpdate change(int roughByteSize) {
        var before = new Value[] {Values.byteArray(new byte[roughByteSize / 2])};
        var after = new Value[] {Values.byteArray(new byte[roughByteSize / 2])};
        return ValueIndexEntryUpdate.change(0, indexDescriptor, before, after);
    }
}

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
package org.neo4j.kernel.impl.transaction.state;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.neo4j.collection.Dependencies.dependenciesOf;
import static org.neo4j.test.LatestVersions.LATEST_KERNEL_VERSION;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.neo4j.collection.Dependencies;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.schema.fulltext.FulltextIndexProvider;
import org.neo4j.kernel.api.impl.schema.text.TextIndexProvider;
import org.neo4j.kernel.api.impl.schema.trigram.TrigramIndexProvider;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexProvider;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.kernel.impl.api.index.IndexProviderNotFoundException;
import org.neo4j.kernel.impl.index.schema.PointIndexProvider;
import org.neo4j.kernel.impl.index.schema.RangeIndexProvider;
import org.neo4j.kernel.impl.index.schema.TokenIndexProvider;

class StaticIndexProviderMapTest {

    @Test
    void testGetters() throws Exception {
        var tokenIndexProvider = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.EARLIEST);
        var rangeIndexProvider = mockProvider(RangeIndexProvider.class, IndexType.RANGE, KernelVersion.EARLIEST);
        var pointIndexProvider = mockProvider(PointIndexProvider.class, IndexType.POINT, KernelVersion.EARLIEST);
        var textIndexProvider = mockProvider(TextIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST);
        var trigramIndexProvider = mockProvider(TrigramIndexProvider.class, IndexType.TEXT, KernelVersion.V5_0);
        var fulltextIndexProvider =
                mockProvider(FulltextIndexProvider.class, IndexType.FULLTEXT, KernelVersion.EARLIEST);
        var vectorV1IndexProvider = mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST);
        var vectorV2IndexProvider = mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.V5_0);
        var map = new StaticIndexProviderMap(
                new Dependencies(),
                tokenIndexProvider,
                rangeIndexProvider,
                pointIndexProvider,
                textIndexProvider,
                trigramIndexProvider,
                fulltextIndexProvider,
                vectorV1IndexProvider,
                vectorV2IndexProvider);
        map.init();

        assertThat(map.getTokenIndexProvider(LATEST_KERNEL_VERSION)).isEqualTo(tokenIndexProvider);
        assertThat(map.getDefaultProvider(LATEST_KERNEL_VERSION)).isEqualTo(rangeIndexProvider);
        assertThat(map.getTextIndexProvider(LATEST_KERNEL_VERSION)).isEqualTo(trigramIndexProvider);
        assertThat(map.getFulltextProvider(LATEST_KERNEL_VERSION)).isEqualTo(fulltextIndexProvider);
        assertThat(map.getPointIndexProvider(LATEST_KERNEL_VERSION)).isEqualTo(pointIndexProvider);
        assertThat(map.getVectorIndexProvider(LATEST_KERNEL_VERSION)).isEqualTo(vectorV2IndexProvider);
    }

    @Test
    void testLookup() throws Exception {
        var tokenIndexProvider = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.EARLIEST);
        var rangeIndexProvider = mockProvider(RangeIndexProvider.class, IndexType.RANGE, KernelVersion.EARLIEST);
        var pointIndexProvider = mockProvider(PointIndexProvider.class, IndexType.POINT, KernelVersion.EARLIEST);
        var textIndexProvider = mockProvider(TextIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST);
        var trigramIndexProvider = mockProvider(TrigramIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST);
        var fulltextIndexProvider =
                mockProvider(FulltextIndexProvider.class, IndexType.FULLTEXT, KernelVersion.EARLIEST);
        var vectorV1IndexProvider = mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST);
        var vectorV2IndexProvider = mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST);
        var map = new StaticIndexProviderMap(
                new Dependencies(),
                tokenIndexProvider,
                rangeIndexProvider,
                pointIndexProvider,
                textIndexProvider,
                trigramIndexProvider,
                fulltextIndexProvider,
                vectorV1IndexProvider,
                vectorV2IndexProvider);
        map.init();

        asList(
                        tokenIndexProvider,
                        rangeIndexProvider,
                        pointIndexProvider,
                        textIndexProvider,
                        trigramIndexProvider,
                        fulltextIndexProvider,
                        vectorV1IndexProvider,
                        vectorV2IndexProvider)
                .forEach(p -> {
                    assertThat(map.lookup(p.getProviderDescriptor()))
                            .as("lookup by descriptor")
                            .isEqualTo(p);

                    assertThat(map.lookup(p.getProviderDescriptor().name()))
                            .as("lookup by descriptor name")
                            .isEqualTo(p);
                });
    }

    @Test
    void testAccept() throws Exception {
        var tokenIndexProvider = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.EARLIEST);
        var rangeIndexProvider = mockProvider(RangeIndexProvider.class, IndexType.RANGE, KernelVersion.EARLIEST);
        var pointIndexProvider = mockProvider(PointIndexProvider.class, IndexType.POINT, KernelVersion.EARLIEST);
        var textIndexProvider = mockProvider(TextIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST);
        var trigramIndexProvider = mockProvider(TrigramIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST);
        var fulltextIndexProvider =
                mockProvider(FulltextIndexProvider.class, IndexType.FULLTEXT, KernelVersion.EARLIEST);
        var vectorV1IndexProvider = mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST);
        var vectorV2IndexProvider = mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST);
        var map = new StaticIndexProviderMap(
                new Dependencies(),
                tokenIndexProvider,
                rangeIndexProvider,
                pointIndexProvider,
                textIndexProvider,
                trigramIndexProvider,
                fulltextIndexProvider,
                vectorV1IndexProvider,
                vectorV2IndexProvider);
        map.init();

        var accepted = new ArrayList<>();
        map.accept(accepted::add);

        assertThat(accepted)
                .containsExactlyInAnyOrder(
                        tokenIndexProvider,
                        rangeIndexProvider,
                        textIndexProvider,
                        trigramIndexProvider,
                        fulltextIndexProvider,
                        pointIndexProvider,
                        vectorV1IndexProvider,
                        vectorV2IndexProvider);
    }

    @Test
    void testWithExtension() throws Exception {
        var extension = mockProvider(IndexProvider.class, IndexType.RANGE, KernelVersion.EARLIEST);
        RangeIndexProvider rangeIndexProvider =
                mockProvider(RangeIndexProvider.class, IndexType.RANGE, KernelVersion.EARLIEST);
        var map = new StaticIndexProviderMap(
                dependenciesOf(extension),
                mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.EARLIEST),
                rangeIndexProvider,
                mockProvider(PointIndexProvider.class, IndexType.POINT, KernelVersion.EARLIEST),
                mockProvider(TextIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST),
                mockProvider(TrigramIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST),
                mockProvider(FulltextIndexProvider.class, IndexType.FULLTEXT, KernelVersion.EARLIEST),
                mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST),
                mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST));
        map.init();

        assertThat(map.lookup(extension.getProviderDescriptor())).isEqualTo(extension);
        assertThat(map.lookup(extension.getProviderDescriptor().name())).isEqualTo(extension);
        assertThat(map.lookup(IndexType.RANGE)).containsExactlyInAnyOrder(extension, rangeIndexProvider);
        var accepted = new ArrayList<>();
        map.accept(accepted::add);
        assertThat(accepted).contains(extension);
    }

    @Test
    void testLookupByMissingType() throws Exception {
        var rangeIndexProvider = mockProvider(RangeIndexProvider.class, IndexType.RANGE, KernelVersion.EARLIEST);
        var map = new StaticIndexProviderMap(
                new Dependencies(),
                mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.EARLIEST),
                rangeIndexProvider,
                mockProvider(
                        PointIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST), // <- Specifically NOT point
                mockProvider(TextIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST),
                mockProvider(TrigramIndexProvider.class, IndexType.TEXT, KernelVersion.EARLIEST),
                mockProvider(FulltextIndexProvider.class, IndexType.FULLTEXT, KernelVersion.EARLIEST),
                mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST),
                mockProvider(VectorIndexProvider.class, IndexType.VECTOR, KernelVersion.EARLIEST));
        map.init();

        assertThatThrownBy(() -> map.lookup(IndexType.POINT))
                .isInstanceOf(IndexProviderNotFoundException.class)
                .hasMessageContaining("Tried to get index providers for index type " + IndexType.POINT
                        + " but could not find any. Available index providers per type are ")
                .hasMessageContaining(IndexType.RANGE + "=["
                        + rangeIndexProvider.getProviderDescriptor().name() + "]");
    }

    @Test
    void testKernelVersion() throws Exception {
        var provider40 = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.V4_0);
        var provider42 = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.V4_2);
        var providerGF = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.GLORIOUS_FUTURE);
        var provider50 = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.V5_0);
        var provider202505 = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.V2025_05);
        var provider525 = mockProvider(TokenIndexProvider.class, IndexType.LOOKUP, KernelVersion.V5_25);

        var map = new StaticIndexProviderMap(
                new Dependencies(), provider40, provider42, providerGF, provider50, provider202505, provider525);
        map.init();

        assertThat(map.getTokenIndexProvider(KernelVersion.V4_0)).isEqualTo(provider40);
        assertThat(map.getTokenIndexProvider(KernelVersion.V4_4)).isEqualTo(provider42);
        assertThat(map.getTokenIndexProvider(KernelVersion.V5_0)).isEqualTo(provider50);
        assertThat(map.getTokenIndexProvider(KernelVersion.V5_25)).isEqualTo(provider525);
        assertThat(map.getTokenIndexProvider(KernelVersion.V2025_04)).isEqualTo(provider525);
        assertThat(map.getTokenIndexProvider(KernelVersion.V2025_05)).isEqualTo(provider202505);
        assertThat(map.getTokenIndexProvider(KernelVersion.GLORIOUS_FUTURE)).isEqualTo(providerGF);
    }

    private static <T extends IndexProvider> T mockProvider(Class<? extends T> clazz) {
        var mock = mock(clazz);
        var version = UUID.randomUUID().toString();
        when(mock.getProviderDescriptor()).thenReturn(new IndexProviderDescriptor(clazz.getName(), version));
        return mock;
    }

    private static <T extends IndexProvider> T mockProvider(
            Class<? extends T> clazz, IndexType indexType, KernelVersion kernelVersion) {
        var mock = mockProvider(clazz);
        when(mock.getIndexType()).thenReturn(indexType);
        when(mock.getMinimumRequiredVersion()).thenReturn(kernelVersion);
        return mock;
    }
}

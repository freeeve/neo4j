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
package org.neo4j.kernel.api.impl.schema.vector;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.util.OptionalInt;
import org.apache.lucene.codecs.Codec;
import org.eclipse.collections.api.set.ImmutableSet;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.database.readonly.DatabaseReadOnlyChecker;
import org.neo4j.graphdb.WriteOperationsNotAllowedException;
import org.neo4j.internal.schema.IndexCapability;
import org.neo4j.internal.schema.IndexConfig;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.internal.schema.SettingsAccessor.IndexConfigAccessor;
import org.neo4j.internal.schema.StorageEngineIndexingBehaviour;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.memory.ByteBufferFactory;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.index.DatabaseIndex;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigBuilder;
import org.neo4j.kernel.api.impl.index.IndexWriterConfigMode;
import org.neo4j.kernel.api.impl.index.lucene.LuceneSettings;
import org.neo4j.kernel.api.impl.index.lucene.v10.codec.VectorCodecV2;
import org.neo4j.kernel.api.impl.index.lucene.v10.codec.VectorCodecV3;
import org.neo4j.kernel.api.impl.index.storage.DirectoryFactory;
import org.neo4j.kernel.api.impl.schema.AbstractLuceneIndexProvider;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.api.index.IndexDirectoryStructure;
import org.neo4j.kernel.api.index.IndexPopulator;
import org.neo4j.kernel.impl.api.index.IndexSamplingConfig;
import org.neo4j.kernel.impl.index.schema.IndexUpdateIgnoreStrategy;
import org.neo4j.logging.LogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.monitoring.Monitors;
import org.neo4j.scheduler.Group;
import org.neo4j.scheduler.JobMonitoringParams;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.values.ElementIdMapper;
import org.neo4j.values.VectorCandidate;
import org.neo4j.values.storable.Value;

public class VectorIndexProvider extends AbstractLuceneIndexProvider {
    private final VectorIndexVersion version;
    private final VectorIndexSettingsValidator settingsValidator;
    private final VectorDocumentStructure documentStructure;
    private final FileSystemAbstraction fileSystem;
    private final JobScheduler scheduler;

    public VectorIndexProvider(
            VectorIndexVersion version,
            FileSystemAbstraction fileSystem,
            DirectoryFactory directoryFactory,
            IndexDirectoryStructure.Factory directoryStructureFactory,
            Monitors monitors,
            Config config,
            DatabaseReadOnlyChecker readOnlyChecker,
            JobScheduler scheduler,
            LogProvider logProvider) {
        super(
                version.minimumRequiredKernelVersion(),
                IndexType.VECTOR,
                version.descriptor(),
                fileSystem,
                directoryFactory,
                directoryStructureFactory,
                monitors,
                config,
                readOnlyChecker,
                logProvider);
        this.version = version;
        this.settingsValidator = version.indexSettingValidator();
        this.documentStructure = VectorDocumentStructures.documentStructureFor(version);
        this.fileSystem = fileSystem;
        this.scheduler = scheduler;
    }

    @Override
    public IndexPrototype validatePrototype(IndexPrototype prototype) {
        prototype = super.validatePrototype(prototype);
        // construction handles validation
        final var vectorIndexConfig =
                settingsValidator.validateToVectorIndexConfig(new IndexConfigAccessor(prototype.getIndexConfig()));
        // replaces provided config with validated config with set defaults
        return prototype.withIndexConfig(vectorIndexConfig.config());
    }

    @Override
    public IndexPopulator getPopulator(
            IndexDescriptor descriptor,
            IndexSamplingConfig samplingConfig,
            ByteBufferFactory bufferFactory,
            MemoryTracker memoryTracker,
            TokenNameLookup tokenNameLookup,
            ElementIdMapper elementIdMapper,
            ImmutableSet<OpenOption> openOptions,
            StorageEngineIndexingBehaviour indexingBehaviour,
            IndexPopulator.Configuration configuration) {
        final var vectorIndexConfig =
                settingsValidator.trustIsValidToVectorIndexConfig(new IndexConfigAccessor(descriptor.getIndexConfig()));
        final var dimensions = vectorIndexConfig.dimensions();

        final var codec = selectCodec(vectorIndexConfig);
        final var writerConfigBuilder = new IndexWriterConfigBuilder(IndexWriterConfigMode.VECTOR_POPULATION, config)
                .withLogProvider(logProvider)
                .withCodec(codec);
        final var luceneIndex = VectorIndexBuilder.create(
                        descriptor, vectorIndexConfig, documentStructure, codec, readOnlyChecker, config, logProvider)
                .withFileSystem(fileSystem)
                .withIndexStorage(getIndexStorage(descriptor.getId()))
                .withWriterConfig(writerConfigBuilder::build)
                .build();

        if (luceneIndex.isReadOnly()) {
            throw WriteOperationsNotAllowedException.noWriteOperationAllowed();
        }

        final var ignoreStrategy = new IgnoreStrategy(version, dimensions);
        final var similarityFunction = vectorSimilarityFunctionFrom(vectorIndexConfig);
        return new VectorIndexPopulator(luceneIndex, ignoreStrategy, documentStructure, similarityFunction);
    }

    private Codec selectCodec(VectorIndexConfig vectorIndexConfig) {
        return version.minimumRequiredKernelVersion().isAtLeast(KernelVersion.VERSION_LUCENE_10_INTRODUCED)
                ? new VectorCodecV3(vectorIndexConfig)
                : new VectorCodecV2(vectorIndexConfig);
    }

    @Override
    public IndexAccessor getOnlineAccessor(
            IndexDescriptor descriptor,
            IndexSamplingConfig samplingConfig,
            TokenNameLookup tokenNameLookup,
            ElementIdMapper elementIdMapper,
            ImmutableSet<OpenOption> openOptions,
            boolean readOnly,
            StorageEngineIndexingBehaviour indexingBehaviour)
            throws IOException {
        final var vectorIndexConfig =
                settingsValidator.trustIsValidToVectorIndexConfig(new IndexConfigAccessor(descriptor.getIndexConfig()));
        final var codec = selectCodec(vectorIndexConfig);
        var builder = VectorIndexBuilder.create(
                        descriptor, vectorIndexConfig, documentStructure, codec, readOnlyChecker, config, logProvider)
                .withIndexStorage(getIndexStorage(descriptor.getId()));
        if (readOnly) {
            builder = builder.permanentlyReadOnly();
        }
        final var luceneIndex = builder.build();
        luceneIndex.open();
        forceMergeSegments(scheduler, luceneIndex);

        final var ignoreStrategy = new IgnoreStrategy(version, vectorIndexConfig.dimensions());
        final var similarityFunction = vectorSimilarityFunctionFrom(vectorIndexConfig);
        return new VectorIndexAccessor(luceneIndex, descriptor, ignoreStrategy, documentStructure, similarityFunction);
    }

    @Override
    public IndexDescriptor completeConfiguration(
            IndexDescriptor index, StorageEngineIndexingBehaviour indexingBehaviour) {
        return index.getCapability().equals(IndexCapability.NO_CAPABILITY)
                ? index.withIndexCapability(capability(version, index.getIndexConfig()))
                : index;
    }

    public static IndexCapability capability(VectorIndexVersion version, IndexConfig config) {
        final var vectorIndexConfig =
                version.indexSettingValidator().trustIsValidToVectorIndexConfig(new IndexConfigAccessor(config));
        return new VectorIndexCapability(
                new IgnoreStrategy(version, vectorIndexConfig.dimensions()), vectorIndexConfig.similarityFunction());
    }

    record IgnoreStrategy(VectorIndexVersion version, OptionalInt dimensions) implements IndexUpdateIgnoreStrategy {
        @Override
        public boolean ignore(Value... values) {
            if (values.length < 1) {
                return true;
            }

            // Vector value
            final var value = values[0];
            if (!version.acceptsValueInstanceType(value) || hasInvalidDimensions(VectorCandidate.maybeFrom(value))) {
                return true;
            }

            return false;
        }

        private boolean hasInvalidDimensions(VectorCandidate candidate) {
            return candidate == null
                    || dimensions.isPresent() && candidate.dimensions() != dimensions.getAsInt()
                    || dimensions.isEmpty()
                            && (candidate.dimensions() < 1 || candidate.dimensions() > version.maxDimensions());
        }
    }

    private Neo4jVectorSimilarityFunction vectorSimilarityFunctionFrom(VectorIndexConfig vectorIndexConfig) {
        final var vectorSimilarityFunction = vectorIndexConfig.similarityFunction();
        if (vectorSimilarityFunction instanceof Neo4jVectorSimilarityFunction sf) {
            return sf;
        }
        throw new IllegalArgumentException(
                "'%s' vector similarity function is expected to be compatible with Lucene. Provided: %s"
                        .formatted(vectorSimilarityFunction.functionName(), vectorSimilarityFunction));
    }

    /**
     * Use given {@link JobScheduler} to force the segment merges
     * @see #forceMergeSegments(DatabaseIndex)
     */
    private static void forceMergeSegments(JobScheduler scheduler, DatabaseIndex<?> luceneIndex) {
        scheduler.schedule(
                Group.INDEX_POPULATION,
                JobMonitoringParams.systemJob("Merging vector index segments"),
                IOUtils.uncheckedRunnable(() -> forceMergeSegments(luceneIndex)));
    }

    /**
     * {@link LuceneSettings#vector_population_merge_factor} should be larger than {@link LuceneSettings#vector_standard_merge_factor}
     * to enable faster population, but at the cost of more segment files.
     * This coerces the index to merge the segments to the {@link LuceneSettings#vector_standard_merge_factor}
     */
    private static void forceMergeSegments(DatabaseIndex<?> luceneIndex) throws IOException {
        IOException exception = null;
        for (final var partition : luceneIndex.getPartitions()) {
            try {
                partition.getIndexWriter().forceMerge(Integer.MAX_VALUE);
            } catch (IOException e) {
                if (exception != null) {
                    exception.addSuppressed(e);
                } else {
                    exception = e;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
}

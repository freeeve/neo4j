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
package org.neo4j.kernel.database;

import static java.io.OutputStream.nullOutputStream;
import static org.neo4j.batchimport.api.Configuration.DEFAULT;
import static org.neo4j.batchimport.api.Monitor.NO_MONITOR;
import static org.neo4j.batchimport.api.input.Collector.STRICT;
import static org.neo4j.internal.batchimport.DefaultAdditionalIds.EMPTY;
import static org.neo4j.io.ByteUnit.mebiBytes;
import static org.neo4j.io.pagecache.context.CursorContextFactory.NULL_CONTEXT_FACTORY;
import static org.neo4j.io.pagecache.tracing.PageCacheTracer.NULL;
import static org.neo4j.kernel.impl.transaction.log.files.TransactionLogInitializer.getLogFilesInitializer;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.neo4j.batchimport.api.BatchImporter;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.IndexConfig;
import org.neo4j.batchimport.api.InputIterable;
import org.neo4j.batchimport.api.InputIterator;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.Input;
import org.neo4j.batchimport.api.input.InputChunk;
import org.neo4j.batchimport.api.input.InputEntityVisitor;
import org.neo4j.batchimport.api.input.PropertySizeCalculator;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.mem.MemoryAllocator;
import org.neo4j.io.pagecache.ExternallyManagedPageCache;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;
import org.neo4j.io.pagecache.impl.muninn.swapper.SingleFilePageSwapperFactory;
import org.neo4j.kernel.api.index.IndexProvidersAccess;
import org.neo4j.kernel.impl.index.schema.DefaultIndexProvidersAccess;
import org.neo4j.kernel.impl.index.schema.IndexImporterFactoryImpl;
import org.neo4j.kernel.impl.transaction.log.files.LogTailMetadataFactoryImpl;
import org.neo4j.kernel.lifecycle.Lifespan;
import org.neo4j.logging.internal.NullLogService;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.storageengine.api.StorageEngineFactory;
import org.neo4j.storageengine.api.StoreGenerator;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.storageengine.api.StoreSeeder;

/**
 * Responsible for creating an empty store seed using the importer,
 * packaging it up and returning the contents as a byte[], ready to be seeded in a cluster.
 * A store can also be created from a seed.
 */
public class EmptyStoreSeeder implements StoreGenerator, StoreSeeder {
    private final Config config;
    private final FileSystemAbstraction fs;
    private final DatabaseLayout databaseLayout;
    private final JobScheduler jobScheduler;
    private final int numShards;

    /**
     * Creates the empty database seed, returning the generated contents packaged up as a byte[].
     * @param config for the database to create (also dictates which database format to use).
     * @param fs the file system in which the empty database gets created.
     * @param jobScheduler scheduler to use in the temporarily created {@link PageCache} backing the import.
     */
    public EmptyStoreSeeder(
            Config config,
            FileSystemAbstraction fs,
            DatabaseLayout databaseLayout,
            JobScheduler jobScheduler,
            int numShards) {
        this.config = config;
        this.fs = fs;
        this.databaseLayout = databaseLayout;
        this.jobScheduler = jobScheduler;
        this.numShards = numShards;
    }

    /**
     * @return a {@code byte[]} representation of the created empty store seed.
     * @throws IOException on I/O error.
     */
    @Override
    public byte[] generateStore() throws IOException {
        var storageEngineFactory = StorageEngineFactory.selectStorageEngine(config);
        var indexProvidersAccess = indexProviders(storageEngineFactory);
        var tempDatabaseLayout = cleanTempDatabaseLayout();
        try (var life = new Lifespan(indexProvidersAccess);
                var pageCache = openNonForcingSmallPageCache(fs, jobScheduler)) {
            var batchImporter =
                    batchImporter(storageEngineFactory, tempDatabaseLayout, pageCache, indexProvidersAccess);
            batchImporter.doImport(new NoInput());
            return zip(fs, tempDatabaseLayout.getNeo4jLayout().homeDirectory());
        } finally {
            fs.deleteRecursively(tempDatabaseLayout.getNeo4jLayout().homeDirectory());
        }
    }

    /**
     * Restores a database from the given seed. If the {@code seed} contains multiple databases then only the database
     * corresponding to the given {@code databaseLayout} will be restored.
     * @param seed a seed previously created using {@link #generateStore()}.
     * @throws IOException on I/O error.
     */
    @Override
    public void seedStore(byte[] seed) throws IOException {
        var filter = pathFilter();
        try (var stream = new ZipInputStream(new ByteArrayInputStream(seed))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (!entry.isDirectory() && filter.test(entry.getName())) {
                    var targetPath =
                            databaseLayout.getNeo4jLayout().homeDirectory().resolve(entry.getName());
                    var directory = targetPath.getParent();
                    if (!fs.fileExists(directory)) {
                        fs.mkdirs(directory);
                    }
                    try (var fileOutput = fs.openAsOutputStream(targetPath, false)) {
                        stream.transferTo(fileOutput);
                    }
                }
            }
        }
    }

    @Override
    public void validateStoreId(StoreId storeId) throws IOException {
        var storageEngineFactory =
                StorageEngineFactory.selectStorageEngine(fs, databaseLayout).orElseThrow();
        var logTailMetadata =
                new LogTailMetadataFactoryImpl(fs).getLogTailMetadata(config, databaseLayout, storageEngineFactory);
        var existingStoreId = logTailMetadata.getStoreId().orElseThrow();
        if (!existingStoreId.equals(storeId)) {
            throw new IllegalStateException(
                    "Existing " + existingStoreId + " of " + databaseLayout + " doesn't match expected " + storeId);
        }
    }

    private BatchImporter batchImporter(
            StorageEngineFactory storageEngineFactory,
            DatabaseLayout tempDatabaseLayout,
            PageCache pageCache,
            IndexProvidersAccess indexProvidersAccess) {
        var configuration = new Configuration.Overridden(DEFAULT) {
            @Override
            public ExternallyManagedPageCache providedPageCache() {
                return new ExternallyManagedPageCache(pageCache);
            }

            @Override
            public IndexConfig indexConfig() {
                return IndexConfig.create().withLabelIndex().withRelationshipTypeIndex();
            }
        };
        return storageEngineFactory.batchImporter(
                tempDatabaseLayout,
                fs,
                true,
                NULL,
                configuration,
                NullLogService.getInstance(),
                new PrintStream(nullOutputStream()),
                false,
                EMPTY,
                new LogTailMetadataFactoryImpl(fs),
                withoutTxLogPreAllocation(config),
                NO_MONITOR,
                jobScheduler,
                STRICT,
                getLogFilesInitializer(),
                new IndexImporterFactoryImpl(),
                INSTANCE,
                NULL_CONTEXT_FACTORY,
                indexProvidersAccess,
                numShards,
                null);
    }

    private DefaultIndexProvidersAccess indexProviders(StorageEngineFactory storageEngineFactory) {
        return new DefaultIndexProvidersAccess(
                storageEngineFactory,
                fs,
                config,
                jobScheduler,
                NullLogService.getInstance(),
                NULL,
                NULL_CONTEXT_FACTORY);
    }

    private DatabaseLayout cleanTempDatabaseLayout() throws IOException {
        var tempDirectory = databaseLayout.databaseDirectory().resolve("temp-seed");
        fs.deleteRecursively(tempDirectory);
        fs.mkdirs(tempDirectory);
        return DatabaseLayout.of(Neo4jLayout.of(tempDirectory), databaseLayout.getDatabaseName());
    }

    private Predicate<String> pathFilter() {
        Set<Path> prefixFilters = Set.of(
                databaseLayout.getNeo4jLayout().homeDirectory().relativize(databaseLayout.databaseDirectory()),
                databaseLayout
                        .getNeo4jLayout()
                        .homeDirectory()
                        .relativize(databaseLayout.getTransactionLogsDirectory()));
        return entryName -> {
            Path entryPath = Path.of(entryName);
            while (entryPath.getNameCount() > 1) {
                if (prefixFilters.contains(entryPath)) {
                    return true;
                }
                entryPath = entryPath.getParent();
            }
            return false;
        };
    }

    /**
     * Don't pre-allocate tx-logs un order to reduce space of the resulting database.
     * @param config {@link Config} instance passed from user.
     * @return the config, but with the {@link GraphDatabaseSettings#preallocate_logical_logs}
     * overridden to {@code false}.
     */
    private Config withoutTxLogPreAllocation(Config config) {
        return Config.newBuilder()
                .fromConfig(config)
                .set(GraphDatabaseSettings.preallocate_logical_logs, false)
                .build();
    }

    private byte[] zip(FileSystemAbstraction fs, Path path) throws IOException {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(byteArrayOutputStream)) {
            for (var file : fs.streamFilesRecursive(path).toList()) {
                if (!fs.isDirectory(file.getPath())) {
                    zip.putNextEntry(
                            new ZipEntry(path.relativize(file.getPath()).toString()));
                    try (var fileInput = fs.openAsInputStream(file.getPath())) {
                        fileInput.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Creates a small page cache which won't do any forcing of files since for this purpose it's unnecessary.
     * The resulting database(s) will only be used to package up into a zip file whose byte[] representation
     * will be returned. If any of the steps along the way fails the state on disk doesn't matter at all.
     */
    private PageCache openNonForcingSmallPageCache(FileSystemAbstraction fs, JobScheduler jobScheduler) {
        var config = MuninnPageCache.config(MemoryAllocator.createAllocator(mebiBytes(80), INSTANCE))
                .swapperFactory(new SingleFilePageSwapperFactory(fs, NULL, INSTANCE, false));
        return new MuninnPageCache(fs, jobScheduler, config);
    }

    private static class NoInput implements Input {
        @Override
        public InputIterable nodes(Collector badCollector) {
            return nothing();
        }

        private InputIterable nothing() {
            return new NoInputIterable();
        }

        @Override
        public InputIterable relationships(Collector badCollector) {
            return new NoInputIterable();
        }

        @Override
        public IdType idType() {
            return IdType.INTEGER;
        }

        @Override
        public ReadableGroups groups() {
            return ReadableGroups.EMPTY;
        }

        @Override
        public Estimates validateAndEstimate(PropertySizeCalculator valueSizeCalculator) throws IOException {
            return new Estimates(0, 0, 0, 0, 0, 0, 0, false, false);
        }
    }

    private static class NoInputIterable implements InputIterable, InputIterator, InputChunk {
        @Override
        public InputIterator iterator() {
            return this;
        }

        @Override
        public InputChunk newChunk() {
            return this;
        }

        @Override
        public boolean next(InputChunk chunk) {
            return false;
        }

        @Override
        public boolean next(InputEntityVisitor visitor) {
            return false;
        }

        @Override
        public void close() {}
    }
}

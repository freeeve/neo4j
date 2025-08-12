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
package org.neo4j.kernel.internal;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.neo4j.io.ByteUnit.bytesToString;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexSettingUtil;
import org.neo4j.graphdb.schema.IndexType;
import org.neo4j.io.device.DeviceMapper;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.diagnostics.providers.StoreFilesDiagnostics;
import org.neo4j.storageengine.api.StorageEngineFactory;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;

@DbmsExtension(configurationCallback = "configure")
class KernelDiagnosticsIT {
    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private FileSystemAbstraction fs;

    @Inject
    private DatabaseLayout databaseLayout;

    @ExtensionCallback
    void configure(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(GraphDatabaseInternalSettings.gbptree_structure_log_enabled, false);
        builder.setConfig(GraphDatabaseInternalSettings.buffered_ids_offload, false);
    }

    @ParameterizedTest
    @EnumSource(
            value = IndexType.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"LOOKUP"}) // testing property indexes
    void shouldIncludeNativeIndexFilesInTotalMappedSize(IndexType indexType) throws IOException {
        // given
        createIndexAndData(indexType);

        // when
        DatabaseLayout databaseLayout = db.databaseLayout();
        DeviceMapper deviceMapper = db.getDependencyResolver().resolveDependency(DeviceMapper.class);
        StorageEngineFactory storageEngineFactory =
                db.getDependencyResolver().resolveDependency(StorageEngineFactory.class);
        StoreFilesDiagnostics files = new StoreFilesDiagnostics(storageEngineFactory, fs, databaseLayout, deviceMapper);
        SizeCapture capture = new SizeCapture();
        files.dump(capture::log);
        assertNotNull(capture.size);

        // then
        long expected = manuallyCountTotalMappedFileSize(databaseLayout.databaseDirectory());
        assertEquals(bytesToString(expected), capture.size);
    }

    private void createIndexAndData(IndexType indexType) {
        Label label = Label.label("Label-" + indexType);
        String key = "key";
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < 100; i++) {
                tx.createNode(label).setProperty(key, "" + i);
            }
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            tx.schema()
                    .indexFor(label)
                    .on(key)
                    .withIndexType(indexType)
                    .withIndexConfiguration(IndexSettingUtil.defaultSettingsForTesting(indexType))
                    .create();
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(2, MINUTES);
            tx.commit();
        }
    }

    private long manuallyCountTotalMappedFileSize(Path dbDir) throws IOException {
        MutableLong result = new MutableLong();
        NativeIndexFileFilter nativeIndexFilter = new NativeIndexFileFilter(dbDir);
        var storeFiles = Arrays.stream(fs.listFiles(dbDir))
                .filter(p -> !p.equals(databaseLayout.databaseLockFile()))
                .filter(p -> !p.equals(databaseLayout.quarantineFile()))
                .filter(p -> !fs.isDirectory(p))
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());
        manuallyCountTotalMappedFileSize(dbDir, result, nativeIndexFilter, storeFiles);
        return result.getValue();
    }

    private void manuallyCountTotalMappedFileSize(
            Path dir, MutableLong result, NativeIndexFileFilter nativeIndexFilter, Set<String> storeFiles)
            throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir)) {
            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    manuallyCountTotalMappedFileSize(path, result, nativeIndexFilter, storeFiles);
                } else if (storeFiles.contains(path.getFileName().toString()) || nativeIndexFilter.test(path)) {
                    try {
                        result.add(Files.size(path));
                    } catch (IOException ignored) {
                        // Preserve behaviour of File.length()
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class SizeCapture {
        private String size;

        public void log(String message) {
            if (message.contains("Total size of mapped files")) {
                int beginPos = message.lastIndexOf(": ");
                Assertions.assertTrue(beginPos != -1);
                size = message.substring(beginPos + 2);
            }
        }
    }
}

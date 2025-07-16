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
package org.neo4j.importer;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.output.NullPrintStream;
import org.junit.jupiter.api.Test;
import org.neo4j.cli.ExecutionContext;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.csv.reader.Configuration;
import org.neo4j.internal.batchimport.input.InputException;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.context.FixedVersionContextSupplier;
import org.neo4j.io.pagecache.tracing.DefaultPageCacheTracer;
import org.neo4j.kernel.impl.transaction.log.files.TransactionLogFilesHelper;
import org.neo4j.storageengine.api.StorageEngineFactory;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.Neo4jLayoutExtension;
import org.neo4j.test.utils.TestDirectory;

@Neo4jLayoutExtension
class FileImporterTest {
    @Inject
    private TestDirectory testDir;

    @Inject
    private DatabaseLayout databaseLayout;

    @Test
    void writesReportToSpecifiedReportFile() throws Exception {

        Path logDir = testDir.directory("logs");
        Path reportLocation = testDir.file("the_report");

        Path inputFile = testDir.file("foobar.csv");
        List<String> lines = Collections.singletonList("foo\\tbar\\tbaz");
        Files.write(inputFile, lines, Charset.defaultCharset());

        Config config = Config.defaults(GraphDatabaseSettings.logs_directory, logDir.toAbsolutePath());
        final var logFilePath = FileImporter.getLogFilePath(config);
        try (var logFile = new BufferedOutputStream(Files.newOutputStream(logFilePath));
                var logProvider = FileImporter.getLog(logFile, true)) {
            final var csvImporter = importerBuilder(
                            databaseLayout.getNeo4jLayout().databaseLayout("foodb"))
                    .withDatabaseConfig(config)
                    .withReportFile(reportLocation.toAbsolutePath())
                    .withCsvConfig(Configuration.TABS)
                    .withStdOut(NullPrintStream.INSTANCE)
                    .withStdErr(NullPrintStream.INSTANCE)
                    .withLogProvider(logProvider)
                    .addNodeFiles(emptySet(), new Path[] {inputFile.toAbsolutePath()})
                    .build();

            csvImporter.doImport(fullImport());
        }

        assertTrue(Files.exists(reportLocation));
        assertThat(Files.readString(logFilePath)).contains("[foodb] Import starting");
    }

    @Test
    void complainsOnNonEmptyDirectoryUnlessForced() throws Exception {
        // Given
        Path file = databaseLayout.getTransactionLogsDirectory().resolve(TransactionLogFilesHelper.DEFAULT_NAME + ".0");
        List<String> lines = Collections.singletonList("foo\\tbar\\tbaz");
        Files.write(file, lines, Charset.defaultCharset());
        Path reportLocation = testDir.file("the_report");

        var rawOut = new ByteArrayOutputStream();
        var rawErr = new ByteArrayOutputStream();
        try (var out = new PrintStream(rawOut);
                var err = new PrintStream(rawErr)) {
            FileImporter.Builder csvImporterBuilder = importerBuilder()
                    .withDatabaseConfig(Config.defaults(GraphDatabaseSettings.neo4j_home, testDir.homePath()))
                    .withCsvConfig(Configuration.TABS)
                    .withStdOut(new PrintStream(rawOut))
                    .withStdErr(new PrintStream(rawErr))
                    .withReportFile(reportLocation.toAbsolutePath());
            assertThatThrownBy(() -> csvImporterBuilder.build().doImport(fullImport()))
                    .hasCauseInstanceOf(DirectoryNotEmptyException.class);
            out.flush();
            err.flush();

            // Then
            assertThat(rawErr.toString()).contains("Database already exist. Re-run with `--overwrite-destination`");
            assertThatCode(() -> csvImporterBuilder.withForce(true).build().doImport(fullImport()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void tracePageCacheAccessOnCsvImport() throws IOException {
        Path logDir = testDir.directory("logs");
        Path reportLocation = testDir.file("the_report");
        Path inputFile = writeFileWithLines("foobar.csv", "foo;bar;baz");

        Config config = Config.defaults(GraphDatabaseSettings.logs_directory, logDir.toAbsolutePath());

        var cacheTracer = new DefaultPageCacheTracer();
        FileImporter fileImporter = importerBuilder()
                .withDatabaseConfig(config)
                .withReportFile(reportLocation.toAbsolutePath())
                .withStdOut(NullPrintStream.INSTANCE)
                .withStdErr(NullPrintStream.INSTANCE)
                .withPageCacheTracer(cacheTracer)
                .withCursorContextFactory(
                        new CursorContextFactory(cacheTracer, new FixedVersionContextSupplier(BASE_TX_ID)))
                .addNodeFiles(emptySet(), new Path[] {inputFile.toAbsolutePath()})
                .build();

        fileImporter.doImport(fullImport());

        long pins = cacheTracer.pins();
        assertThat(pins).isGreaterThan(0);
        assertThat(cacheTracer.unpins()).isEqualTo(pins);
        assertThat(cacheTracer.hits()).isGreaterThan(0).isLessThanOrEqualTo(pins);
        assertThat(cacheTracer.faults()).isGreaterThan(0).isLessThanOrEqualTo(pins);
    }

    @Test
    void shouldEnforceBadTolerance() throws IOException {
        // given
        var nodes = writeFileWithLines("nodes.csv", ":ID", "abc", "abc", "abc", "abc", "abc", "abc");
        var importer = importerBuilder()
                .withDatabaseConfig(Config.defaults(GraphDatabaseSettings.neo4j_home, testDir.homePath()))
                .withStdOut(NullPrintStream.INSTANCE)
                .withStdErr(NullPrintStream.INSTANCE)
                .withReportFile(testDir.file("report.txt"))
                .addNodeFiles(emptySet(), new Path[] {nodes.toAbsolutePath()})
                .withBadTolerance(4)
                .withSkipDuplicateNodes(true)
                .build();

        // when
        assertThatThrownBy(() -> importer.doImport(fullImport()))
                .hasRootCauseInstanceOf(InputException.class)
                .hasMessageContaining("Too many bad entries");
    }

    private Path writeFileWithLines(String fileName, String... lines) throws IOException {
        var path = testDir.file(fileName);
        Files.write(path, List.of(lines), Charset.defaultCharset());
        return path;
    }

    private ImportCommand.Full fullImport() {
        return new ImportCommand.Full(new ExecutionContext(
                testDir.homePath(),
                testDir.homePath().resolve("conf"),
                NullPrintStream.INSTANCE,
                NullPrintStream.INSTANCE,
                testDir.getFileSystem()));
    }

    private FileImporter.Builder importerBuilder() {
        return importerBuilder(databaseLayout);
    }

    private FileImporter.Builder importerBuilder(DatabaseLayout layout) {
        return FileImporter.builder()
                .withStorageEngineFactory(StorageEngineFactory.defaultStorageEngine())
                .withDatabaseLayout(layout)
                .withFileSystem(testDir.getFileSystem());
    }
}

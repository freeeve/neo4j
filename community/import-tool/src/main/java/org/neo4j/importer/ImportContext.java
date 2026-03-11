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

import static java.lang.String.format;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.import_base_context_directory;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.import_detailed_reporting_interval;
import static org.neo4j.logging.Level.DEBUG;
import static org.neo4j.logging.Level.INFO;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.neo4j.batchimport.api.DetailedProgressReport;
import org.neo4j.batchimport.api.Monitor;
import org.neo4j.batchimport.api.UnsupportedFormatException;
import org.neo4j.cli.CommandFailedException;
import org.neo4j.cli.ExitCode;
import org.neo4j.commandline.dbms.CannotWriteException;
import org.neo4j.configuration.Config;
import org.neo4j.importer.FileImporter.CsvImportException;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.io.locker.FileLockException;
import org.neo4j.kernel.database.NormalizedDatabaseName;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.logging.log4j.Log4jLogProvider;
import org.neo4j.logging.log4j.LoggerTarget;
import picocli.CommandLine.ParameterException;

@SuppressWarnings("resource")
public class ImportContext extends Monitor.Delegate implements InternalLogProvider {

    private static final DateTimeFormatter SPACELESS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd.HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final String DEFAULT_LOG_DIR_TEMPLATE = "%s-admin-import-%s";

    public static final String LOG_FILE_NAME = "import.log";
    public static final String PROGRESS_REPORTING_FILE_NAME = "progress.json.log";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
            .registerModule(new SimpleModule().addSerializer(new DurationSerializer()));

    private final String dbName;

    private final Path baseDir;

    private final long reportingIntervalMs;

    private final Supplier<InternalLogProvider> logProviderSupplier;

    private final Supplier<PrintStream> progressStreamSupplier;

    private InternalLogProvider logProvider;

    private PrintStream progressStream;

    private boolean clearBaseDir;

    private ImportContext(
            String dbName,
            Path baseDir,
            long reportingIntervalMs,
            boolean retainContextDir,
            Supplier<InternalLogProvider> logProviderSupplier,
            Supplier<PrintStream> progressStreamSupplier) {
        super(Monitor.NO_MONITOR);
        this.dbName = dbName;
        this.baseDir = baseDir;
        this.reportingIntervalMs = reportingIntervalMs;
        this.clearBaseDir = !retainContextDir;
        this.logProviderSupplier = logProviderSupplier;
        this.progressStreamSupplier = progressStreamSupplier;
    }

    public static ImportContext create(
            FileSystemAbstraction fs,
            NormalizedDatabaseName database,
            Config databaseConfig,
            boolean retainContextDir,
            boolean verbose) {
        var baseDir = newContextDir(
                fs, databaseConfig.get(import_base_context_directory).toAbsolutePath(), database.name());
        return new ImportContext(
                database.name(),
                baseDir,
                databaseConfig.get(import_detailed_reporting_interval).toMillis(),
                retainContextDir || verbose,
                () -> {
                    try {
                        return new Log4jLogProvider(output(fs, baseDir.resolve(LOG_FILE_NAME)), verbose ? DEBUG : INFO);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                },
                () -> {
                    try {
                        return new PrintStream(output(fs, baseDir.resolve(PROGRESS_REPORTING_FILE_NAME)), true);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
    }

    public void preamble(PrintStream out) {
        var baseDir = baseDir();
        out.printf("Starting to import, the following output will be saved in the directory: %s%n", baseDir);
        out.printf("  Logging information: %s%n", LOG_FILE_NAME);
        out.printf("  Detailed progress reporting (JSON formatted): %s%n", PROGRESS_REPORTING_FILE_NAME);
        if (clearBaseDir) {
            out.println();
            out.println("NOTE this directory will be cleared on the completion of a successful import.");
            out.println();
        }
    }

    public Path baseDir() {
        return baseDir;
    }

    public Path logPath() {
        return baseDir.resolve(LOG_FILE_NAME);
    }

    public Path detailedReportingPath() {
        return baseDir.resolve(PROGRESS_REPORTING_FILE_NAME);
    }

    public Exception captureError(Exception error) {
        clearBaseDir = false;
        if (error instanceof ParameterException) {
            return error;
        } else if (error instanceof FileLockException) {
            return new CommandFailedException(
                    "The database is in use. Stop database '%s' and try again.".formatted(dbName),
                    error,
                    ExitCode.FAIL);
        } else if (error instanceof CannotWriteException) {
            return new CommandFailedException("You do not have permission to import.", error, ExitCode.NOPERM);
        } else if (error instanceof CsvImportException) {
            return new CommandFailedException("Error importing csv file.", error, ExitCode.SOFTWARE);
        } else if (error instanceof UnsupportedFormatException) {
            return new CommandFailedException("Unsupported format.", error, ExitCode.SOFTWARE);
        } else if (error instanceof UncheckedIOException ioEx) {
            return transformIOException(ioEx.getCause());
        } else if (error instanceof IOException ioEx) {
            return transformIOException(ioEx);
        }
        return error;
    }

    @Override
    public InternalLog getLog(Class<?> loggingClass) {
        return logProvider().getLog(loggingClass);
    }

    @Override
    public InternalLog getLog(String name) {
        return logProvider().getLog(name);
    }

    @Override
    public InternalLog getLog(LoggerTarget target) {
        return logProvider().getLog(target);
    }

    @Override
    public long detailedProgressReportIntervalMillis() {
        return reportingIntervalMs;
    }

    @Override
    public void detailedProgressReport(DetailedProgressReport report) {
        try {
            var out = progressStream();
            OBJECT_MAPPER.writeValue(out, report);
            out.println();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void close() {
        try {
            IOUtils.closeAllUnchecked(logProvider, progressStream);
        } finally {
            clearBaseDir();
        }
    }

    private InternalLogProvider logProvider() {
        if (logProvider == null) {
            logProvider = logProviderSupplier.get();
        }
        return logProvider;
    }

    private PrintStream progressStream() {
        if (progressStream == null) {
            progressStream = progressStreamSupplier.get();
        }
        return progressStream;
    }

    private void clearBaseDir() {
        if (clearBaseDir) {
            try {
                FileUtils.deleteDirectory(baseDir);
            } catch (IOException e) {
                var error = new CommandFailedException(e, ExitCode.SOFTWARE);
                error.addSupplementaryMessage("Unable to fully clear the import context directory: " + baseDir);
                throw error;
            }
        }
    }

    private static CommandFailedException transformIOException(IOException e) {
        var error = new CommandFailedException(e, ExitCode.SOFTWARE);
        if (e instanceof NoSuchFileException ex) {
            error.addSupplementaryMessage(
                    "Check that the file '%s' exists or is specified correctly.".formatted(ex.getFile()));
        } else if (e.getCause() instanceof ProviderMismatchException) {
            error.addSupplementaryMessage("The scheme of the provided URI is not currently supported - currently "
                    + "only 's3', 'gs' and 'azb' schemes are supported.");
        } else if (e.getCause() instanceof URISyntaxException) {
            error.addSupplementaryMessage("Please check that the syntax of the URI resource provided is correct.");
        }
        return error;
    }

    private static Path newContextDir(FileSystemAbstraction fs, Path importsDir, String dbName) {
        var ts = SPACELESS_DATE_FORMATTER.format(Instant.now());
        var repeat = 0;
        Path contextDir;
        do {
            var suffix = ts + (repeat++ == 0 ? "" : "." + repeat);
            contextDir = importsDir.resolve(format(DEFAULT_LOG_DIR_TEMPLATE, dbName, suffix));
        } while (fs.fileExists(contextDir));
        return contextDir;
    }

    private static OutputStream output(FileSystemAbstraction fs, Path path) throws IOException {
        fs.mkdirs(path.getParent());
        return new BufferedOutputStream(fs.openAsOutputStream(path, true));
    }

    private static class DurationSerializer extends StdSerializer<Duration> {

        private DurationSerializer() {
            super(Duration.class);
        }

        @Override
        public void serialize(Duration duration, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException {
            jsonGenerator.writeNumber(duration.toMillis());
        }
    }
}

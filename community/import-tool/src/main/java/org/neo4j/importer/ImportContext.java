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
import static org.neo4j.logging.Level.DEBUG;
import static org.neo4j.logging.Level.INFO;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.neo4j.batchimport.api.UnsupportedFormatException;
import org.neo4j.cli.CommandFailedException;
import org.neo4j.cli.ExitCode;
import org.neo4j.commandline.dbms.CannotWriteException;
import org.neo4j.configuration.Config;
import org.neo4j.importer.FileImporter.CsvImportException;
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
public class ImportContext implements InternalLogProvider {

    public static final String LOG_FILE_NAME = "import.log";
    private static final DateTimeFormatter SPACELESS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd.HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final String DEFAULT_LOG_DIR_TEMPLATE = "%s-admin-import-%s";

    private final String dbName;

    private final Path baseDir;

    private final Supplier<InternalLogProvider> delegateProvider;

    private InternalLogProvider delegate;

    private boolean clearBaseDir;

    private ImportContext(
            String dbName, Path baseDir, boolean retainContextDir, Supplier<InternalLogProvider> delegateProvider) {
        this.dbName = dbName;
        this.baseDir = baseDir;
        this.clearBaseDir = !retainContextDir;
        this.delegateProvider = delegateProvider;
    }

    public static ImportContext create(
            FileSystemAbstraction fs,
            NormalizedDatabaseName database,
            Config databaseConfig,
            boolean retainContextDir,
            boolean verbose) {
        var baseDir = newContextDir(
                fs, databaseConfig.get(import_base_context_directory).toAbsolutePath(), database.name());
        return new ImportContext(database.name(), baseDir, retainContextDir || verbose, () -> {
            try {
                fs.mkdirs(baseDir);
                return new Log4jLogProvider(
                        new BufferedOutputStream(fs.openAsOutputStream(baseDir.resolve(LOG_FILE_NAME), true)),
                        verbose ? DEBUG : INFO);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
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

    public void preamble(PrintStream out) {
        var baseDir = baseDir();
        out.printf("Starting to import, the following output will be saved in the directory: %s%n", baseDir);
        out.printf("  Logging information: %s%n", LOG_FILE_NAME);
        if (clearBaseDir) {
            out.println();
            out.println("NOTE this directory will be cleared on the completion of a successful import.");
            out.println();
        }
    }

    public Path baseDir() {
        return baseDir;
    }

    public Path logFile() {
        return baseDir.resolve(LOG_FILE_NAME);
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
        return delegate().getLog(loggingClass);
    }

    @Override
    public InternalLog getLog(String name) {
        return delegate().getLog(name);
    }

    @Override
    public InternalLog getLog(LoggerTarget target) {
        return delegate().getLog(target);
    }

    @Override
    public void close() {
        try {
            if (delegate != null) {
                delegate.close();
            }
        } finally {
            clearBaseDir();
        }
    }

    private InternalLogProvider delegate() {
        if (delegate == null) {
            delegate = delegateProvider.get();
        }
        return delegate;
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

    static CommandFailedException transformIOException(IOException e) {
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
}

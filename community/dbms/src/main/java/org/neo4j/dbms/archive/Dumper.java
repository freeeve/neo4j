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
package org.neo4j.dbms.archive;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.Objects.requireNonNull;
import static org.neo4j.dbms.archive.LoggingArchiveProgressPrinter.createProgressPrinter;
import static org.neo4j.dbms.archive.Utils.checkWritableDirectory;
import static org.neo4j.dbms.archive.Utils.copy;
import static org.neo4j.io.fs.FileVisitors.justContinue;
import static org.neo4j.io.fs.FileVisitors.onDirectory;
import static org.neo4j.io.fs.FileVisitors.onFile;
import static org.neo4j.io.fs.FileVisitors.onlyMatching;
import static org.neo4j.io.fs.FileVisitors.throwExceptions;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.neo4j.cli.ExecutionContext;
import org.neo4j.commandline.Util;
import org.neo4j.dbms.archive.printer.OutputProgressPrinter;
import org.neo4j.dbms.archive.printer.ProgressPrinters;
import org.neo4j.function.Predicates;
import org.neo4j.function.ThrowingConsumer;
import org.neo4j.graphdb.Resource;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.util.Preconditions;

public class Dumper {
    public static final String DUMP_EXTENSION = ".dump";
    public static final String TAR_EXTENSION = ".tar";

    private final List<ArchiveOperation> operations = new ArrayList<>();

    private final FileSystemAbstraction fs;
    private final ArchiveProgressPrinter progressPrinter;
    private final boolean deleteAfterCopy;

    public Dumper(FileSystemAbstraction fs) {
        this(fs, ProgressPrinters.emptyPrinter(), false);
    }

    public Dumper(FileSystemAbstraction fs, PrintStream output) {
        this(fs, ProgressPrinters.printStreamPrinter(output), false);
    }

    public Dumper(FileSystemAbstraction fs, InternalLogProvider logProvider) {
        this(fs, ProgressPrinters.logProviderPrinter(logProvider.getLog(Dumper.class)), false);
    }

    public Dumper(FileSystemAbstraction fs, InternalLogProvider logProvider, boolean deleteAfterCopy) {
        this(fs, ProgressPrinters.logProviderPrinter(logProvider.getLog(Dumper.class)), deleteAfterCopy);
    }

    private Dumper(FileSystemAbstraction fs, OutputProgressPrinter progressPrinter, boolean deleteAfterCopy) {
        this.fs = requireNonNull(fs);
        this.progressPrinter = createProgressPrinter(progressPrinter);
        this.deleteAfterCopy = deleteAfterCopy;
    }

    /**
     * Tells whether a given path is a valid dump (based on file extension)
     * @param dump should be a dump
     * @return <code>true</code> if the provided path is a dump
     */
    public static boolean isDumpFile(Path dump) {
        return dump.toString().endsWith(DUMP_EXTENSION);
    }

    public void dump(Path path, Path archive, DumpFormat format) throws IOException {
        dump(path, path, (DumpOutput) new FileOutput(fs, archive), format, Predicates.alwaysFalse());
    }

    /**
     * @param dbPath                store file location
     * @param transactionalLogsPath tx logs location
     * @param dot                   Dump output type
     * @param format                compression format
     * @param exclude               exclusion predicate
     * @throws IOException in case of error
     */
    public void dump(
            Path dbPath, Path transactionalLogsPath, DumpOutput dot, DumpFormat format, Predicate<Path> exclude)
            throws IOException {
        operations.clear();

        visitPath(dbPath, exclude);
        if (!Util.isSameOrChildFile(dbPath, transactionalLogsPath)) {
            visitPath(transactionalLogsPath, exclude);
        }

        dump(dot, format);
    }

    /**
     * @param dot    Dump output type
     * @param format compression format
     * @throws IOException in case of error
     */
    public void dump(DumpOutput dot, DumpFormat format) throws IOException {
        progressPrinter.reset();
        for (ArchiveOperation operation : operations) {
            progressPrinter.maxBytes(progressPrinter.maxBytes() + operation.size);
            progressPrinter.maxFiles(progressPrinter.maxFiles() + (operation.isFile ? 1 : 0));
        }

        try (var stream = wrapArchiveOut(dot, format);
                Resource ignore = progressPrinter.startPrinting()) {
            for (ArchiveOperation operation : operations) {
                operation.addToArchive(stream);
                if (deleteAfterCopy && operation.isFile) {
                    fs.delete(operation.file);
                }
            }
        }
    }

    /**
     * @param folderPath folder to archive
     * @param exclude    exclusion predicate
     * @throws IOException in case of error
     */
    public void visitPath(Path folderPath, Predicate<Path> exclude) throws IOException {
        Files.walkFileTree(
                folderPath,
                onlyMatching(
                        exclude.negate(),
                        throwExceptions(onDirectory(
                                dir -> dumpDirectory(folderPath, dir),
                                onFile(file -> dumpFile(folderPath, file), justContinue())))));
    }

    private ArchiveOutputStream wrapArchiveOut(DumpOutput dot, DumpFormat format) throws IOException {
        OutputStream compress = format.compress(dot.stream());

        // Add enough archive meta-data that the load command can print a meaningful progress indicator.
        if (StandardCompressionFormat.ZSTD.isFormat(compress)) {
            writeArchiveMetadata(compress);
        }

        TarArchiveOutputStream tarball = new TarArchiveOutputStream(compress, UTF_8.name());
        tarball.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tarball.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        return tarball;
    }

    /**
     * @see Loader#readArchiveSizeMetadata(InputStream)
     */
    void writeArchiveMetadata(OutputStream stream) throws IOException {
        DataOutputStream metadata = new DataOutputStream(stream); // Unbuffered. No need for flushing.
        metadata.writeInt(1); // Archive format version. Increment whenever the metadata format changes.
        metadata.writeLong(progressPrinter.maxFiles());
        metadata.writeLong(progressPrinter.maxBytes());
    }

    private void dumpFile(Path root, Path file) throws IOException {
        withEntry(stream -> writeFile(file, stream), root, file);
    }

    private void dumpDirectory(Path root, Path dir) throws IOException {
        withEntry(stream -> {}, root, dir);
    }

    private void withEntry(ThrowingConsumer<ArchiveOutputStream, IOException> operation, Path root, Path file)
            throws IOException {
        operations.add(new ArchiveOperation(operation, root, file));
    }

    private void writeFile(Path file, ArchiveOutputStream archiveStream) throws IOException {
        try (var in = fs.openAsInputStream(file)) {
            copy(in, archiveStream, progressPrinter);
        }
    }

    private static class ArchiveOperation {
        final ThrowingConsumer<ArchiveOutputStream, IOException> operation;
        final long size;
        final boolean isFile;
        final Path root;
        final Path file;

        private ArchiveOperation(ThrowingConsumer<ArchiveOutputStream, IOException> operation, Path root, Path file)
                throws IOException {
            this.operation = operation;
            this.isFile = Files.isRegularFile(file);
            this.size = isFile ? Files.size(file) : 0;
            this.root = root;
            this.file = file;
        }

        void addToArchive(ArchiveOutputStream stream) throws IOException {
            ArchiveEntry entry = createEntry(file, root, stream);
            stream.putArchiveEntry(entry);
            operation.accept(stream);
            stream.closeArchiveEntry();
        }

        private static ArchiveEntry createEntry(Path file, Path root, ArchiveOutputStream archive) throws IOException {
            return archive.createArchiveEntry(file.toFile(), "./" + root.relativize(file));
        }
    }

    public interface DumpFormat extends CompressionFormat {}

    public interface DumpOutput {
        OutputStream stream() throws IOException;

        String description();
    }

    public record FileOutput(FileSystemAbstraction fs, Path path) implements DumpOutput {
        public FileOutput {
            Preconditions.checkState(
                    !fs.isDirectory(path), "FileOutput must target a file, not a directory: %s".formatted(path));
        }

        public OutputStream stream() throws IOException {
            // Always create the file to be sure that we are the owner.
            checkWritableDirectory(path.getParent());
            // TODO: This should probably use FileSystemAbstraction.
            return Files.newOutputStream(path, CREATE_NEW);
        }

        @Override
        public String description() {
            return path.toString();
        }

        public static FileOutput of(FileSystemAbstraction fs, Path path) {
            return new FileOutput(fs, path);
        }
    }

    public record StdoutOutput(ExecutionContext ctx) implements DumpOutput {

        @Override
        public OutputStream stream() throws IOException {
            return CloseShieldOutputStream.wrap(ctx.out());
        }

        @Override
        public String description() {
            return "writing to stdout";
        }
    }
}

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

import static java.util.Objects.requireNonNull;
import static org.neo4j.dbms.archive.StandardCompressionFormat.GZIP;

import com.github.luben.zstd.ZstdIOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.neo4j.dbms.archive.Manifest.DirectoryRecord;
import org.neo4j.dbms.archive.Manifest.FileRecord;
import org.neo4j.function.ThrowingSupplier;

/**
 * <p>
 * This utility aims to create reproducible archives, regardless of compressor chosen.
 * Given the same input files, and host system, the output of the utility should be identical.
 * <p>
 *     Note:
 *     <i>
 *      The utility attempts to help users achieve reproducible archives. It does so, by sorting the inputs,
 *      stripping permissions, and user references, and fixing the modified time. If the user does not provide
 *      modified time, then the archive is created with the reference time J2000.
 *      </i>
 * </p>
 */
public class Tarball {
    // You are one of today's 10 000 lucky ones.
    private static final FileTime J2000 = FileTime.from(Instant.parse("2000-01-01T11:58:55.816Z"));
    private static final Writer DEFAULT_WRITER = Files::copy;
    private static final Reader DEFAULT_READER = Files::copy;

    private Tarball() {}

    /**
     * Creates a GZIP-compressed TAR archive ("tarball") of the {@code source} directory (including the directory itself).
     *
     * @param source The directory to archive.
     * @param include Paths to be included in the archive.
     * @param where Directory where the archive will be written.
     * @throws UncheckedIOException if an I/O error occurs.
     * @return The path to the created archive.
     */
    public static Path gz(Path source, Predicate<Path> include, Path where) {
        try {
            return tarball(where, addExtension(GZIP, source.getFileName().toString()), GZIP, include, null, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a compressed TAR archive ("tarball") of the {@code source} paths.
     *
     * @param where Where the archive will be written, the archive will be named after the source.
     * @param filename The name of the archive (file ending will be added automatically, if needed).
     * @param compressor The compression algorithm to use.
     * @param include Paths to be included in the archive.
     * @param sources The directories to archive, each listed will be added to the root of the archive.
     * @return The path to the created archive.
     * @throws FileAlreadyExistsException If the archive filename (with appropriate file extension) already exists.
     * @throws NotDirectoryException If {@code where} is not a directory.
     * @throws IOException If it fails to write to the OutputStream, or read from the files.
     */
    public static Path tarball(
            Path where,
            String filename,
            StandardCompressionFormat compressor,
            Predicate<Path> include,
            FileTime clamped_mTime,
            Path... sources)
            throws IOException {
        requireNonNull(include);
        requireNonNull(sources);
        var manifest = Manifest.builder().add(include, sources).build();
        return tarball(where, filename, compressor, clamped_mTime, manifest);
    }

    /**
     * Creates a compressed TAR archive ("tarball") of the {@code source} paths.
     *
     * @param where Where the archive will be written, the archive will be named after the source.
     * @param filename The name of the archive (file ending will be added automatically, if needed).
     * @param compressor The compression algorithm to use.
     * @param manifest The directories to archive, each listed will be added to the root of the archive.
     * @return The path to the created archive.
     * @throws FileAlreadyExistsException If the archive filename (with appropriate file extension) already exists.
     * @throws NotDirectoryException If {@code where} is not a directory.
     * @throws IOException If it fails to write to the OutputStream, or read from the files.
     */
    public static Path tarball(
            Path where,
            String filename,
            StandardCompressionFormat compressor,
            FileTime clamped_mTime,
            Manifest manifest)
            throws IOException {
        requireDirectory(where);
        requireNotBlank(filename);
        requireNonNull(manifest);
        requireCompressor(
                compressor,
                GZIP /* Currently, StandardCompressionFormat.ZSTD writes extra data into the compressed stream,
                     making tar unable to decompress the archive*/);
        var target = where.resolve(filename);
        var stream = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW));
        create(compressor != null ? compressor.compress(stream) : stream, clamped_mTime, DEFAULT_WRITER, manifest);
        return target;
    }

    /**
     *
     * Creates a compressed TAR archive of everything in the `source(s)` directory, ignoring any symlinks.
     *
     * @param os The OutputStream to write to tarball to.
     * @param clamped_mTime Fixated modified time (for reproducible archives). Use null, if the value should be read
     *     from the file.
     * @param writer The writer to use to write the contents of the file. This allows progress monitoring instrumentation.
     * @param manifest The manifest over what to archive.
     * @throws IOException If it fails to write to the OutputStream, or read from the files.
     **/
    public static void create(OutputStream os, FileTime clamped_mTime, Writer writer, Manifest manifest)
            throws IOException {

        try (var tar = new TarArchiveOutputStream(os)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            for (var desc : manifest.files()) {
                switch (desc) {
                    case DirectoryRecord dd ->
                        tar.putArchiveEntry(
                                makeTarEntry(dd.source(), dd.target().toString(), clamped_mTime));
                    case FileRecord fd -> {
                        tar.putArchiveEntry(
                                makeTarEntry(fd.source(), fd.target().toString(), clamped_mTime));
                        // Write contents
                        writer.write(fd.source(), tar);
                    }
                }
                tar.closeArchiveEntry();
            }
        }
    }
    /**
     * Reads a compressed TAR archive and output its contents to the `target` directory.
     * The method attempts to determine the compression algorithm used to create the archive from
     * {@link StandardCompressionFormat}.
     * <p>Note:<i>
     * The implementation assumes that it is allowed to create the necessary files and directories
     * as needed.
     * </i></p>
     *
     * @param archive The archive to read.
     * @param target Where the archive will be written.
     * @throws IOException
     */
    public static void extract(Path archive, Path target) throws IOException {
        extract(archive, target, DEFAULT_READER);
    }

    /**
     * Reads a compressed TAR archive and output its contents to the `target` directory.
     * The method attempts to determine the compression algorithm used to create the archive from
     * {@link StandardCompressionFormat}.
     * <p>Note:<i>
     * The implementation assumes that it is allowed to create the necessary files and directories
     * as needed.
     * </i></p>
     *
     * @param archive The archive to read.
     * @param target Where the archive will be written.
     * @param reader The reader to read the content of the archive, to the target path.
     * @throws IOException
     */
    public static void extract(Path archive, Path target, Reader reader) throws IOException {
        requireFile(archive);
        requireDirectory(target);
        try (var tar = selectDecompressor(() -> new BufferedInputStream(Files.newInputStream(archive)))) {
            while (tar.getNextEntry() != null) {
                var entry = tar.getCurrentEntry();
                var pth = target.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(pth);
                } else if (entry.isFile()) {
                    Files.createDirectories(pth.getParent());
                    reader.read(tar, pth);
                }
            }
        }
    }

    private static TarArchiveInputStream selectDecompressor(ThrowingSupplier<InputStream, IOException> supplier)
            throws IOException {
        try {
            return new TarArchiveInputStream(StandardCompressionFormat.decompress(supplier));
        } catch (ZipException | ZstdIOException exc) {
            // fallthrough
            return new TarArchiveInputStream(supplier.get());
        }
    }

    private static void requireDirectory(Path source) throws NotDirectoryException {
        if (!Files.isDirectory(source)) {
            throw new NotDirectoryException(source.toString());
        }
    }

    private static void requireFile(Path target) {
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Required a file, but got:" + target);
        }
    }

    private static void requireNotBlank(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("String cannot be blank");
        }
    }

    private static void requireCompressor(
            StandardCompressionFormat compressor, StandardCompressionFormat... validCompressors) {
        if (compressor == null) {
            return;
        }
        var valid = Arrays.asList(validCompressors);
        if (!valid.contains(compressor)) {
            throw new IllegalArgumentException(
                    "Invalid compressor: %s, require one of %s".formatted(compressor, valid));
        }
    }

    /**
     * Generate a reproducible tar entry.
     *
     * @param file The file to scan
     * @param filename The name in the archive
     * @param mtime The modified time to use
     * @return A reproducible TarArchiveEntry
     * @throws IOException If an I/O error occurs.
     */
    private static TarArchiveEntry makeTarEntry(Path file, String filename, FileTime mtime) throws IOException {
        var entry = new TarArchiveEntry(file, filename);
        // delete ctime
        entry.setCreationTime(null);
        entry.setStatusChangeTime(null);
        // delete atime
        entry.setLastAccessTime(null);
        // fixate mtime
        entry.setLastModifiedTime(mtime != null ? mtime : J2000);
        // Pin users for good measure.
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setUserName("");
        entry.setGroupName("");
        return entry;
    }

    private static String addExtension(StandardCompressionFormat format, String filename) {
        var extension =
                switch (format) {
                    case GZIP -> ".tar.gz";
                    case ZSTD -> ".tar.zst";
                    case null -> ".tar";
                };
        if (filename.endsWith(extension)) {
            return filename;
        }
        return filename + extension;
    }

    @FunctionalInterface
    public interface Writer {
        void write(Path source, OutputStream os) throws IOException;
    }

    @FunctionalInterface
    public interface Reader {
        void read(InputStream is, Path target) throws IOException;
    }
}

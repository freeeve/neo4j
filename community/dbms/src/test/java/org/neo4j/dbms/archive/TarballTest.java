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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.function.Predicates;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class TarballTest {

    @Inject
    TestDirectory testDirectory;

    private Path where;
    private Path source;
    private Path extract;

    @BeforeEach
    void setup() throws IOException {
        where = testDirectory.homePath();
        source = testDirectory.directory("source");
        extract = testDirectory.directory("extract");
        writeSourceTree(source);
    }

    @Test
    void tarballDoesNotSupportLegacyZstd() {
        assertThatThrownBy(() -> Tarball.tarball(
                        testDirectory.homePath(),
                        "archie",
                        StandardCompressionFormat.ZSTD,
                        Predicates.alwaysTrue(),
                        null,
                        Tarball.list(source, Predicates.alwaysTrue())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = StandardCompressionFormat.class,
            names = {"ZSTD"},
            mode = EnumSource.Mode.EXCLUDE)
    void consistencyCheck(StandardCompressionFormat compressor) throws IOException {
        var archive = Tarball.tarball(
                testDirectory.homePath(),
                "archie",
                compressor,
                Predicates.alwaysTrue(),
                null,
                Tarball.list(source, Predicates.alwaysTrue()));
        assertThat(archive).exists();
        assertThat(archive).isNotEmptyFile();

        Tarball.extract(archive, extract);

        assertSourceTree(extract);
    }

    @ParameterizedTest
    @EnumSource(
            value = StandardCompressionFormat.class,
            names = {"ZSTD"},
            mode = EnumSource.Mode.EXCLUDE)
    void reproducibleTarballs(StandardCompressionFormat compressor) throws IOException {
        var clamped_mTime = FileTime.from(Instant.now());
        var archive1 = Tarball.tarball(where, "archie1", compressor, Predicates.alwaysTrue(), clamped_mTime, source);

        FileUtils.deleteDirectory(source);
        writeSourceTree(source);

        var archive2 = Tarball.tarball(where, "archie2", compressor, Predicates.alwaysTrue(), clamped_mTime, source);

        assertThat(archive1).hasSameBinaryContentAs(archive2);
    }

    @Test
    void refuseToOverwrite() throws IOException {
        var filename = "archie";
        var archive = where.resolve(filename);

        Files.writeString(archive, "");
        assertThatThrownBy(() -> Tarball.tarball(where, filename, null, Predicates.alwaysTrue(), null, source))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void tarballHasSourceFolderIncluded() throws IOException {
        var archive = Tarball.tarball(where, "archie", null, Predicates.alwaysTrue(), null, source);

        assertThat(archive).exists();
        Tarball.extract(archive, extract);
        assertSourceTree(extract.resolve("source"));
    }

    @Test
    void skipsSymlinks() throws IOException {
        Files.createSymbolicLink(source.resolve("d"), source.resolve("a"));
        var archive = Tarball.tarball(where, "archie", null, Predicates.alwaysTrue(), null, source);
        Tarball.extract(archive, extract);

        // Check that nothing except the expected is present
        assertSourceTree(extract.resolve("source"));
    }

    @Test
    void multipleRootFoldersInTarball() throws IOException {
        var source2 = testDirectory.directory("source2");
        FileUtils.copyDirectory(source, source2);

        var archive = Tarball.tarball(where, "archie", null, Predicates.alwaysTrue(), null, source, source2);
        Tarball.extract(archive, extract);

        assertSourceTree(extract.resolve("source"));
        assertSourceTree(extract.resolve("source2"));
    }

    @Test
    void listFilesWorks() throws IOException {
        var files = Tarball.list(source, Predicates.alwaysTrue());
        assertThat(files).containsExactly(source.resolve("a"), source.resolve("b"), source.resolve("c"));
    }

    @Test
    void listRecursiveWorks() throws IOException {
        var files = Tarball.listRecursive(source, Predicates.alwaysTrue());
        assertThat(files)
                .containsExactly(
                        source,
                        source.resolve("a"),
                        source.resolve("a").resolve("aa"),
                        source.resolve("a").resolve("ab"),
                        source.resolve("b"),
                        source.resolve("b").resolve("ba"),
                        source.resolve("b").resolve("ba").resolve("baa"),
                        source.resolve("c"));
    }

    private void writeSourceTree(Path source) throws IOException {
        // Build a tree of files
        //         source
        //      /    |    \
        //     a     b     c
        //   /  \    |      \
        //  aa  ab  ba     (empty)
        //           |
        //          baa
        Path a = source.resolve("a");
        Path aa = a.resolve("aa");
        Path ab = a.resolve("ab");
        Path b = source.resolve("b");
        Path ba = b.resolve("ba");
        Path baa = ba.resolve("baa");
        Path c = source.resolve("c");

        Files.createDirectories(a);
        Files.createDirectories(ba);
        Files.createDirectories(c);

        Files.writeString(aa, "aa");
        Files.writeString(ab, "ab");
        Files.writeString(baa, "baa");
    }

    private void assertSourceTree(Path source) throws IOException {
        Path a = source.resolve("a");
        Path aa = a.resolve("aa");
        Path ab = a.resolve("ab");
        Path b = source.resolve("b");
        Path ba = b.resolve("ba");
        Path baa = ba.resolve("baa");
        Path c = source.resolve("c");

        var expectedFiles = Set.of(source, a, aa, ab, b, ba, baa, c);
        var actualFiles = Set.copyOf(Arrays.asList(Tarball.listRecursive(source, Predicates.alwaysTrue())));

        Set<Path> missingFiles = new HashSet<>(expectedFiles);
        missingFiles.removeAll(actualFiles);
        assertThat(missingFiles).as("Missing files").isEmpty();

        Set<Path> extraFiles = new HashSet<>(actualFiles);
        extraFiles.removeAll(expectedFiles);
        assertThat(extraFiles).as("Extra files").isEmpty();

        assertThat(a).isDirectory();
        assertThat(aa).isNotEmptyFile();
        assertThat(ab).isNotEmptyFile();
        assertThat(b).isDirectory();
        assertThat(ba).isDirectory();
        assertThat(baa).isNotEmptyFile();
        assertThat(c).isEmptyDirectory();
    }
}

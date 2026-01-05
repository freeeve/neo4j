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
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
@RandomSupportExtension
class ManifestTest {

    @Inject
    TestDirectory testDirectory;

    @Inject
    RandomSupport random;

    private Path source;

    @BeforeEach
    void setup() throws IOException {
        source = testDirectory.directory("source");
        writeRecords(testDirectory.homePath(), expectedRecords());
    }

    @Test
    void builderCanAddFolder() throws IOException {
        var manifest = Manifest.builder().add(source).build();
        var records = expectedRecords();
        assertThat(manifest.files()).isEqualTo(records);
        assertThat(manifest).isEqualTo(Manifest.of(records));
    }

    @Test
    void builderCanAddFile() throws IOException {
        var file = testDirectory.file("hello");
        byte[] content = "hello".getBytes();
        Files.write(file, content);
        var manifest = Manifest.builder().add(file).build();
        assertThat(manifest.files()).containsExactly(new Manifest.FileRecord(file, content.length, Path.of("hello")));
    }

    @Test
    void builderCanAddContents() throws IOException {
        // a, b, c
        var flat = Manifest.builder().addContentsOf(source).build().files();
        // source/a, source/b, source/c
        var folder = Manifest.builder().add(source).build().files();

        for (int i = 1; i < folder.length; i++) {
            assertThat(flat[i - 1].source()).isEqualTo(folder[i].source());
            assertThat(source.getFileName().resolve(flat[i - 1].target())).isEqualTo(folder[i].target());
        }
    }

    @Test
    void builderIgnoresSymlinks() throws IOException {
        var link = testDirectory.homePath().resolve("link");
        Files.createSymbolicLink(link, source);
        assertThat(Manifest.builder().add(link).build().files()).isEmpty();
    }

    @Test
    void shouldOrderRecords() {
        var actualRecords = expectedRecords();
        ArrayUtils.shuffle(actualRecords, random.random());
        var actualManifest = Manifest.of(actualRecords);
        var expectedManifest = Manifest.of(expectedRecords());
        assertThat(actualManifest).isEqualTo(expectedManifest);
        assertThat(actualManifest.files()).isEqualTo(expectedManifest.files());
    }

    @Test
    void shouldAllowSameSourceWithDifferentTargets() {
        Manifest.of(
                new Manifest.FileRecord(source, 0, Path.of("target")),
                new Manifest.FileRecord(source, 0, Path.of("other_target")));
    }

    @Test
    void shouldNotAllowDuplicateTargets() {
        assertThatThrownBy(() -> Manifest.of(
                        new Manifest.FileRecord(source.resolve("this"), 0, Path.of("target")),
                        new Manifest.FileRecord(source.resolve("that"), 0, Path.of("target"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotAllowDuplicateRecords() {
        var record = new Manifest.FileRecord(source.resolve("this"), 0, Path.of("target"));
        assertThatThrownBy(() -> Manifest.of(record, record)).isInstanceOf(IllegalArgumentException.class);
    }

    Path relativize(Path pth) {
        return source.getFileName().resolve(source.relativize(pth));
    }

    Manifest.ManifestRecord[] expectedRecords() {
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

        return new Manifest.ManifestRecord[] {
            new Manifest.DirectoryRecord(source, relativize(source)),
            new Manifest.DirectoryRecord(a, relativize(a)),
            new Manifest.FileRecord(aa, aa.getFileName().toString().getBytes().length, relativize(aa)),
            new Manifest.FileRecord(ab, ab.getFileName().toString().getBytes().length, relativize(ab)),
            new Manifest.DirectoryRecord(b, relativize(b)),
            new Manifest.DirectoryRecord(ba, relativize(ba)),
            new Manifest.FileRecord(baa, baa.getFileName().toString().getBytes().length, relativize(baa)),
            new Manifest.DirectoryRecord(c, relativize(c))
        };
    }

    void writeRecords(Path where, Manifest.ManifestRecord[] records) throws IOException {
        for (var record : records) {
            if (record.target().equals(where)) {
                continue;
            }
            switch (record) {
                case Manifest.FileRecord fr ->
                    Files.write(
                            where.resolve(fr.target()),
                            fr.target().getFileName().toString().getBytes());
                case Manifest.DirectoryRecord dr -> Files.createDirectories(where.resolve(dr.target()));
            }
        }
    }
}

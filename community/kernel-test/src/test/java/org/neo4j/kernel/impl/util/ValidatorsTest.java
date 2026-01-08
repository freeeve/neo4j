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
package org.neo4j.kernel.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.io.fs.FileSystemAbstraction.PatternStyle.glob;
import static org.neo4j.io.fs.FileSystemAbstraction.PatternStyle.regex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.neo4j.cloud.storage.SchemeFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction.PatternStyle;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class ValidatorsTest {
    @Inject
    private TestDirectory directory;

    @Inject
    private FileSystemAbstraction filesystem;

    @Test
    void shouldFindLocalFilesByRegex() throws Exception {
        // GIVEN
        final var abc = existenceOfFile("abc");
        final var bcd = existenceOfFile("bcd");
        final var qwer0 = existenceOfFile("qwer.0");
        final var qwer1 = existenceOfFile("qwer.1");
        final var qwer2 = existenceOfFile("qwer.2");
        final var qwer10 = existenceOfFile("qwer.10");

        // WHEN/THEN
        assertValid("abc", regex, abc);
        assertValid("bcd", regex, bcd);
        assertValid("ab.", regex, abc);
        assertValid(".*bc", regex, abc);
        assertValid(".*bc.*", regex, abc, bcd);
        assertValid("qwer\\.\\d", regex, qwer0, qwer1, qwer2);
        assertValid("qwer\\.\\p{Digit}", regex, qwer0, qwer1, qwer2);
        assertValid("qwer\\.\\d+", regex, qwer0, qwer1, qwer2, qwer10);
        assertValid("qwer\\.\\d{1,2}", regex, qwer0, qwer1, qwer2, qwer10);

        assertNotValid(regex, "abcd");
        assertNotValid(regex, ".*de.*");
        assertNotValid(regex, "qwer\\.\\d{3,}");
    }

    @Test
    void shouldFindStoragePathsByRegex() throws Exception {
        // GIVEN
        final var abc = existenceOfFile("abc");
        final var bcd = existenceOfFile("bcd");
        final var qwer0 = existenceOfFile("qwer.0");
        final var qwer1 = existenceOfFile("qwer.1");
        final var qwer2 = existenceOfFile("qwer.2");
        final var qwer10 = existenceOfFile("qwer.10");

        final var base = directory.homePath().toUri().toString();
        assertThat(base).endsWith("/");

        // the file scheme resolver is always present so will be able to handle the file URIs below
        final var schemeFilesystem = new SchemeFileSystemAbstraction(filesystem);

        // WHEN/THEN
        assertValid(schemeFilesystem, regex, base + "abc", abc);
        assertValid(schemeFilesystem, regex, base + "bcd", bcd);
        assertValid(schemeFilesystem, regex, base + "ab.", abc);
        assertValid(schemeFilesystem, regex, base + ".*bc", abc);
        assertValid(schemeFilesystem, regex, base + ".*bc.*", abc, bcd);
        assertValid(schemeFilesystem, regex, base + "qwer\\.\\d", qwer0, qwer1, qwer2);
        assertValid(schemeFilesystem, regex, base + "qwer\\.\\p{Digit}", qwer0, qwer1, qwer2);
        assertValid(schemeFilesystem, regex, base + "qwer\\.\\d+", qwer0, qwer1, qwer2, qwer10);
        assertValid(schemeFilesystem, regex, base + "qwer\\.\\d{1,2}", qwer0, qwer1, qwer2, qwer10);

        assertNotValid(schemeFilesystem, regex, base + "abcd");
        assertNotValid(schemeFilesystem, regex, base + ".*de.*");
        assertNotValid(schemeFilesystem, regex, base + "qwer\\.\\d{3,}");
    }

    @Test
    void shouldFindPathsWithGlobbingPattern() throws IOException {
        // given
        var abc = existenceOfFile("abc");
        var bcd = existenceOfFile("bcd");
        var qwer0 = existenceOfFile(new String[] {"sub1"}, "qwer.0");
        var qwer1 = existenceOfFile(new String[] {"sub1", "sub2"}, "qwer.1");
        var qwer10 = existenceOfFile(new String[] {"sub1"}, "qwer.10");
        var qwer2 = existenceOfFile(new String[] {"sub2"}, "qwer.2");
        var qwer3 = existenceOfFile(new String[] {"sub1", "sub2", "sub3a", "sub3b"}, "qwer.3");
        var qwer4 = existenceOfFile(new String[] {"sub1", "sub2", "sub3a", "sub4"}, "qwer.4");
        var qwer5 = existenceOfFile(new String[] {"sub1", "sub2", "sub3", "sub4"}, "qwer.5");

        // then
        assertValid("*c*", glob, abc, bcd);
        assertValid("**/qw*", glob, qwer0, qwer1, qwer2, qwer3, qwer4, qwer5, qwer10);

        assertValid("sub1/**/qw*", glob, qwer1, qwer3, qwer4, qwer5);
        assertValid("sub1/**/sub4/qw*", glob, qwer4, qwer5);
        assertValid("**/sub3a/**/qw*", glob, qwer3, qwer4);
    }

    private String escapeForWindows(String input) {
        // Fixup the regex escaping so that operating on Windows works
        final var home = directory.homePath();
        final var sep = home.getFileSystem().getSeparator();
        final var regex = StringEscapeUtils.escapeJava(input);
        return home + sep + regex;
    }

    private void assertValid(String fileByName, PatternStyle patternStyle, Path... expected) {
        assertValid(filesystem, patternStyle, escapeForWindows(fileByName), expected);
    }

    private static void assertValid(
            FileSystemAbstraction fs, PatternStyle patternStyle, String fileByName, Path... expected) {
        final var matching = validate(fs, patternStyle, fileByName);
        assertThat(matching).containsExactlyInAnyOrder(expected);
    }

    private void assertNotValid(PatternStyle patternStyle, String string) {
        assertNotValid(filesystem, patternStyle, escapeForWindows(string));
    }

    private static void assertNotValid(FileSystemAbstraction fs, PatternStyle patternStyle, String fileByName) {
        assertThatThrownBy(() -> validate(fs, patternStyle, fileByName)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Path> validate(FileSystemAbstraction fs, PatternStyle patternStyle, String fileByName) {
        return Validators.matchingFiles(fs, patternStyle, fileByName);
    }

    private Path existenceOfFile(String name) throws IOException {
        return existenceOfFile(new String[0], name);
    }

    private Path existenceOfFile(String[] subDirs, String name) throws IOException {
        Path base = directory.homePath();
        for (String subDir : subDirs) {
            base = base.resolve(subDir);
            filesystem.mkdir(base);
        }
        return Files.createFile(base.resolve(name));
    }
}

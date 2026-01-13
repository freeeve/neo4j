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

import static org.neo4j.util.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.neo4j.cloud.storage.PathRepresentation;
import org.neo4j.cloud.storage.SchemeFileSystemAbstraction;
import org.neo4j.cloud.storage.StorageSchemeResolver;
import org.neo4j.common.Validator;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction.PatternStyle;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.storageengine.api.StorageEngineFactory;

public final class Validators {

    private static final String GLOB_PATTERN = "**";

    private static final String WINDOWS_SEPARATOR = "\\";

    private Validators() {}

    static List<Path> matchingFiles(FileSystemAbstraction fs, PatternStyle patternStyle, String pathPattern) {
        try {
            List<Path> paths =
                    switch (patternStyle) {
                        case GLOB -> recursivePaths(fs, pathPattern);
                        case REGEX -> regexPaths(fs, pathPattern);
                        case NONE -> directPath(fs, pathPattern);
                    };

            if (paths.isEmpty()) {
                throw new IllegalArgumentException("File '" + pathPattern + "' doesn't exist");
            }

            return paths;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static final Validator<Path> CONTAINS_EXISTING_DATABASE = dbDir -> {
        try (var fileSystem = new DefaultFileSystemAbstraction()) {
            if (!isExistingDatabase(fileSystem, DatabaseLayout.ofFlat(dbDir))) {
                throw new IllegalArgumentException("Directory '" + dbDir + "' does not contain a database");
            }
        }
    };

    public static boolean isExistingDatabase(FileSystemAbstraction fileSystem, DatabaseLayout layout) {
        return StorageEngineFactory.selectStorageEngine(fileSystem, layout).isPresent();
    }

    public static boolean isExistingDatabase(
            StorageEngineFactory.Selector storageEngineSelector,
            FileSystemAbstraction fileSystem,
            DatabaseLayout layout) {
        return storageEngineSelector.selectStorageEngine(fileSystem, layout).isPresent();
    }

    public static <T> Validator<T> emptyValidator() {
        return value -> {};
    }

    private static List<Path> recursivePaths(FileSystemAbstraction fs, String pathPattern) throws IOException {
        int ix = pathPattern.indexOf(GLOB_PATTERN);
        if (ix == -1) {
            // no globs provided so fallback to parent path pattern matching
            return regexPaths(fs, PatternStyle.GLOB, pathPattern);
        }
        Path parent = resolvePath(fs, pathPattern.substring(0, ix));
        checkState(fs.fileExists(parent), "Directory %s of %s doesn't exist", parent, pathPattern);
        return fs.matchFiles(parent, PatternStyle.GLOB, pathPattern.substring(ix));
    }

    private static List<Path> regexPaths(FileSystemAbstraction fs, String pathPattern) throws IOException {
        return regexPaths(fs, PatternStyle.REGEX, pathPattern);
    }

    private static List<Path> regexPaths(FileSystemAbstraction fs, PatternStyle patternStyle, String pathPattern)
            throws IOException {
        RegexPath regexPath;
        if (pathSeparator(pathPattern).equals(WINDOWS_SEPARATOR)) {
            regexPath = regexPathOnWindows(pathPattern);
        } else {
            regexPath = regexPath(pathPattern, pathPattern.lastIndexOf(PathRepresentation.SEPARATOR));
        }

        Path parent = resolvePath(fs, regexPath.parentPart);
        checkState(fs.fileExists(parent), "Directory %s of %s doesn't exist", parent, pathPattern);
        return fs.matchFiles(parent, patternStyle, regexPath.regexPart.replace("\\\\", "\\"));
    }

    private static RegexPath regexPathOnWindows(String pathPattern) {
        var pos = 0;
        var ix = -1;
        while (true) {
            var nextIx = pathPattern.indexOf(File.separatorChar, pos);
            if (nextIx == -1) {
                // no more to scan so parent is up to last seen separator
                return regexPath(pathPattern, ix);
            } else {
                if (nextIx + 1 == pathPattern.length()) {
                    // scanned to the end so parent is up to last seen separator
                    return regexPath(pathPattern, ix);
                } else {
                    if (pathPattern.charAt(nextIx + 1) == File.separatorChar) {
                        // found the start of a regex pattern so the parent must have already been scanned
                        return regexPath(pathPattern, ix);
                    } else {
                        ix = nextIx;
                        pos = ix + 1;
                    }
                }
            }
        }
    }

    private static RegexPath regexPath(String pathPattern, int splitIndex) {
        if (splitIndex != -1) {
            var regexPart = pathPattern.substring(splitIndex + 1);
            if (!regexPart.isEmpty()) {
                return new RegexPath(pathPattern.substring(0, splitIndex + 1), regexPart);
            }
        }

        throw new IllegalArgumentException("Unable to find the parent of the path: " + pathPattern);
    }

    private static List<Path> directPath(FileSystemAbstraction fs, String pathPattern) throws IOException {
        return List.of(resolvePath(fs, pathPattern));
    }

    private static String pathSeparator(String pathPattern) {
        if (StorageSchemeResolver.isSchemeBased(pathPattern) || pathPattern.startsWith(PathRepresentation.SEPARATOR)) {
            return PathRepresentation.SEPARATOR;
        }
        return File.separator;
    }

    private static Path resolvePath(FileSystemAbstraction fs, String path) throws IOException {
        if (fs instanceof SchemeFileSystemAbstraction system) {
            return system.resolve(path).toRealPath();
        }

        return Path.of(path).toRealPath();
    }

    private record RegexPath(String parentPart, String regexPart) {}
}

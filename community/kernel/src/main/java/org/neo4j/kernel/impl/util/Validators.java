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
import org.neo4j.common.Validator;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction.PatternStyle;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.storageengine.api.StorageEngineFactory;

public final class Validators {
    private static final String GLOB_PATTERN = "**";

    private Validators() {}

    static List<Path> matchingFiles(FileSystemAbstraction fs, PatternStyle patternStyle, String pathPattern) {
        try {
            List<Path> paths =
                    switch (patternStyle) {
                        case GLOB -> recursivePaths(fs, pathPattern);
                        case REGEX -> regexOnParentPaths(fs, patternStyle, pathPattern);
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
            return regexOnParentPaths(fs, PatternStyle.GLOB, pathPattern);
        }
        Path parent = resolvePath(fs, pathPattern.substring(0, ix));
        checkState(fs.fileExists(parent), "Directory %s of %s doesn't exist", parent, pathPattern);
        return fs.matchFiles(parent, PatternStyle.GLOB, pathPattern.substring(ix));
    }

    private static List<Path> regexOnParentPaths(
            FileSystemAbstraction fs, PatternStyle patternStyle, String pathPattern) throws IOException {
        String separator = pathSeparator(fs, pathPattern);
        int pos = pathPattern.length();

        int ix;
        while (true) {
            ix = pathPattern.lastIndexOf(separator, pos);
            if (ix != -1) {
                // special handling of regex patterns for Windows paths as they naturally contain \ characters
                // and these can also appear as part of a regex
                if (separator.equals("\\") && pathPattern.charAt(ix - 1) == separator.charAt(0)) {
                    pos = ix - 1;
                    continue;
                }

                break;
            }

            throw new IllegalArgumentException("Unable to find the parent of the path: " + pathPattern);
        }

        Path parent = resolvePath(fs, pathPattern.substring(0, ix + 1));
        checkState(fs.fileExists(parent), "Directory %s of %s doesn't exist", parent, pathPattern);
        return fs.matchFiles(parent, patternStyle, pathPattern.substring(ix + 1).replace("\\\\", "\\"));
    }

    private static List<Path> directPath(FileSystemAbstraction fs, String pathPattern) throws IOException {
        return List.of(resolvePath(fs, pathPattern));
    }

    private static String pathSeparator(FileSystemAbstraction fs, String pathPattern) {
        if (fs instanceof SchemeFileSystemAbstraction system) {
            if (system.canResolve(pathPattern)) {
                return PathRepresentation.SEPARATOR;
            }
        }

        return File.separator;
    }

    private static Path resolvePath(FileSystemAbstraction fs, String path) throws IOException {
        if (fs instanceof SchemeFileSystemAbstraction system) {
            return system.resolve(path).toRealPath();
        }

        return Path.of(path).toRealPath();
    }
}

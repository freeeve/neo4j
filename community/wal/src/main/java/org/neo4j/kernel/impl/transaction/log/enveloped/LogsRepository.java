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
package org.neo4j.kernel.impl.transaction.log.enveloped;

import static org.neo4j.kernel.impl.transaction.log.enveloped.EnvelopedFilePattern.VERSION_SUFFIX;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Pattern;
import org.neo4j.internal.helpers.collection.LongRange;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.StoreChannel;

public class LogsRepository {
    static final long BASE_VERSION = 0;
    private final FileSystemAbstraction fs;
    private final Path directory;
    private final String baseName;

    private final Pattern pattern;

    public LogsRepository(FileSystemAbstraction fs, Path directory, String baseName) {
        if (fs.fileExists(directory) && !fs.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        this.fs = fs;
        this.directory = directory;
        this.baseName = baseName;
        this.pattern = EnvelopedFilePattern.envelopedFilePattern(baseName);
    }

    public LogChannelContext<StoreChannel> openReadChannel(long version) throws IOException {
        return openLogChannel(version, Set.of(StandardOpenOption.READ));
    }

    public LogChannelContext<StoreChannel> createWriteChannel(long version) throws IOException {
        return openLogChannel(version, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    }

    public LogChannelContext<StoreChannel> openWriteChannel(long version) throws IOException {
        return openLogChannel(version, Set.of(StandardOpenOption.WRITE));
    }

    private LogChannelContext<StoreChannel> openLogChannel(long version, Set<OpenOption> options) throws IOException {
        var path = pathFor(version);
        return new LogChannelContext<>(fs.open(path, options), path, version);
    }

    void deleteLogFilesFrom(long fromVersion) throws IOException {
        deleteLogFilesWithinRange(LongRange.range(fromVersion, Long.MAX_VALUE));
    }

    void deleteLogFilesTo(long toVersion) throws IOException {
        deleteLogFilesWithinRange(LongRange.range(0, toVersion));
    }

    private void deleteLogFilesWithinRange(LongRange range) throws IOException {
        var reverse = range.to() == Long.MAX_VALUE;
        var listLogFiles = listLogFiles(reverse);
        // delete files in order; from the desired end or to the desired beginning.
        for (Path path : listLogFiles) {
            var version = getVersion(path.getFileName().toString());
            if (range.isWithinRange(version)) {
                fs.deleteFile(path);
            } else {
                // sorted list so no reason to continue
                return;
            }
        }
    }

    long[] logVersions(boolean reversed) throws IOException {
        return Arrays.stream(listLogFiles(reversed))
                .mapToLong(path -> getVersion(path.getFileName().toString()))
                .toArray();
    }

    LongRange logVersionsRange() throws IOException {
        var versions = logVersions(false);
        if (versions.length == 0) {
            return LongRange.EMPTY_RANGE;
        }
        return LongRange.range(versions[0], versions[versions.length - 1]);
    }

    public long latestVersion() throws IOException {
        return logVersionsRange().to();
    }

    public boolean isEmpty() throws IOException {
        return listLogFiles(false).length == 0;
    }

    private Long getVersion(String fileName) {
        var matcher = pattern.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Unexpected file with no version. Got " + fileName);
        }
        return Long.parseLong(matcher.group("VERSION"));
    }

    private Path[] listLogFiles(boolean reverse) throws IOException {
        Comparator<Path> comparator = (o1, o2) -> getVersion(o1.getFileName().toString())
                .compareTo(getVersion(o2.getFileName().toString()));
        if (reverse) {
            comparator = comparator.reversed();
        }
        var paths = fs.listFiles(
                directory,
                entry -> pattern.matcher(entry.getFileName().toString()).matches());
        Arrays.sort(paths, comparator);
        return paths;
    }

    Path pathFor(long version) {
        return directory.resolve(baseName + VERSION_SUFFIX + version);
    }

    void initialise() throws IOException {
        if (!fs.fileExists(directory)) {
            fs.mkdir(directory);
        }
    }
}

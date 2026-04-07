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
package org.neo4j.kernel.impl.transaction.log.pruning;

import static org.neo4j.storageengine.AppendIndexProvider.UNKNOWN_APPEND_INDEX;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.kernel.impl.transaction.log.LogFileInformation;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;

public final class BackupThreshold implements Threshold {

    private final long minSize;
    private final FileSizeThreshold sizeThreshold;
    private final InternalLog log;
    private volatile long backupAppendIndex = UNKNOWN_APPEND_INDEX;

    public BackupThreshold(InternalLogProvider logProvider, long minSize, FileSizeThreshold sizeThreshold) {
        this.log = logProvider.getLog(getClass());
        this.minSize = minSize;
        this.sizeThreshold = sizeThreshold;
    }

    @Override
    public void init() {
        sizeThreshold.init();
    }

    @Override
    public boolean reached(Path path, long version, LogFileInformation source) {
        long appendIndex = backupAppendIndex;
        if (appendIndex == UNKNOWN_APPEND_INDEX) {
            return sizeThreshold.reached(path, version, source);
        }
        try {
            if (sizeThreshold.reached(path, version, source)) {
                return true;
            }

            long previousFileLastAppendIndex = source.getPreviousAppendIndexFromHeader(version);
            if (previousFileLastAppendIndex == -1) {
                log.warn(
                        "Failed to get append index of the first entry in the transaction log file. Requested version: "
                                + version);
                return false;
            }
            long currentSize = sizeThreshold.getCurrentSize();
            return currentSize > minSize && previousFileLastAppendIndex < backupAppendIndex;
        } catch (IOException e) {
            log.warn(
                    "Error on attempt to calculate reachability threshold from transaction log files. Checked version: "
                            + version,
                    e);
            return false;
        }
    }

    public void setBackupAppendIndex(long backupAppendIndex) {
        this.backupAppendIndex = backupAppendIndex;
    }

    @Override
    public String toString() {
        return "backup" + " " + backupAppendIndex + (minSize > 0 ? " " + minSize + " size" : StringUtils.EMPTY)
                + " "
                + sizeThreshold;
    }
}

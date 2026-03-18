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

import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;
import org.neo4j.kernel.impl.transaction.log.entry.LogHeader;
import org.neo4j.storageengine.api.StoreId;

public class BaseLogHeaderFactory implements LogHeaderFactory {

    private volatile KernelVersion currentAppendedDatabaseVersion;

    public BaseLogHeaderFactory(KernelVersion currentAppendedDatabaseVersion) {
        this.currentAppendedDatabaseVersion = currentAppendedDatabaseVersion;
    }

    @Override
    public LogHeader createLogHeader(
            long newFileVersion, long lastAppendIndex, int lastChecksum, int segmentSize, long preFileTerm) {
        var version = getCurrentDatabaseVersion();
        return LogFormat.fromKernelVersion(version)
                .newHeader(
                        newFileVersion,
                        lastAppendIndex,
                        preFileTerm,
                        StoreId.UNKNOWN,
                        segmentSize,
                        lastChecksum,
                        version);
    }

    public void setVersion(KernelVersion databaseVersion) {
        this.currentAppendedDatabaseVersion = databaseVersion;
    }

    public KernelVersion getCurrentDatabaseVersion() {
        if (currentAppendedDatabaseVersion == null) {
            throw new IllegalStateException("No version has been set for the current log");
        }
        return currentAppendedDatabaseVersion;
    }
}

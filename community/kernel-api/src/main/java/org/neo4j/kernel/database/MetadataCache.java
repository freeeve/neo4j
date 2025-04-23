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
package org.neo4j.kernel.database;

import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.KernelVersionRepository;
import org.neo4j.kernel.impl.transaction.log.LogFormatVersionRepository;
import org.neo4j.kernel.impl.transaction.log.LogTailMetadata;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;

public class MetadataCache implements KernelVersionRepository, LogFormatVersionRepository {
    private volatile KernelVersion kernelVersion;
    private volatile LogFormat logFormat;

    public MetadataCache(LogTailMetadata logTailMetadata) {
        this(logTailMetadata.kernelVersion(), logTailMetadata.getCurrentLogFormat());
    }

    public MetadataCache(KernelVersion kernelVersion) {
        this(kernelVersion, LogFormat.fromKernelVersion(kernelVersion));
    }

    private MetadataCache(KernelVersion kernelVersion, LogFormat logFormat) {
        setKernelVersion(kernelVersion);
        setCurrentLogFormat(logFormat);
    }

    @Override
    public KernelVersion kernelVersion() {
        return kernelVersion;
    }

    @Override
    public void setKernelVersion(KernelVersion kernelVersion) {
        this.kernelVersion = kernelVersion;
    }

    @Override
    public void setCurrentLogFormat(LogFormat logFormat) {
        this.logFormat = logFormat;
    }

    @Override
    public LogFormat getCurrentLogFormat() {
        return logFormat;
    }
}

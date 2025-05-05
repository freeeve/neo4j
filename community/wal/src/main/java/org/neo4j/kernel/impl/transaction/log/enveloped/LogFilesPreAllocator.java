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

import java.io.IOException;
import org.neo4j.internal.nativeimpl.NativeAccess;
import org.neo4j.internal.nativeimpl.NativeAccessProvider;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.pagecache.OutOfDiskSpaceException;

public class LogFilesPreAllocator {
    private final NativeAccess nativeAccess;

    public LogFilesPreAllocator(NativeAccess nativeAccess) {
        this.nativeAccess = nativeAccess;
    }

    public LogFilesPreAllocator() {
        this(NativeAccessProvider.getNativeAccess());
    }

    void preAllocateLogFile(LogChannelContext<StoreChannel> logChannelCtx, long fileSize) throws IOException {
        if (!nativeAccess.isAvailable()) {
            return;
        }
        if (logChannelCtx.channel().size() != 0) {
            // already pre-allocated
            return;
        }
        var result = nativeAccess.tryPreallocateSpace(logChannelCtx.channel().getFileDescriptor(), fileSize);
        if (result.isError()) {
            if (nativeAccess.errorTranslator().isOutOfDiskSpace(result)) {
                throw new OutOfDiskSpaceException(
                        "System is out of disk space for log file at: " + logChannelCtx.path() + ". "
                                + "Requested file size: " + fileSize + ". Call error: "
                                + result);
            }
            throw new IOException("Fail to preallocate additional space for log file at: " + logChannelCtx.path() + ". "
                    + "Requested file size: " + fileSize + ". Call error: " + result);
        }
    }
}

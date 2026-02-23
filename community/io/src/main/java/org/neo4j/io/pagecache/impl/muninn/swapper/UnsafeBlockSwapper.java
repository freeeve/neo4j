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
package org.neo4j.io.pagecache.impl.muninn.swapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.neo4j.internal.unsafe.UnsafeUtil;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;

/**
 * BlockSwapper that uses reflection to wrap buffer address into ByteBuffer to directly read from/write to StoreChannel
 */
final class UnsafeBlockSwapper implements BlockSwapper {

    @Override
    public int swapIn(StoreChannel channel, long bufferAddress, long fileOffset, int bufferSize) throws IOException {
        int readTotal = 0;
        try {
            var buffer = buffer(bufferAddress, bufferSize);
            int read;
            do {
                read = channel.read(buffer, fileOffset + readTotal);
            } while (read != -1 && (readTotal += read) < bufferSize);

            // Zero-fill the rest.
            int rest = bufferSize - readTotal;
            if (rest > 0) {
                UnsafeUtil.setMemory(bufferAddress + readTotal, rest, MuninnPageCache.ZERO_BYTE);
            }
            return readTotal;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(formatSwapInErrorMessage(fileOffset, bufferSize, readTotal), e);
        }
    }

    @Override
    public void swapOut(StoreChannel channel, long bufferAddress, long fileOffset, int bufferSize) throws IOException {
        try {
            // direct write from memory to channel using proxy
            var buffer = buffer(bufferAddress, bufferSize);
            channel.writeAll(buffer, fileOffset);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    private static ByteBuffer buffer(long buffer, int bufferLength) throws IOException {
        try {
            return UnsafeUtil.newDirectByteBuffer(buffer, bufferLength);
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    private static String formatSwapInErrorMessage(long fileOffset, int size, int readTotal) {
        return "Read failed after " + readTotal + " of " + size + " bytes from fileOffset " + fileOffset + ".";
    }
}

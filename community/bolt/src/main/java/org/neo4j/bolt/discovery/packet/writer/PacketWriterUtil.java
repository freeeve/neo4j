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
package org.neo4j.bolt.discovery.packet.writer;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.neo4j.bolt.discovery.packet.error.InsufficientBufferSpaceException;

public final class PacketWriterUtil {

    private PacketWriterUtil() {}

    public static void ensureWritable(ByteBuf out, int expected) throws InsufficientBufferSpaceException {
        if (!out.isWritable(expected)) {
            throw new InsufficientBufferSpaceException(expected, out.writableBytes());
        }
    }

    public static void writeString(ByteBuf out, String value) throws InsufficientBufferSpaceException {
        ensureWritable(out, 1);

        var encoded = value.getBytes(StandardCharsets.UTF_8);
        ensureWritable(out, encoded.length);

        writeVarInt(out, encoded.length);
        out.writeBytes(encoded);
    }

    public static void writeVarInt(ByteBuf out, int value) {
        do {
            var c = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                c |= 0x80;
            }

            out.writeByte(c);
        } while (value != 0);
    }
}

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
package org.neo4j.bolt.discovery.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.neo4j.bolt.discovery.packet.PacketEnvelope;
import org.neo4j.bolt.discovery.packet.error.NoSuchPacketWriterException;
import org.neo4j.bolt.discovery.packet.registry.PacketRegistry;
import org.neo4j.bolt.discovery.packet.writer.PacketWriter;
import org.neo4j.bolt.discovery.packet.writer.PacketWriterUtil;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.internal.LogService;

public class PacketEncoder extends MessageToMessageEncoder<PacketEnvelope<?>> {
    private final PacketRegistry registry;
    private final InternalLog log;

    public PacketEncoder(PacketRegistry registry, LogService logging) {
        this.registry = registry;
        this.log = logging.getInternalLog(PacketEncoder.class);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void encode(ChannelHandlerContext ctx, PacketEnvelope<?> msg, List<Object> out) throws Exception {
        log.debug("<<< %s", msg);

        var content = msg.content();
        var writer = (PacketWriter) this.registry
                .findWriter(content.getClass())
                .orElseThrow(() -> new NoSuchPacketWriterException(
                        "No such packet: " + content.getClass().getCanonicalName()));

        var buf = ctx.alloc().buffer().writeInt(msg.magicNumber());

        PacketWriterUtil.writeVarInt(buf, msg.version());

        buf.writeByte(msg.opcode());

        writer.writeTo(buf, content);

        out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
    }
}

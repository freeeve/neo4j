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
package org.neo4j.bolt.discovery.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramChannel;
import org.neo4j.bolt.discovery.config.DiscoveryConfiguration;
import org.neo4j.bolt.discovery.handler.codec.PacketEncoder;
import org.neo4j.bolt.discovery.handler.packet.DiscardHandler;
import org.neo4j.bolt.discovery.handler.packet.RecurringBroadcastHandler;
import org.neo4j.bolt.discovery.packet.registry.PacketRegistry;
import org.neo4j.logging.internal.LogService;

public class ServerDiscoveryChannelInitializer extends ChannelInitializer<DatagramChannel> {

    private final DiscoveryConfiguration configuration;
    private final PacketRegistry packetRegistry;
    private final LogService logging;

    public ServerDiscoveryChannelInitializer(
            DiscoveryConfiguration configuration, PacketRegistry packetRegistry, LogService logging) {
        this.configuration = configuration;
        this.packetRegistry = packetRegistry;
        this.logging = logging;
    }

    @Override
    protected void initChannel(DatagramChannel ch) throws Exception {
        ch.pipeline()
                .addLast(new DiscardHandler())
                .addLast(new PacketEncoder(this.packetRegistry, this.logging))
                .addLast(new RecurringBroadcastHandler(this.configuration, this.logging))
                .addLast(new DiscoveryExceptionHandler(this.logging));
    }
}

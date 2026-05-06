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
package org.neo4j.bolt.discovery;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import java.net.InetAddress;
import java.util.stream.Collectors;
import org.neo4j.bolt.discovery.config.DiscoveryConfiguration;
import org.neo4j.bolt.discovery.handler.ServerDiscoveryChannelInitializer;
import org.neo4j.bolt.discovery.packet.beacon.DiscoveryBeaconSignal;
import org.neo4j.bolt.discovery.packet.registry.PacketRegistry;
import org.neo4j.bolt.discovery.packet.writer.DiscoveryBeaconSignalWriter;
import org.neo4j.bolt.protocol.common.connector.transport.ConnectorTransport;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;

public class DiscoveryConnector implements Lifecycle {

    private final EventLoopGroup eventLoopGroup;
    private final ConnectorTransport transport;
    private final DiscoveryConfiguration configuration;

    private final LogService logging;
    private final Log userLog;

    private DatagramChannel channel;

    public DiscoveryConnector(
            EventLoopGroup eventLoopGroup,
            ConnectorTransport transport,
            DiscoveryConfiguration configuration,
            LogService logging) {
        this.eventLoopGroup = eventLoopGroup;
        this.transport = transport;
        this.configuration = configuration;

        this.logging = logging;
        this.userLog = logging.getUserLog(DiscoveryConnector.class);
    }

    @Override
    public void init() throws Exception {}

    @Override
    public void start() throws Exception {
        var packetRegistry = PacketRegistry.factory()
                .withWriter(DiscoveryBeaconSignal.class, new DiscoveryBeaconSignalWriter())
                .build();

        var future = (new Bootstrap()
                .group(this.eventLoopGroup)
                .channelFactory(() -> this.transport.createDatagramChannel(SocketProtocolFamily.INET))
                .handler(new ServerDiscoveryChannelInitializer(this.configuration, packetRegistry, this.logging))
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_BROADCAST, true)
                // Note: We cannot pass address here without breaking broadcast
                .bind(this.configuration.port())
                .sync());

        this.channel = (DatagramChannel) future.channel();

        var addresses = this.configuration.addresses();
        var broadcastList = addresses.stream().map(InetAddress::getHostAddress).collect(Collectors.joining(", "));
        var message = "Fleet discovery broadcasts have been enabled for %d network" + (addresses.size() == 1 ? "" : "s")
                + ": %s";

        this.userLog.info(message, addresses.size(), broadcastList);
    }

    @Override
    public void stop() throws Exception {
        var channel = this.channel;
        this.channel = null;
        if (channel == null) {
            return;
        }

        channel.close().awaitUninterruptibly();
    }

    @Override
    public void shutdown() throws Exception {}
}

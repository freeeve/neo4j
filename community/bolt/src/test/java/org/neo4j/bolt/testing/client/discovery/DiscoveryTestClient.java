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
package org.neo4j.bolt.testing.client.discovery;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.SocketProtocolFamily;
import java.time.Duration;
import org.neo4j.bolt.protocol.common.connector.transport.ConnectorTransport;

public class DiscoveryTestClient {

    private final ConnectorTransport transport;

    public DiscoveryTestClient(ConnectorTransport transport) {
        this.transport = transport;
    }

    public void listenForSignal(Duration duration, BeaconListener listener) throws InterruptedException {
        var handlerFactory = this.transport.createIoHandlerFactory();
        var eventLoopGroup = new MultiThreadIoEventLoopGroup(1, handlerFactory);

        try {
            var ch = new Bootstrap()
                    .group(eventLoopGroup)
                    // beacons are only supported on IPv4 at the moment - explicitly limit netty to v4
                    .channelFactory(() -> this.transport.createDatagramChannel(SocketProtocolFamily.INET))
                    .handler(new NotifyingHandler(duration, listener))
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .bind(8687)
                    .sync()
                    .channel();

            ch.closeFuture().await();
        } finally {
            eventLoopGroup.shutdownGracefully().await();
        }
    }
}

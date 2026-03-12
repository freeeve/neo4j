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
package org.neo4j.bolt.protocol.common.connector.transport;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.ServerDomainSocketChannel;
import org.neo4j.annotations.service.ServiceProvider;

/**
 * Provides a transport implementation based on the `KQueue`.
 */
@ServiceProvider
public final class KqueueConnectorTransport implements ConnectorTransport {

    @Override
    public String getName() {
        return "KQueue";
    }

    @Override
    public boolean isAvailable() {
        return KQueue.isAvailable();
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public boolean supportsOption(ConnectorOption<?> option) {
        return option == ConnectorOption.TCP_FAST_OPEN || option == ConnectorOption.TCP_FAST_OPEN_CONNECT;
    }

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return KQueueIoHandler.newFactory();
    }

    @Override
    public Class<? extends SocketChannel> socketChannelType() {
        return KQueueSocketChannel.class;
    }

    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelType() {
        return KQueueServerSocketChannel.class;
    }

    @Override
    public Class<? extends DomainSocketChannel> domainSocketChannelType() {
        return KQueueDomainSocketChannel.class;
    }

    @Override
    public Class<? extends ServerDomainSocketChannel> serverDomainSocketChannelType() {
        return KQueueServerDomainSocketChannel.class;
    }

    @Override
    public DatagramChannel createDatagramChannel(SocketProtocolFamily protocolFamily) {
        return new KQueueDatagramChannel(protocolFamily);
    }
}

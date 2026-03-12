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
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketChannel;
import org.neo4j.annotations.service.ServiceProvider;

/**
 * Provides a transport implementation based on the Linux `epoll` function.
 */
@ServiceProvider
public final class EpollConnectorTransport implements ConnectorTransport {

    @Override
    public String getName() {
        return "epoll";
    }

    @Override
    public boolean isAvailable() {
        return Epoll.isAvailable();
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
        return EpollIoHandler.newFactory();
    }

    @Override
    public Class<? extends SocketChannel> socketChannelType() {
        return EpollSocketChannel.class;
    }

    @Override
    public Class<EpollServerSocketChannel> serverSocketChannelType() {
        return EpollServerSocketChannel.class;
    }

    @Override
    public Class<? extends DomainSocketChannel> domainSocketChannelType() {
        return EpollDomainSocketChannel.class;
    }

    @Override
    public Class<EpollServerDomainSocketChannel> serverDomainSocketChannelType() {
        return EpollServerDomainSocketChannel.class;
    }

    @Override
    public DatagramChannel createDatagramChannel(SocketProtocolFamily protocolFamily) {
        return new EpollDatagramChannel(protocolFamily);
    }
}

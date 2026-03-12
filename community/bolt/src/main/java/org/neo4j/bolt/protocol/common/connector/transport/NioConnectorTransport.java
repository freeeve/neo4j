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
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.neo4j.annotations.service.ServiceProvider;

/**
 * Provides a connector transport implementation based on the JDK NIO (New IO) APIs.
 * <p />
 * This implementation should typically be used as a fallback in order to facilitate support for operating systems which
 * lack support for faster network APIs as it is available within all compliant JDK implementations.
 */
@ServiceProvider
public final class NioConnectorTransport implements ConnectorTransport {

    @Override
    public String getName() {
        return "NIO";
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return NioIoHandler.newFactory();
    }

    @Override
    public Class<? extends SocketChannel> socketChannelType() {
        return NioSocketChannel.class;
    }

    @Override
    public Class<NioServerSocketChannel> serverSocketChannelType() {
        return NioServerSocketChannel.class;
    }

    @Override
    public DatagramChannel createDatagramChannel(SocketProtocolFamily protocolFamily) {
        return new NioDatagramChannel(protocolFamily);
    }

    @Override
    public boolean isNative() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}

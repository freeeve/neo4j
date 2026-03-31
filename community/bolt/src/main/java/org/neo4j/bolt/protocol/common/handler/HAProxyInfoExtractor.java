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
package org.neo4j.bolt.protocol.common.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.memory.HeapEstimator;

/**
 * Extracts PROXY protocol information from decoded {@link HAProxyMessage} and stores it
 * in the {@link Connection} object for use by authentication, logging, and monitoring.
 * <p />
 * This handler should be placed immediately after {@link io.netty.handler.codec.haproxy.HAProxyMessageDecoder}
 * in the pipeline. It will:
 * <ul>
 *   <li>Extract real client and server addresses from the PROXY protocol header</li>
 *   <li>Store them in the Connection object via {@link Connection#setProxyProtocolInfo}</li>
 *   <li>Remove itself from the pipeline (self-removing)</li>
 * </ul>
 * <p />
 * Non-{@link HAProxyMessage} messages are passed through to the next handler.
 * <p />
 * The handler correctly handles both PROXY and LOCAL commands:
 * <ul>
 *   <li>PROXY command: Real connection, addresses are extracted and stored</li>
 *   <li>LOCAL command: Health check from proxy, no addresses stored (uses actual socket addresses)</li>
 * </ul>
 */
public class HAProxyInfoExtractor extends SimpleChannelInboundHandler<HAProxyMessage> {
    public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(HAProxyInfoExtractor.class);

    private final InternalLog log;

    public HAProxyInfoExtractor(InternalLogProvider logging) {
        this.log = logging.getLog(HAProxyInfoExtractor.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HAProxyMessage proxyMsg) {

        Connection connection = Connection.getConnection(ctx.channel());

        // Only process PROXY command (real connections), not LOCAL (health checks)
        if (proxyMsg.command() == HAProxyCommand.PROXY) {
            SocketAddress realClientAddress = new InetSocketAddress(proxyMsg.sourceAddress(), proxyMsg.sourcePort());

            connection.setProxyProtocolInfo(realClientAddress);

            log.debug(
                    "[%s] PROXY protocol v%d detected: real client=%s (proxy=%s) (local=%s)",
                    connection.id(),
                    proxyMsg.protocolVersion() == HAProxyProtocolVersion.V1 ? 1 : 2,
                    realClientAddress,
                    ctx.channel().remoteAddress(),
                    ctx.channel().localAddress());
        } else {
            // LOCAL command = health check from proxy, don't override addresses
            log.debug("[%s] PROXY protocol LOCAL command (health check), using connection addresses", connection.id());
        }

        // Mark PROXY protocol processing as complete so TransportSelectionHandler can continue
        ctx.channel().attr(TransportSelectionHandler.PROXY_PROTOCOL_PROCESSING).set(Boolean.FALSE);

        // Remove decoder and extractor from pipeline - PROXY processing is complete
        // The decoder should be removed first, then this extractor
        var pipeline = ctx.pipeline();
        pipeline.remove(this);
    }
}

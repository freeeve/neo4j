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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.neo4j.bolt.discovery.packet.DiscoveryConstants;

final class NotifyingHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Duration listenDuration;
    private final BeaconListener listener;

    private ScheduledFuture<?> shutdownFuture;

    public NotifyingHandler(Duration duration, BeaconListener listener) {
        this.listenDuration = duration;
        this.listener = listener;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);

        this.shutdownFuture = ctx.executor()
                .schedule(
                        () -> {
                            try {
                                this.listener.onComplete();
                            } finally {
                                ctx.channel().close();
                            }
                        },
                        this.listenDuration.toMillis(),
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        var future = this.shutdownFuture;
        if (future != null) {
            future.cancel(false);
        }

        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        var buf = msg.content();

        // ensure that only packets with the correct magic number are passed as there may be other stuff
        // going on in the test environment thus potentially breaking our tests
        buf.markReaderIndex();
        try {
            var magicNumber = buf.readInt();
            if (magicNumber != DiscoveryConstants.MAGIC_NUMBER) {
                return;
            }
        } finally {
            buf.resetReaderIndex();
        }

        this.listener.onBeaconSignal(msg.sender(), msg.content());
    }
}

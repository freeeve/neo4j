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
package org.neo4j.bolt.discovery.handler.packet;

import static org.neo4j.bolt.discovery.packet.DiscoveryConstants.BROADCAST_PORT;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.neo4j.bolt.discovery.config.DiscoveryConfiguration;
import org.neo4j.bolt.discovery.info.DiscoveryInformationProvider;
import org.neo4j.bolt.discovery.info.DiscoveryInformationProvider.DiscoveryInformation;
import org.neo4j.bolt.discovery.packet.DiscoveryConstants;
import org.neo4j.bolt.discovery.packet.PacketEnvelope;
import org.neo4j.bolt.discovery.packet.beacon.DiscoveryBeaconSignal;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.internal.LogService;

public class RecurringBroadcastHandler extends ChannelHandlerAdapter {

    private final DiscoveryConfiguration configuration;
    private final DiscoveryInformationProvider infoProvider;
    private final List<InetAddress> addresses;

    private final InternalLog log;

    private final Random rng;
    private ScheduledFuture<?> future;

    public RecurringBroadcastHandler(DiscoveryConfiguration configuration, LogService logging) {
        this.configuration = configuration;
        this.infoProvider = configuration.infoProvider();
        this.addresses = configuration.addresses();

        this.log = logging.getInternalLog(RecurringBroadcastHandler.class);

        this.rng = new Random();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.scheduleBroadcast(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        var f = this.future;
        if (f == null) {
            return;
        }

        f.cancel(false);
    }

    private void scheduleBroadcast(ChannelHandlerContext ctx) {
        this.future = ctx.executor()
                .schedule(
                        () -> this.onBroadcast(ctx),
                        this.configuration.effectiveBroadcastInterval(this.rng),
                        TimeUnit.MILLISECONDS);
    }

    private void onBroadcast(ChannelHandlerContext ctx) {
        try {
            var info = this.infoProvider.getCurrentInfo();
            log.debug("Emitting fleet discovery beacon: %s", info);

            this.addresses.forEach(addr -> this.doBroadcast(ctx, info, addr));
        } finally {
            this.scheduleBroadcast(ctx);
        }
    }

    private void doBroadcast(ChannelHandlerContext ctx, DiscoveryInformation info, InetAddress address) {
        ctx.writeAndFlush(new PacketEnvelope<>(
                new InetSocketAddress(address, BROADCAST_PORT),
                DiscoveryConstants.MAGIC_NUMBER,
                DiscoveryConstants.LATEST_VERSION,
                DiscoveryBeaconSignal.OPCODE,
                new DiscoveryBeaconSignal(
                        info.dbmsId(),
                        info.nodeId(),
                        info.productVersion(),
                        info.edition().toString(),
                        info.advertisementAddress())));
    }
}

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
package org.neo4j.server.queryapi.driver;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalIoHandler;
import java.net.URI;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.neo4j.bolt.connection.BoltAgent;
import org.neo4j.bolt.connection.BoltConnectionProvider;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.LoggingProvider;
import org.neo4j.bolt.connection.MetricsListener;
import org.neo4j.bolt.connection.RoutingContext;
import org.neo4j.bolt.connection.netty.NettyBoltConnectionProvider;
import org.neo4j.bolt.connection.pooled.PooledBoltConnectionProvider;
import org.neo4j.bolt.connection.routed.Rediscovery;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.internal.BoltLoggingProvider;
import org.neo4j.driver.internal.DriverFactory;
import org.neo4j.driver.internal.RoutingSettings;
import org.neo4j.driver.internal.boltlistener.BoltConnectionListener;
import org.neo4j.driver.internal.security.StaticAuthTokenManager;
import org.neo4j.driver.internal.value.BoltValueFactory;
import org.neo4j.logging.InternalLogProvider;

/**
 * A custom {@link DriverFactory} that uses netty's {@link io.netty.channel.local.LocalChannel} to connect to the
 * bolt server.
 */
public final class LocalChannelDriverFactory extends DriverFactory implements AutoCloseable {

    public static final URI IGNORED_HTTP_DRIVER_URI = URI.create("bolt://http-driver.com:0");
    private final LocalAddress localAddress;
    private final InternalLogProvider internalLogProvider;
    private final org.neo4j.configuration.Config config;
    private final MultiThreadIoEventLoopGroup localGroup;

    public LocalChannelDriverFactory(
            LocalAddress localAddress, InternalLogProvider internalLogProvider, org.neo4j.configuration.Config config) {
        this.localAddress = localAddress;
        this.internalLogProvider = internalLogProvider;
        this.config = config;
        this.localGroup = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
    }

    @Override
    protected LocalAddress localAddress() {
        return localAddress;
    }

    public Driver createLocalDriver() {
        return super.newInstance(
                IGNORED_HTTP_DRIVER_URI,
                new StaticAuthTokenManager(AuthTokens.none()),
                null,
                Config.builder()
                        .withLogging(new DriverToInternalLogProvider(internalLogProvider))
                        .withUserAgent("neo4j-query-api/v2")
                        .build(),
                null,
                localGroup,
                null);
    }

    @Override
    protected BoltConnectionProvider createBoltConnectionProvider(
            URI uri,
            Config config,
            EventLoopGroup eventLoopGroup,
            RoutingSettings routingSettings,
            Supplier<Rediscovery> rediscoverySupplier,
            BoltConnectionListener boltConnectionListener,
            BoltServerAddress address,
            RoutingContext routingContext,
            BoltAgent boltAgent,
            String userAgent,
            int connectTimeoutMillis,
            MetricsListener metricsListener,
            Clock clock) {
        var loggingProvider = new BoltLoggingProvider(config.logging());
        Function<BoltServerAddress, BoltConnectionProvider> pooledBoltConnectionProviderSupplier =
                selectedAddress -> createPooledBoltConnectionProvider(
                        config,
                        eventLoopGroup,
                        clock,
                        loggingProvider,
                        boltConnectionListener,
                        selectedAddress,
                        boltAgent,
                        userAgent,
                        connectTimeoutMillis,
                        metricsListener);

        return pooledBoltConnectionProviderSupplier.apply(address);
    }

    private BoltConnectionProvider createPooledBoltConnectionProvider(
            Config config,
            EventLoopGroup eventLoopGroup,
            Clock clock,
            LoggingProvider loggingProvider,
            BoltConnectionListener boltConnectionListener,
            BoltServerAddress address,
            BoltAgent boltAgent,
            String userAgent,
            int connectTimeoutMillis,
            MetricsListener metricsListener) {
        var nettyBoltConnectionProvider = createNettyBoltConnectionProvider(eventLoopGroup, clock, loggingProvider);
        nettyBoltConnectionProvider = BoltConnectionListener.listeningBoltConnectionProvider(
                nettyBoltConnectionProvider, boltConnectionListener);
        return new PooledBoltConnectionProvider(
                nettyBoltConnectionProvider,
                config.maxConnectionPoolSize(),
                config.connectionAcquisitionTimeoutMillis(),
                config.maxConnectionLifetimeMillis(),
                config.idleTimeBeforeConnectionTest(),
                clock,
                loggingProvider,
                metricsListener,
                address,
                RoutingContext.EMPTY,
                boltAgent,
                userAgent,
                connectTimeoutMillis);
    }

    private BoltConnectionProvider createNettyBoltConnectionProvider(
            EventLoopGroup eventLoopGroup, Clock clock, LoggingProvider loggingProvider) {
        return new NettyBoltConnectionProvider(
                eventLoopGroup,
                clock,
                getDomainNameResolver(),
                localAddress(),
                loggingProvider,
                BoltValueFactory.getInstance(),
                null);
    }

    @Override
    public void close() throws Exception {
        var workerTerminationFuture = localGroup.shutdownGracefully(
                config.get(GraphDatabaseInternalSettings.netty_server_shutdown_quiet_period),
                config.get(GraphDatabaseInternalSettings.netty_server_shutdown_timeout)
                        .toSeconds(),
                TimeUnit.SECONDS);

        var workerTerminationCompleted = workerTerminationFuture.awaitUninterruptibly(
                config.get(BoltConnectorInternalSettings.thread_pool_shutdown_wait_time)
                        .toSeconds(),
                TimeUnit.SECONDS);
        if (!workerTerminationCompleted) {
            var log = internalLogProvider.getLog(LocalChannelDriverFactory.class);
            log.warn(
                    "Termination of local driver factory worker event loop group has exceeded maximum permitted duration - Remaining jobs will be forcefully terminated");
        } else if (!workerTerminationFuture.isSuccess()) {
            var log = internalLogProvider.getLog(LocalChannelDriverFactory.class);
            log.warn(
                    "Termination of local driver factory worker event loop group has failed",
                    workerTerminationFuture.cause());
        }
    }
}

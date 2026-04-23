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
package org.neo4j.bolt.protocol.common.connector.connection.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.neo4j.bolt.protocol.common.BoltProtocol;
import org.neo4j.bolt.protocol.common.connector.Connector;
import org.neo4j.bolt.protocol.common.connector.config.ConnectorConfiguration;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.common.handler.AuthenticationProtocolLimiterHandler;
import org.neo4j.bolt.protocol.common.handler.AuthenticationTimeoutHandler;
import org.neo4j.bolt.protocol.common.handler.HouseKeeperHandler;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.packstream.codec.transport.ChunkFrameDecoder;

class AuthenticationTimeoutConnectionListenerTest {

    private static final String CONNECTION_ID = "bolt-authtimeout";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final List<String> CHANNEL_HANDLER_NAMES = List.of(ChunkFrameDecoder.NAME);

    private ConnectorConfiguration configuration;
    private Connector<?> connector;
    private Connection connection;
    private MemoryTracker memoryTracker;
    private Channel channel;
    private EventLoop eventLoop;
    private ChannelPipeline pipeline;
    private AssertableLogProvider logProvider;

    private AuthenticationTimeoutConnectionListener listener;

    @BeforeEach
    void prepareListener() {
        this.configuration = Mockito.mock(ConnectorConfiguration.class, Mockito.RETURNS_DEFAULTS);
        this.connector = Mockito.mock(Connector.class);
        this.connection = Mockito.mock(Connection.class, Mockito.RETURNS_MOCKS);
        this.memoryTracker = Mockito.mock(MemoryTracker.class);
        this.channel = Mockito.mock(Channel.class);
        this.eventLoop = Mockito.mock(EventLoop.class);
        this.pipeline = Mockito.mock(ChannelPipeline.class, Mockito.RETURNS_SELF);
        this.logProvider = new AssertableLogProvider();

        Mockito.doReturn(CONNECTION_ID).when(this.connection).id();
        Mockito.doReturn(this.configuration).when(this.connector).configuration();
        Mockito.doReturn(this.connector).when(this.connection).connector();
        Mockito.doReturn(this.memoryTracker).when(this.connection).memoryTracker();
        Mockito.doReturn(this.eventLoop).when(this.channel).eventLoop();
        Mockito.doReturn(this.pipeline).when(this.channel).pipeline();
        Mockito.doReturn(CHANNEL_HANDLER_NAMES).when(this.pipeline).names();

        Mockito.doAnswer(invocationOnMock -> {
                    var consumer = invocationOnMock.<Consumer<ChannelPipeline>>getArgument(0);
                    consumer.accept(this.pipeline);
                    return null;
                })
                .when(this.connection)
                .modifyPipeline(Mockito.<Consumer<ChannelPipeline>>any());

        Mockito.doAnswer(invocationOnMock -> {
                    var runnable = invocationOnMock.<Runnable>getArgument(0);
                    runnable.run();
                    return null;
                })
                .when(this.eventLoop)
                .execute(Mockito.any(Runnable.class));

        this.listener = new AuthenticationTimeoutConnectionListener(connection, TIMEOUT, this.logProvider);
    }

    @Test
    void shouldInstallAuthenticationTimeoutHandlerOnPipelineInitialization() {
        this.listener.onNetworkPipelineInitialized(this.pipeline);

        var inOrder = Mockito.inOrder(this.memoryTracker, this.pipeline);

        inOrder.verify(this.memoryTracker).allocateHeap(AuthenticationTimeoutHandler.SHALLOW_SIZE);
        inOrder.verify(this.pipeline).addLast(any(AuthenticationTimeoutHandler.class));
        inOrder.verifyNoMoreInteractions();

        LogAssertions.assertThat(this.logProvider)
                .forLevel(AssertableLogProvider.Level.DEBUG)
                .forClass(AuthenticationTimeoutConnectionListener.class)
                .containsMessageWithArgumentsContaining("Installing authentication timeout handler", CONNECTION_ID);
    }

    @Test
    void shouldNotInstallLimiterOnProtocolSelection() {
        this.listener.onProtocolSelected(Mockito.mock(BoltProtocol.class));

        var inOrder = Mockito.inOrder(this.memoryTracker, this.pipeline);
        inOrder.verify(this.memoryTracker, Mockito.never())
                .allocateHeap(AuthenticationProtocolLimiterHandler.SHALLOW_SIZE);
        inOrder.verify(this.pipeline, Mockito.never())
                .addAfter(
                        eq(ChunkFrameDecoder.NAME), any(String.class), any(AuthenticationProtocolLimiterHandler.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldRemoveAuthenticationTimeoutHandlerOnAuthentication() {
        var loginContext = Mockito.mock(LoginContext.class);

        this.listener.onNetworkPipelineInitialized(this.pipeline);
        this.listener.onProtocolSelected(Mockito.mock(BoltProtocol.class));

        Mockito.verify(this.memoryTracker, Mockito.times(1)).allocateHeap(AuthenticationTimeoutHandler.SHALLOW_SIZE);
        Mockito.verify(this.pipeline).addLast(any(AuthenticationTimeoutHandler.class));
        Mockito.verifyNoMoreInteractions(this.memoryTracker, this.pipeline);

        this.listener.onLogon(loginContext);

        var inOrder = Mockito.inOrder(loginContext, this.connection, this.channel, this.pipeline, this.eventLoop);

        inOrder.verify(this.pipeline).remove(any(AuthenticationTimeoutHandler.class));
        inOrder.verifyNoMoreInteractions();

        LogAssertions.assertThat(this.logProvider)
                .forLevel(AssertableLogProvider.Level.DEBUG)
                .forClass(AuthenticationTimeoutConnectionListener.class)
                .containsMessageWithArgumentsContaining("Removing authentication timeout handler", CONNECTION_ID);
    }

    @Test
    void shouldReAddAuthenticationTimeoutHandlerBeforeHousekeeperOnAuthentication() {
        listener.onNetworkPipelineInitialized(pipeline);
        listener.onLogoff();

        InOrder inOrder = Mockito.inOrder(connection, channel, pipeline);

        inOrder.verify(pipeline)
                .addBefore(
                        eq(HouseKeeperHandler.HANDLER_NAME),
                        any(String.class),
                        any(AuthenticationTimeoutHandler.class));
        inOrder.verifyNoMoreInteractions();

        LogAssertions.assertThat(logProvider)
                .forLevel(AssertableLogProvider.Level.DEBUG)
                .forClass(AuthenticationTimeoutConnectionListener.class)
                .containsMessageWithArgumentsContaining("Re-adding authentication timeout handler", CONNECTION_ID);
    }

    @Test
    void shouldNotInstallLimiterOnLogoff() {
        listener.onNetworkPipelineInitialized(pipeline);
        listener.onLogoff();

        InOrder inOrder = Mockito.inOrder(connection, channel, pipeline);

        inOrder.verify(pipeline)
                .addBefore(
                        eq(HouseKeeperHandler.HANDLER_NAME),
                        any(String.class),
                        any(AuthenticationTimeoutHandler.class));
        inOrder.verify(pipeline, Mockito.never())
                .addAfter(
                        eq(ChunkFrameDecoder.NAME), any(String.class), any(AuthenticationProtocolLimiterHandler.class));
        inOrder.verifyNoMoreInteractions();

        LogAssertions.assertThat(logProvider)
                .forLevel(AssertableLogProvider.Level.DEBUG)
                .forClass(AuthenticationTimeoutConnectionListener.class)
                .containsMessageWithArgumentsContaining("Re-adding authentication timeout handler", CONNECTION_ID);
    }

    @Test
    void shouldReleaseMemoryOnRemoval() {
        this.listener.onListenerRemoved();

        Mockito.verify(this.memoryTracker).releaseHeap(AuthenticationTimeoutConnectionListener.SHALLOW_SIZE);
    }
}

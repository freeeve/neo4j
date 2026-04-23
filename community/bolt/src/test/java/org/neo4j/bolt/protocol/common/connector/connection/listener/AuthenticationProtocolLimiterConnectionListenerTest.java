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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.bolt.protocol.common.BoltProtocol;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.common.handler.AuthenticationProtocolLimiterHandler;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.packstream.codec.transport.ChunkFrameDecoder;

class AuthenticationProtocolLimiterConnectionListenerTest {
    private static final String CONNECTION_ID = "bolt-authlimiter";
    private static final int STRUCTURE_ELEMENT_LIMIT = 64;
    private static final int STRUCTURE_DEPTH_LIMIT = 4;

    private MemoryTracker memoryTracker;
    private Connection connection;
    private Channel channel;
    private ChannelPipeline pipeline;
    private AssertableLogProvider logProvider;

    private AuthenticationProtocolLimiterConnectionListener listener;

    @BeforeEach
    void prepareListener() {
        this.connection = Mockito.mock(Connection.class, Mockito.RETURNS_MOCKS);
        this.memoryTracker = Mockito.mock(MemoryTracker.class);
        this.channel = Mockito.mock(Channel.class);
        this.pipeline = Mockito.mock(ChannelPipeline.class, Mockito.RETURNS_SELF);
        this.logProvider = new AssertableLogProvider();

        Mockito.doReturn(CONNECTION_ID).when(this.connection).id();
        Mockito.doReturn(this.memoryTracker).when(this.connection).memoryTracker();
        Mockito.doReturn(this.pipeline).when(this.channel).pipeline();

        Mockito.doAnswer(invocationOnMock -> {
                    var consumer = invocationOnMock.<Consumer<ChannelPipeline>>getArgument(0);
                    consumer.accept(this.pipeline);
                    return null;
                })
                .when(this.connection)
                .modifyPipeline(Mockito.<Consumer<ChannelPipeline>>any());

        this.listener = new AuthenticationProtocolLimiterConnectionListener(
                connection, STRUCTURE_ELEMENT_LIMIT, STRUCTURE_DEPTH_LIMIT, this.logProvider);
    }

    @Test
    void shouldInstallLimiterOnProtocolSelection() {
        this.listener.onProtocolSelected(Mockito.mock(BoltProtocol.class));

        assertAuthenticationProtocolLimiterHandlerInstallation();
    }

    @Test
    void shouldInstallLimiterOnLogoff() {
        this.listener.onLogoff();

        assertAuthenticationProtocolLimiterHandlerInstallation();
    }

    @Test
    void shouldRemoveLimiterOnLogon() {
        var loginContext = Mockito.mock(LoginContext.class);

        // SETUP
        this.listener.onNetworkPipelineInitialized(this.pipeline);
        this.listener.onProtocolSelected(Mockito.mock(BoltProtocol.class));
        assertAuthenticationProtocolLimiterHandlerInstallation();

        // ACT
        this.listener.onLogon(loginContext);

        // VERIFY
        var inOrder = Mockito.inOrder(loginContext, this.connection, this.channel, this.pipeline);

        inOrder.verify(this.pipeline).remove(any(AuthenticationProtocolLimiterHandler.class));
        inOrder.verifyNoMoreInteractions();

        LogAssertions.assertThat(this.logProvider)
                .forLevel(AssertableLogProvider.Level.DEBUG)
                .forClass(AuthenticationProtocolLimiterConnectionListener.class)
                .containsMessageWithArgumentsContaining(
                        "[%s] Removing authentication protocol limiter handler", CONNECTION_ID);
    }

    @Test
    void shouldReleaseMemoryOnRemoval() {
        this.listener.onListenerRemoved();

        Mockito.verify(this.memoryTracker).releaseHeap(AuthenticationProtocolLimiterConnectionListener.SHALLOW_SIZE);
    }

    private void assertAuthenticationProtocolLimiterHandlerInstallation() {
        var inOrder = Mockito.inOrder(this.memoryTracker, this.pipeline);
        inOrder.verify(this.memoryTracker).allocateHeap(AuthenticationProtocolLimiterHandler.SHALLOW_SIZE);
        inOrder.verify(this.pipeline)
                .addAfter(
                        eq(ChunkFrameDecoder.NAME), any(String.class), any(AuthenticationProtocolLimiterHandler.class));
        inOrder.verifyNoMoreInteractions();

        LogAssertions.assertThat(this.logProvider)
                .forLevel(AssertableLogProvider.Level.DEBUG)
                .forClass(AuthenticationProtocolLimiterConnectionListener.class)
                .containsMessageWithArgumentsContaining(
                        "[%s] Imposing authentication structure limits of %d elements with a maximum depth of %d",
                        CONNECTION_ID, STRUCTURE_ELEMENT_LIMIT, STRUCTURE_DEPTH_LIMIT);
    }
}

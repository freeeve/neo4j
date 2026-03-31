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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.neo4j.logging.AssertableLogProvider.Level.DEBUG;
import static org.neo4j.logging.LogAssertions.assertThat;

import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.bolt.testing.annotation.StrictBufferExtension;
import org.neo4j.bolt.testing.channel.StrictBufferContext;
import org.neo4j.bolt.testing.mock.ConnectionMockFactory;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.NullLogProvider;

@StrictBufferExtension
class HAProxyInfoExtractorTest {

    @Test
    void shouldExtractAddressesFromProxyV1Command(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var proxyMsg = new HAProxyMessage(
                HAProxyProtocolVersion.V1,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP4,
                "192.168.1.100", // source (client)
                "192.168.1.1", // destination (server)
                45678, // source port
                7687 // destination port
                );
        ctx.tracked(proxyMsg);

        var channel = ctx.channel();
        var connection = ConnectionMockFactory.newFactory("test-connection")
                .attachTo(channel, new HAProxyInfoExtractor(logging));

        // When
        channel.writeInbound(proxyMsg);

        // Then - verify setProxyProtocolInfo was called with correct addresses
        var clientCaptor = ArgumentCaptor.forClass(SocketAddress.class);
        verify(connection).setProxyProtocolInfo(clientCaptor.capture());

        Assertions.assertThat(clientCaptor.getValue()).isInstanceOf(InetSocketAddress.class);
        var clientAddress = (InetSocketAddress) clientCaptor.getValue();

        Assertions.assertThat(clientAddress.getAddress().getHostAddress()).isEqualTo("192.168.1.100");
        Assertions.assertThat(clientAddress.getPort()).isEqualTo(45678);

        // Handler should remove itself
        Assertions.assertThat(channel.pipeline().get(HAProxyInfoExtractor.class))
                .isNull();

        assertThat(logging)
                .forClass(HAProxyInfoExtractor.class)
                .forLevel(DEBUG)
                .containsMessages("PROXY protocol v1 detected");
    }

    @Test
    void shouldExtractAddressesFromProxyV2Command(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var proxyMsg = new HAProxyMessage(
                HAProxyProtocolVersion.V2,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP4,
                "10.0.0.50", // source (client)
                "10.0.0.1", // destination (server)
                54321, // source port
                7687 // destination port
                );
        ctx.tracked(proxyMsg);

        var channel = ctx.channel();
        var connection = ConnectionMockFactory.newFactory("test-connection")
                .attachTo(channel, new HAProxyInfoExtractor(logging));

        // When
        channel.writeInbound(proxyMsg);

        // Then
        var clientCaptor = ArgumentCaptor.forClass(SocketAddress.class);
        verify(connection).setProxyProtocolInfo(clientCaptor.capture());
        Assertions.assertThat(clientCaptor.getValue()).isInstanceOf(InetSocketAddress.class);
        var clientAddress = (InetSocketAddress) clientCaptor.getValue();

        Assertions.assertThat(clientAddress.getAddress().getHostAddress()).isEqualTo("10.0.0.50");
        Assertions.assertThat(clientAddress.getPort()).isEqualTo(54321);

        assertThat(logging)
                .forClass(HAProxyInfoExtractor.class)
                .forLevel(DEBUG)
                .containsMessages("PROXY protocol v2 detected");
    }

    @Test
    void shouldExtractIPv6Addresses(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var proxyMsg = new HAProxyMessage(
                HAProxyProtocolVersion.V2,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP6,
                "2001:db8::1", // source (client) IPv6
                "2001:db8::2", // destination (server) IPv6
                45678,
                7687);
        ctx.tracked(proxyMsg);

        var channel = ctx.channel();
        var connection = ConnectionMockFactory.newFactory("test-connection")
                .attachTo(channel, new HAProxyInfoExtractor(logging));

        // When
        channel.writeInbound(proxyMsg);

        // Then
        var clientCaptor = ArgumentCaptor.forClass(SocketAddress.class);
        verify(connection).setProxyProtocolInfo(clientCaptor.capture());
        Assertions.assertThat(clientCaptor.getValue()).isInstanceOf(InetSocketAddress.class);
        var clientAddress = (InetSocketAddress) clientCaptor.getValue();

        Assertions.assertThat(clientAddress.getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void shouldNotSetAddressesForLocalCommand(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        // LOCAL command = health check from proxy
        var proxyMsg = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.LOCAL, HAProxyProxiedProtocol.UNKNOWN, null, null, 0, 0);
        ctx.tracked(proxyMsg);

        var channel = ctx.channel();
        var connection = ConnectionMockFactory.newFactory("test-connection")
                .attachTo(channel, new HAProxyInfoExtractor(logging));

        // When
        channel.writeInbound(proxyMsg);

        // Then - setProxyProtocolInfo should NOT be called
        verify(connection, never()).setProxyProtocolInfo(any());

        // Handler should still remove itself
        Assertions.assertThat(channel.pipeline().get(HAProxyInfoExtractor.class))
                .isNull();

        assertThat(logging)
                .forClass(HAProxyInfoExtractor.class)
                .forLevel(DEBUG)
                .containsMessages("LOCAL command (health check)");
    }

    @Test
    void shouldPassThroughNonHAProxyMessages(StrictBufferContext ctx) {
        // Given
        var logging = NullLogProvider.getInstance();

        var channel = ctx.withConnection(new HAProxyInfoExtractor(logging));

        // When - send a regular buffer (not HAProxyMessage)
        var buf = ctx.outputBuffer().writeInt(0x6060B017);
        channel.writeInbound(buf);

        // Then - message should pass through and handler should remain
        var received = channel.readInbound();
        Assertions.assertThat(received).isNotNull();

        // Handler should still be in pipeline (only removes on HAProxyMessage)
        Assertions.assertThat(channel.pipeline().get(HAProxyInfoExtractor.class))
                .isNotNull();
    }

    @Test
    void shouldRemoveSelfAfterProcessingProxyMessage(StrictBufferContext ctx) {
        // Given
        var logging = NullLogProvider.getInstance();

        var proxyMsg = new HAProxyMessage(
                HAProxyProtocolVersion.V1,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP4,
                "10.0.0.1",
                "10.0.0.2",
                12345,
                7687);
        ctx.tracked(proxyMsg);

        var channel = ctx.withConnection(new HAProxyInfoExtractor(logging));

        // When
        channel.writeInbound(proxyMsg);

        // Then - handler should be removed
        Assertions.assertThat(channel.pipeline().get(HAProxyInfoExtractor.class))
                .isNull();
    }

    @Test
    void shouldHandleProxyV1LocalCommand(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        // V1 LOCAL command
        var proxyMsg = new HAProxyMessage(
                HAProxyProtocolVersion.V1, HAProxyCommand.LOCAL, HAProxyProxiedProtocol.UNKNOWN, null, null, 0, 0);
        ctx.tracked(proxyMsg);

        var channel = ctx.channel();
        var connection = ConnectionMockFactory.newFactory("test-connection")
                .attachTo(channel, new HAProxyInfoExtractor(logging));

        // When
        channel.writeInbound(proxyMsg);

        // Then - setProxyProtocolInfo should NOT be called for LOCAL
        verify(connection, never()).setProxyProtocolInfo(any());

        assertThat(logging)
                .forClass(HAProxyInfoExtractor.class)
                .forLevel(DEBUG)
                .containsMessages("LOCAL command (health check)");
    }
}

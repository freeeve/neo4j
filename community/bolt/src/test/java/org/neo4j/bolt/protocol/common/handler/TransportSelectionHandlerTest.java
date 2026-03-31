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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.neo4j.logging.AssertableLogProvider.Level.DEBUG;
import static org.neo4j.logging.AssertableLogProvider.Level.ERROR;
import static org.neo4j.logging.AssertableLogProvider.Level.WARN;
import static org.neo4j.logging.LogAssertions.assertThat;

import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.bolt.testing.annotation.StrictBufferExtension;
import org.neo4j.bolt.testing.channel.StrictBufferContext;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings.ProtocolLoggingMode;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.packstream.codec.transport.WebSocketFramePackingEncoder;
import org.neo4j.packstream.codec.transport.WebSocketFrameUnpackingDecoder;
import org.neo4j.ssl.SslPolicy;
import org.neo4j.ssl.config.ScopedSslPolicyProvider;

@StrictBufferExtension
class TransportSelectionHandlerTest {

    @Test
    void shouldLogOnUnexpectedExceptionsAndClosesContext(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var channel = ctx.withConnection(new TransportSelectionHandler(logging));

        // When
        var ex = new Throwable("Oh no!");
        channel.pipeline().fireExceptionCaught(ex);

        // Then
        assertThat(channel.isOpen()).isFalse();

        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(ERROR)
                .containsMessageWithException("Fatal error occurred when initialising pipeline: ", ex);
    }

    @Test
    void shouldLogConnectionResetErrorsAtWarningLevelAndClosesContext(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var channel = ctx.withConnection(new TransportSelectionHandler(logging));

        // When
        var ex = new IOException("Connection reset by peer");
        channel.pipeline().fireExceptionCaught(ex);

        // Then
        assertThat(channel.isOpen()).isFalse();

        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(WARN)
                .containsMessageWithArguments(
                        "Fatal error occurred when initialising pipeline, "
                                + "remote peer unexpectedly closed connection: %s",
                        channel);
    }

    @Test
    void shouldPreventMultipleLevelsOfSslEncryption(StrictBufferContext ctx) throws SSLException {
        // Given
        var logging = new AssertableLogProvider();

        var sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        var sslPolicyProvider = Mockito.mock(ScopedSslPolicyProvider.class);
        var sslPolicy = Mockito.mock(SslPolicy.class);

        Mockito.doReturn(sslPolicy).when(sslPolicyProvider).getPolicy();
        Mockito.doReturn(sslCtx).when(sslPolicy).nettyServerContext();

        var channel = ctx.withConnection(
                mock -> mock.withConfiguration(config -> config.sslPolicyProvider(sslPolicyProvider)),
                new TransportSelectionHandler(true, logging));

        // When
        channel.writeInbound(ctx.buffer(new byte[] {22, 3, 1, 0, 5})); // encrypted

        // Then
        assertThat(channel.isOpen()).isFalse();

        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(ERROR)
                .containsMessageWithArguments(
                        "Fatal error: multiple levels of SSL encryption detected." + " Terminating connection: %s",
                        channel);
    }

    @Test
    void shouldRemoveAllocationUponRemoval(StrictBufferContext ctx) {
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker),
                new TransportSelectionHandler(NullLogProvider.getInstance()));

        channel.pipeline().remove(TransportSelectionHandler.class);

        verify(memoryTracker).releaseHeap(TransportSelectionHandler.SHALLOW_SIZE);
    }

    @Test
    void shouldAllocateUponSslHandshake(StrictBufferContext ctx) throws SSLException {
        var memoryTracker = mock(MemoryTracker.class);

        var sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        var sslPolicyProvider = Mockito.mock(ScopedSslPolicyProvider.class);
        var sslPolicy = Mockito.mock(SslPolicy.class);

        Mockito.doReturn(sslPolicy).when(sslPolicyProvider).getPolicy();
        Mockito.doReturn(sslCtx).when(sslPolicy).nettyServerContext();

        var channel = ctx.withConnection(
                mock -> mock.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.sslPolicyProvider(sslPolicyProvider)),
                new TransportSelectionHandler(NullLogProvider.getInstance()));

        // do not track inbound buffer since the TLS handlers make isolated testing for release
        // impossible in this context
        channel.writeInbound(ctx.scopedBuffer(new byte[] {22, 3, 1, 0, 5}));

        verify(memoryTracker).allocateHeap(TransportSelectionHandler.SSL_HANDLER_SHALLOW_SIZE);

        // explicitly release in and outbound buffers here since the TLS handler would otherwise
        // write a bunch of stuff that fails the test
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAllocateUponWebsocketHandshake(StrictBufferContext ctx) {
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker),
                new TransportSelectionHandler(NullLogProvider.getInstance()));

        channel.writeInbound(ctx.buffer("GET /\r\n"));

        verify(memoryTracker)
                .allocateHeap(TransportSelectionHandler.HTTP_SERVER_CODEC_SHALLOW_SIZE
                        + TransportSelectionHandler.HTTP_OBJECT_AGGREGATOR_SHALLOW_SIZE
                        + DiscoveryResponseHandler.SHALLOW_SIZE
                        + TransportSelectionHandler.WEB_SOCKET_SERVER_PROTOCOL_HANDLER_SHALLOW_SIZE
                        + TransportSelectionHandler.WEB_SOCKET_FRAME_AGGREGATOR_SHALLOW_SIZE
                        + WebSocketFramePackingEncoder.SHALLOW_SIZE
                        + WebSocketFrameUnpackingDecoder.SHALLOW_SIZE);

        verify(memoryTracker).releaseHeap(TransportSelectionHandler.SHALLOW_SIZE);

        // discard irrelevant outbound handshake data
        // FIXME: Reports refCnt of 2 at the end of the test despite buffers internally indicating one
        //        reference
        channel.releaseOutbound();
    }

    @Test
    void shouldRejectPlainWebsocketConnectionWhenSecureRequired(StrictBufferContext ctx) throws SSLException {
        // Given
        var logging = new AssertableLogProvider();
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.requireEncryption(true)),
                new TransportSelectionHandler(logging));

        // When
        channel.writeInbound(ctx.buffer("GET /\r\n"));

        // Then
        assertThat(channel.isOpen()).isFalse();
        assertThat(channel.isActive()).isFalse();
        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(ERROR)
                .containsMessages("An unencrypted connection attempt was made where encryption is required.");
    }

    @Test
    void shouldRejectPlainSocketConnectionWhenSecureRequired(StrictBufferContext ctx) throws SSLException {
        // Given
        var logging = new AssertableLogProvider();
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.requireEncryption(true)),
                new TransportSelectionHandler(logging));

        // When
        channel.writeInbound(ctx.buffer().writeInt(0x6060B017).writeInt(0x00090005));
        channel.flush();

        // Then
        assertThat(channel.isOpen()).isFalse();
        assertThat(channel.isActive()).isFalse();
        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(ERROR)
                .containsMessages("An unencrypted connection attempt was made where encryption is required.");
    }

    @Test
    void shouldInstallProtocolLoggingHandlers(StrictBufferContext ctx) {
        var memoryTracker = Mockito.mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withConfiguration(config ->
                                config.enableProtocolLogging(true).protocolLoggingMode(ProtocolLoggingMode.BOTH))
                        .withMemoryTracker(memoryTracker),
                new TransportSelectionHandler(NullLogProvider.getInstance()));

        // generate an incomplete handshake which exceeds the 5-byte threshold of the selection
        // handler
        channel.writeInbound(ctx.buffer().writeInt(0x6060B017).writeInt(0x00090005));

        var handlers = channel.pipeline().names();

        Assertions.assertThat(handlers)
                .containsSequence(ProtocolLoggingHandler.RAW_NAME, "protocolNegotiationRequestEncoder")
                .containsSubsequence(
                        "protocolNegotiationRequestDecoder",
                        ProtocolLoggingHandler.DECODED_NAME,
                        "protocolHandshakeHandler");

        Mockito.verify(memoryTracker, Mockito.times(2)).allocateHeap(ProtocolLoggingHandler.SHALLOW_SIZE);
    }

    @Test
    void shouldInstallRawProtocolLoggingHandlers(StrictBufferContext ctx) {
        var memoryTracker = Mockito.mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withConfiguration(config ->
                                config.enableProtocolLogging(true).protocolLoggingMode(ProtocolLoggingMode.RAW))
                        .withMemoryTracker(memoryTracker),
                new TransportSelectionHandler(NullLogProvider.getInstance()));

        // generate an incomplete handshake which exceeds the 5-byte threshold of the selection
        // handler
        channel.writeInbound(ctx.buffer().writeInt(0x6060B017).writeInt(0x00090005));

        var handlers = channel.pipeline().names();

        Assertions.assertThat(handlers)
                .containsSequence(ProtocolLoggingHandler.RAW_NAME, "protocolNegotiationRequestEncoder")
                .doesNotContain(ProtocolLoggingHandler.DECODED_NAME);

        Mockito.verify(memoryTracker).allocateHeap(ProtocolLoggingHandler.SHALLOW_SIZE);
    }

    @Test
    void shouldInstallDecodedProtocolLoggingHandlers(StrictBufferContext ctx) {
        var memoryTracker = Mockito.mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withConfiguration(config ->
                                config.enableProtocolLogging(true).protocolLoggingMode(ProtocolLoggingMode.DECODED))
                        .withMemoryTracker(memoryTracker),
                new TransportSelectionHandler(NullLogProvider.getInstance()));

        // generate an incomplete handshake which exceeds the 5-byte threshold of the selection
        // handler
        channel.writeInbound(ctx.buffer().writeInt(0x6060B017).writeInt(0x00090005));

        var handlers = channel.pipeline().names();

        Assertions.assertThat(handlers)
                .containsSubsequence(
                        "protocolNegotiationRequestDecoder",
                        ProtocolLoggingHandler.DECODED_NAME,
                        "protocolHandshakeHandler")
                .doesNotContain(ProtocolLoggingHandler.RAW_NAME);

        Mockito.verify(memoryTracker).allocateHeap(ProtocolLoggingHandler.SHALLOW_SIZE);
    }

    // PROXY protocol v1 header (text-based)
    private static final String PROXY_V1_TCP4 = "PROXY TCP4 192.168.1.1 192.168.1.2 12345 7687";

    // PROXY protocol v2 signature (binary - 12 bytes)
    private static final byte[] PROXY_V2_SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    @Test
    void shouldDetectProxyProtocolV1AndInstallHandlers(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.enableProxyProtocol(true)),
                new TransportSelectionHandler(logging));

        // When - send PROXY protocol v1 header
        channel.writeInbound(ctx.buffer(PROXY_V1_TCP4.getBytes(StandardCharsets.US_ASCII)));

        // Then - TransportSelectionHandler should install HAProxy handlers
        var handlers = channel.pipeline().names();
        Assertions.assertThat(handlers).contains("haproxyDecoder").contains("haproxyExtractor");

        Assertions.assertThat(channel.pipeline().get(HAProxyMessageDecoder.class))
                .isNotNull();
        Assertions.assertThat(channel.pipeline().get(HAProxyInfoExtractor.class))
                .isNotNull();

        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(DEBUG)
                .containsMessages("PROXY protocol header detected");

        channel.finishAndReleaseAll();
    }

    @Test
    void shouldDetectProxyProtocolV2AndInstallHandlers(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.enableProxyProtocol(true)),
                new TransportSelectionHandler(logging));

        // When - send PROXY protocol v2 binary signature
        channel.writeInbound(ctx.buffer(PROXY_V2_SIGNATURE));

        // Then - TransportSelectionHandler should install HAProxy handlers
        var handlers = channel.pipeline().names();
        Assertions.assertThat(handlers).contains("haproxyDecoder").contains("haproxyExtractor");

        assertThat(logging)
                .forClass(TransportSelectionHandler.class)
                .forLevel(DEBUG)
                .containsMessages("PROXY protocol header detected");

        channel.finishAndReleaseAll();
    }

    @Test
    void shouldPassThroughWhenNoProxyProtocolDetected(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.enableProxyProtocol(true)),
                new TransportSelectionHandler(logging));

        // When - send Bolt handshake magic (not PROXY protocol)
        channel.writeInbound(ctx.buffer().writeInt(0x6060B017).writeInt(0x00090005));

        // Then - TransportSelectionHandler should NOT install HAProxy handlers
        var handlers = channel.pipeline().names();
        Assertions.assertThat(handlers).doesNotContain("haproxyDecoder").doesNotContain("haproxyExtractor");

        // TransportSelectionHandler should continue with normal transport selection
        // (it should have switched to handshake, so it should be removed)
        Assertions.assertThat(channel.pipeline().get(TransportSelectionHandler.class))
                .isNull();

        channel.releaseInbound();
    }

    @Test
    void shouldPassThroughWebSocketConnection(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var channel = ctx.withConnection(
                conn -> conn.withConfiguration(config -> config.enableProxyProtocol(true)),
                new TransportSelectionHandler(logging));

        // When - send HTTP GET (WebSocket upgrade)
        channel.writeInbound(ctx.buffer("GET / HTTP/1.1\r\n"));

        // Then - TransportSelectionHandler should NOT install HAProxy handlers
        var handlers = channel.pipeline().names();
        Assertions.assertThat(handlers).doesNotContain("haproxyDecoder").doesNotContain("haproxyExtractor");

        // TransportSelectionHandler should continue with WebSocket setup
        Assertions.assertThat(channel.pipeline().get(TransportSelectionHandler.class))
                .isNull();

        // Discard outbound handshake data generated by WebSocket handlers
        channel.releaseOutbound();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldWaitForMoreBytesWhenInsufficient(StrictBufferContext ctx) {
        // Given
        var logging = NullLogProvider.getInstance();

        var channel = ctx.withConnection(
                conn -> conn.withConfiguration(config -> config.enableProxyProtocol(true)),
                new TransportSelectionHandler(logging));

        // When - send only 3 bytes (less than minimum detection bytes needed)
        channel.writeInbound(ctx.buffer(new byte[] {0x50, 0x52, 0x4F})); // "PRO"

        // Then - TransportSelectionHandler should still be in pipeline waiting for more data
        Assertions.assertThat(channel.pipeline().get(TransportSelectionHandler.class))
                .isNotNull();

        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAllocateMemoryWhenInstallingProxyHandlers(StrictBufferContext ctx) {
        // Given
        var logging = NullLogProvider.getInstance();
        var memoryTracker = mock(MemoryTracker.class);

        var channel = ctx.withConnection(
                conn -> conn.withMemoryTracker(memoryTracker)
                        .withConfiguration(config -> config.enableProxyProtocol(true)),
                new TransportSelectionHandler(logging));

        // When - send PROXY protocol v1 header
        channel.writeInbound(ctx.buffer(PROXY_V1_TCP4.getBytes(StandardCharsets.US_ASCII)));

        // Then - memory should be allocated for the new handlers
        verify(memoryTracker).allocateHeap(org.mockito.ArgumentMatchers.anyLong());

        channel.finishAndReleaseAll();
    }

    @Test
    void shouldNotDetectProxyProtocolWhenDisabled(StrictBufferContext ctx) {
        // Given
        var logging = new AssertableLogProvider();

        var channel = ctx.withConnection(
                conn -> conn.withConfiguration(config -> config.enableProxyProtocol(false)),
                new TransportSelectionHandler(logging));

        // When - send PROXY protocol v1 header
        channel.writeInbound(ctx.buffer(PROXY_V1_TCP4.getBytes(StandardCharsets.US_ASCII)));

        // Then - TransportSelectionHandler should NOT install HAProxy handlers
        // Instead, it should try to detect transport (and fail/close since PROXY looks like invalid transport)
        var handlers = channel.pipeline().names();
        Assertions.assertThat(handlers).doesNotContain("haproxyDecoder").doesNotContain("haproxyExtractor");

        channel.finishAndReleaseAll();
    }
}

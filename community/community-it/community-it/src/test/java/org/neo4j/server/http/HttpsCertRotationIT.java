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
package org.neo4j.server.http;

import static org.neo4j.configuration.ssl.SslPolicyScope.HTTPS;
import static org.neo4j.server.helpers.CommunityWebContainerBuilder.serverOnRandomPorts;
import static org.neo4j.test.assertion.Assert.assertEventually;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.ssl.SslPolicyConfig;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.helpers.TestWebContainer;
import org.neo4j.ssl.SslResourceBuilder;
import org.neo4j.test.extension.SkipOnSpd;
import org.neo4j.test.server.ExclusiveWebContainerTestBase;
import org.neo4j.test.server.InsecureTrustManager;

class HttpsCertRotationIT extends ExclusiveWebContainerTestBase {
    private TestWebContainer testWebContainer;

    @Test
    @SkipOnSpd
    void shouldReplaceCertificate() throws Exception {

        // setup cert
        int keyId = 0;
        var policyDir = testDirectory.homePath().resolve("certificates");
        var fs = testDirectory.getFileSystem();
        fs.mkdirs(policyDir);
        SslResourceBuilder.caSignedKeyId(keyId).trustSignedByCA().install(policyDir);
        SslPolicyConfig sslPolicy = SslPolicyConfig.forScope(HTTPS);

        testWebContainer = serverOnRandomPorts()
                .persistent()
                .usingDataDir(
                        testDirectory.directory(methodName).toAbsolutePath().toString())
                .withHttpsEnabled()
                .withHttpDisabled()
                .withProperty(ServerSettings.http_enabled_transports.name(), "HTTP1_1,HTTP2")
                .withProperty(sslPolicy.base_directory.name(), "certificates")
                .build();

        waitBeStartedForSeconds(20);

        var trustAllSslContext = SSLContext.getInstance("TLS");
        trustAllSslContext.init(null, new TrustManager[] {new InsecureTrustManager()}, null);

        var testClientInvocation = new ThrowingSupplier<X509Certificate>() {

            @Override
            public X509Certificate get() throws Exception {
                return testClient(testWebContainer.getBaseUri().toString());
            }
        };

        int retries = 6;
        long delayBeforeRetryMs = 500L;

        X509Certificate originalCert = retry(testClientInvocation, retries, delayBeforeRetryMs);

        testWebContainer.replaceHTTPSCertificate();

        waitBeStartedForSeconds(10);

        assertEventually(
                () -> retry(testClientInvocation, retries, delayBeforeRetryMs),
                currentCert -> !currentCert.getSerialNumber().equals(originalCert.getSerialNumber()),
                10,
                TimeUnit.SECONDS);
    }

    @AfterEach
    void cleanup() {
        if (testWebContainer != null) {
            testWebContainer.shutdown();
        }
    }

    private static Stream<HttpClient.Version> httpVersions() {
        return Stream.of(HttpClient.Version.HTTP_1_1, HttpClient.Version.HTTP_2);
    }

    public static X509Certificate testClient(String url) throws Exception {
        URI uri = new URI(url);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 443 : uri.getPort();

        // Configure SSL context to trust all certificates (for testing purposes only)
        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        final SSLEngine[] lastEngine = new SSLEngine[1];
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    var sslHandler = sslContext.newHandler(ch.alloc(), host, port);
                    lastEngine[0] = sslHandler.engine();
                    pipeline.addLast(sslHandler);
                    pipeline.addLast(new HttpClientCodec());
                    pipeline.addLast(new HttpContentDecompressor());
                    pipeline.addLast(new HttpObjectAggregator(1048576)); // Aggregate HTTP content
                    pipeline.addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                            System.out.println("Response received:");
                            var response = msg.content().toString(io.netty.util.CharsetUtil.UTF_8);
                            System.out.println(response);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            cause.printStackTrace();
                            ctx.close();
                        }
                    });
                }
            });

            // Start the client
            ChannelFuture future = bootstrap.connect(host, port).sync();

            // Send an HTTP GET request
            HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
            request.headers().set(HttpHeaderNames.HOST, host);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            future.channel().writeAndFlush(request);

            // Wait until the connection is closed
            future.channel().closeFuture().sync();

            if (lastEngine[0] != null) {
                var certs = lastEngine[0].getSession().getPeerCertificates();
                if (certs.length > 0) {
                    return (X509Certificate) certs[0];
                }
            }
            throw new IllegalStateException("request succeeded but no certificates seen");

        } finally {
            group.shutdownGracefully();
        }
    }

    private void waitBeStartedForSeconds(long timeout) {
        if (testWebContainer == null) {
            throw new IllegalStateException("testWebContainer should not be null");
        }
        assertEventually(
                () -> testWebContainer.getState(), state -> state.equals("STARTED"), timeout, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static X509Certificate retry(ThrowingSupplier<X509Certificate> function, int maxRetries, long delay)
            throws Exception {
        int attempts = 0;
        while (true) {
            try {
                return function.get();
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxRetries) {
                    throw e; // Throw the last encountered exception
                }
                Thread.sleep(delay);
            }
        }
    }
}

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
package org.neo4j.server.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.neo4j.server.configuration.ServerSettings.http_x_forward_allow_hosts;
import static org.neo4j.server.configuration.ServerSettings.http_x_forward_allow_proxies;
import static org.neo4j.server.configuration.ServerSettings.http_x_forward_enabled;
import static org.neo4j.server.configuration.ServerSettings.http_x_forward_private_ips_enabled;

import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.SecurityContext;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;

class SecureXForwardFilterTest {
    private static final String X_FORWARD_HOST_HEADER_KEY = "X-Forwarded-Host";
    private static final String X_FORWARD_PROTO_HEADER_KEY = "X-Forwarded-Proto";

    private AssertableLogProvider logProvider;
    private SecureXForwardFilter filter;
    private ContainerRequest request;

    @BeforeEach
    void setUp() {
        logProvider = new AssertableLogProvider();

        request = new ContainerRequest(
                URI.create("http://internal.server.com"),
                URI.create("http://internal.server.com/foo/bar"),
                "GET",
                mock(SecurityContext.class),
                mock(PropertiesDelegate.class),
                null);
    }

    @Test
    void shouldIgnoreHeadersWhenDisabled() {
        // given
        var config = Config.defaults(http_x_forward_enabled, false);
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("evil.com"));
        request.headers(headers);

        URI originalBaseUri = request.getBaseUri();

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri()).isEqualTo(originalBaseUri);
        LogAssertions.assertThat(logProvider).containsMessages("X-Forward header processing disabled (secure default)");
    }

    @Test
    void shouldIgnoreHeadersWhenConfigurationInsecure() {
        // given
        var config = Config.defaults(http_x_forward_enabled, true);
        filter = new SecureXForwardFilter(config, logProvider);

        request.headers(Map.of());

        // when
        filter.filter(request);
    }

    @Test
    void shouldProcessHeadersFromTrustedProxy() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1")) // Use test fallback IP
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("trusted.com"));
        request.headers(headers);

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri().toString()).contains("trusted.com");
    }

    @Test
    void shouldRejectHeadersFromUntrustedProxy() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("10.0.0.5"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("evil.com"));
        request.headers(headers);

        URI originalBaseUri = request.getBaseUri();

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri()).isEqualTo(originalBaseUri);
        LogAssertions.assertThat(logProvider)
                .containsMessages("Untrusted source 127.0.0.1 attempted to send X-Forward headers");
    }

    @Test
    void shouldRejectHostNotInAllowlist() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_allow_hosts, List.of("allowed.com"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("evil.com"));
        request.headers(headers);
        URI originalBaseUri = request.getBaseUri();

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri()).isEqualTo(originalBaseUri);
        LogAssertions.assertThat(logProvider)
                .containsMessages("Rejected X-Forwarded-Host 'evil.com'", "not in allowed hosts");
    }

    @Test
    void shouldAcceptHostInAllowlist() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_allow_hosts, List.of("allowed.com"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("allowed.com"));
        request.headers(headers);

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri().toString()).contains("allowed.com");
    }

    @Test
    void shouldRejectPrivateIPWhenBlocked() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_private_ips_enabled, false)
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("10.0.0.5"));
        request.headers(headers);

        URI originalBaseUri = request.getBaseUri();

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri()).isEqualTo(originalBaseUri);
        LogAssertions.assertThat(logProvider)
                .containsMessages("Rejected X-Forwarded-Host '10.0.0.5'", "private IP address blocked");
    }

    @Test
    void shouldAcceptPrivateIPWhenAllowed() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_private_ips_enabled, true)
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("10.0.0.5"));
        request.headers(headers);

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri().toString()).contains("10.0.0.5");
    }

    @Test
    void shouldRejectMaliciousPatterns() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("evil<script>alert(1)</script>.com"));
        request.headers(headers);

        URI originalBaseUri = request.getBaseUri();

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri()).isEqualTo(originalBaseUri);
        LogAssertions.assertThat(logProvider)
                .containsMessages(
                        "Rejected X-Forwarded-Host 'evil?script?alert(1)?/script?.com'", "contains malicious patterns");
    }

    @Test
    void shouldHandleHostWithPort() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_allow_hosts, List.of("example.com"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("example.com:8080"));
        request.headers(headers);

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri().toString()).contains("example.com:8080");
    }

    @Test
    void shouldPickFirstHostFromCommaSeparatedList() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_allow_hosts, List.of("first.com"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        // Test comma-separated list like the failing integration test
        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("first.com, second.com,third.com"));
        request.headers(headers);

        // when
        filter.filter(request);

        // then - should use first.com from the list
        assertThat(request.getBaseUri().toString()).contains("first.com");
    }

    @Test
    void shouldRejectCommaSeparatedListIfFirstHostNotAllowed() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .set(http_x_forward_allow_hosts, List.of("allowed.com"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(X_FORWARD_HOST_HEADER_KEY, List.of("evil.com, allowed.com,third.com"));
        request.headers(headers);

        URI originalBaseUri = request.getBaseUri();

        // when
        filter.filter(request);

        // then - should be rejected because first host (evil.com) is not allowed
        assertThat(request.getBaseUri()).isEqualTo(originalBaseUri);
        LogAssertions.assertThat(logProvider)
                .containsMessages("Rejected X-Forwarded-Host 'evil.com'", "not in allowed hosts");
    }

    @Test
    void shouldLogSecurityWarningsWhenConfigurationIsInsecure() {
        // given - empty trusted proxies and allowed hosts
        var config = Config.defaults(http_x_forward_enabled, true);
        filter = new SecureXForwardFilter(config, logProvider);

        // then
        LogAssertions.assertThat(logProvider)
                .containsMessages("SECURITY WARNING: X-Forward headers accepted from any source")
                .containsMessages("SECURITY WARNING: X-Forward headers accept any hostname");
    }

    @Test
    void shouldHandleXForwardedProto() {
        // given
        var config = Config.newBuilder()
                .set(http_x_forward_enabled, true)
                .set(http_x_forward_allow_proxies, List.of("127.0.0.1"))
                .build();
        filter = new SecureXForwardFilter(config, logProvider);

        var headers = Map.of(
                X_FORWARD_HOST_HEADER_KEY, List.of("example.com"),
                X_FORWARD_PROTO_HEADER_KEY, List.of("https"));
        request.headers(headers);

        // when
        filter.filter(request);

        // then
        assertThat(request.getBaseUri().getScheme()).isEqualTo("https");
        assertThat(request.getBaseUri().toString()).contains("example.com");
    }
}

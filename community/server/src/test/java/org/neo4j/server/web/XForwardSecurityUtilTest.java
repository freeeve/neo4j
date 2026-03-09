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

import org.junit.jupiter.api.Test;

class XForwardSecurityUtilTest {

    @Test
    void shouldDetectPrivateIPv4Addresses() {
        // Class A private (10.0.0.0/8)
        assertThat(XForwardSecurityUtil.isPrivateIP("10.0.0.1")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("10.255.255.255")).isTrue();

        // Class B private (172.16.0.0/12)
        assertThat(XForwardSecurityUtil.isPrivateIP("172.16.0.1")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("172.31.255.255")).isTrue();

        // Class C private (192.168.0.0/16)
        assertThat(XForwardSecurityUtil.isPrivateIP("192.168.0.1")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("192.168.255.255")).isTrue();

        // Loopback (127.0.0.0/8)
        assertThat(XForwardSecurityUtil.isPrivateIP("127.0.0.1")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("127.255.255.255")).isTrue();

        // Link-local (169.254.0.0/16)
        assertThat(XForwardSecurityUtil.isPrivateIP("169.254.1.1")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("169.254.255.255")).isTrue();

        // Localhost variants
        assertThat(XForwardSecurityUtil.isPrivateIP("localhost")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("localhost.localdomain")).isTrue();
    }

    @Test
    void shouldNotDetectPublicIPv4Addresses() {
        assertThat(XForwardSecurityUtil.isPrivateIP("8.8.8.8")).isFalse();
        assertThat(XForwardSecurityUtil.isPrivateIP("1.1.1.1")).isFalse();
        assertThat(XForwardSecurityUtil.isPrivateIP("173.15.255.255")).isFalse(); // Just outside 172.16.0.0/12
        assertThat(XForwardSecurityUtil.isPrivateIP("172.32.0.1")).isFalse(); // Just outside 172.16.0.0/12
        assertThat(XForwardSecurityUtil.isPrivateIP("11.0.0.1")).isFalse(); // Just outside 10.0.0.0/8
        assertThat(XForwardSecurityUtil.isPrivateIP("192.167.255.255")).isFalse(); // Just outside 192.168.0.0/16
        assertThat(XForwardSecurityUtil.isPrivateIP("example.com")).isFalse();
    }

    @Test
    void shouldDetectPrivateIPv6Addresses() {
        // IPv6 loopback
        assertThat(XForwardSecurityUtil.isPrivateIP("::1")).isTrue();

        // IPv6 unique local addresses (fc00::/7 and fd00::/7)
        assertThat(XForwardSecurityUtil.isPrivateIP("fc00:1234::1")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("fd12:3456::1")).isTrue();

        // IPv6 link-local (fe80::/10)
        assertThat(XForwardSecurityUtil.isPrivateIP("fe80::1234")).isTrue();
        assertThat(XForwardSecurityUtil.isPrivateIP("feb0::1234")).isTrue();
    }

    @Test
    void shouldDetectMaliciousPatterns() {
        // HTML/Script injection
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("evil<script>alert(1)</script>"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("test>malicious"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("bad\"quotes"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("single'quote"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("ampersand&attack"))
                .isTrue();

        // Dangerous protocols
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("javascript:alert(1)"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("data:text/html,<script>"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("vbscript:msgbox"))
                .isTrue();

        // Path traversal
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("../../../etc/passwd"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("..\\windows\\system32"))
                .isTrue();

        // Control characters
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("evil\u0000null"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("bad\u001Fcontrol"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("delete\u007F"))
                .isTrue();

        // Backslashes (Windows path injection)
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("c:\\windows\\system32"))
                .isTrue();

        // URL encoding (suspicious in hostname)
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("evil%2Ecom")).isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("test%20space"))
                .isTrue();
    }

    @Test
    void shouldNotDetectValidHostnames() {
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("example.com"))
                .isFalse();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("api.service.com"))
                .isFalse();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("test-service.internal"))
                .isFalse();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("192.168.1.1"))
                .isFalse();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("localhost")).isFalse();
    }

    @Test
    void shouldSanitizeForLogging() {
        assertThat(XForwardSecurityUtil.sanitizeForLogging(null)).isEqualTo("null");
        assertThat(XForwardSecurityUtil.sanitizeForLogging("normal.com")).isEqualTo("normal.com");

        // Control characters should be replaced
        assertThat(XForwardSecurityUtil.sanitizeForLogging("evil\u0000null")).isEqualTo("evil?null");
        assertThat(XForwardSecurityUtil.sanitizeForLogging("bad\u001Fcontrol")).isEqualTo("bad?control");

        // Dangerous HTML characters should be replaced
        assertThat(XForwardSecurityUtil.sanitizeForLogging("evil<script>")).isEqualTo("evil?script?");
        assertThat(XForwardSecurityUtil.sanitizeForLogging("test\"quote'")).isEqualTo("test?quote?");

        // Very long strings should be truncated
        String longString = "a".repeat(150);
        String sanitized = XForwardSecurityUtil.sanitizeForLogging(longString);
        assertThat(sanitized).hasSize(100);
        assertThat(sanitized).endsWith("...");
    }

    @Test
    void shouldValidateHostnameFormat() {
        // Valid hostnames
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("example.com")).isTrue();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("api.service.internal"))
                .isTrue();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("test-service")).isTrue();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("192.168.1.1")).isTrue();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("localhost")).isTrue();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("a")).isTrue();

        // Invalid hostnames
        assertThat(XForwardSecurityUtil.isValidHostnameFormat(null)).isFalse();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("")).isFalse();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("   ")).isFalse();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("-invalid")).isFalse();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("invalid-")).isFalse();
        assertThat(XForwardSecurityUtil.isValidHostnameFormat("invalid..hostname"))
                .isFalse();

        // Too long (over 253 characters)
        String tooLong = "a".repeat(254);
        assertThat(XForwardSecurityUtil.isValidHostnameFormat(tooLong)).isFalse();
    }

    @Test
    void shouldHandleEdgeCases() {
        // Empty and null strings
        assertThat(XForwardSecurityUtil.isPrivateIP(null)).isFalse();
        assertThat(XForwardSecurityUtil.isPrivateIP("")).isFalse();
        assertThat(XForwardSecurityUtil.isPrivateIP("   ")).isFalse();

        assertThat(XForwardSecurityUtil.containsMaliciousPatterns(null)).isFalse();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("")).isFalse();

        // Case sensitivity
        assertThat(XForwardSecurityUtil.isPrivateIP("LOCALHOST")).isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("JAVASCRIPT:alert"))
                .isTrue();
        assertThat(XForwardSecurityUtil.containsMaliciousPatterns("DATA:text/html"))
                .isTrue();
    }
}

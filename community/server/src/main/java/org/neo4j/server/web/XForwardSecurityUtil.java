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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Security utilities for X-Forward header validation.
 *
 * This class provides validation methods to prevent common attacks
 * involving X-Forwarded-Host headers, including:
 * - Private IP address detection (RFC 1918)
 * - Malicious pattern detection
 * - Input sanitization
 */
public final class XForwardSecurityUtil {

    // Private IP address patterns (RFC 1918)
    private static final Pattern PRIVATE_IP_V4_PATTERN = Pattern.compile("^(?:" + "10\\."
            + // Class A: 10.0.0.0/8
            "|172\\.(?:1[6-9]|2[0-9]|3[01])\\."
            + // Class B: 172.16.0.0/12
            "|192\\.168\\."
            + // Class C: 192.168.0.0/16
            "|127\\."
            + // Loopback: 127.0.0.0/8
            "|169\\.254\\."
            + // Link-local: 169.254.0.0/16
            ")");

    // IPv6 private patterns
    private static final Pattern PRIVATE_IP_V6_PATTERN = Pattern.compile(
            "^(?:" + "::1|"
                    + // Loopback
                    "fc[0-9a-f]{2}:|"
                    + // Unique local addresses fc00::/7
                    "fd[0-9a-f]{2}:|"
                    + // Unique local addresses fd00::/7
                    "fe[89ab][0-9a-f]:"
                    + // Link-local fe80::/10
                    ")",
            Pattern.CASE_INSENSITIVE);

    // Patterns that might indicate malicious intent
    private static final Pattern[] MALICIOUS_PATTERNS = {
        Pattern.compile(".*[<>\"'&].*"), // HTML/script injection
        Pattern.compile(".*(?:javascript|data|vbscript):.*", Pattern.CASE_INSENSITIVE), // Dangerous protocols
        Pattern.compile(".*\\.\\..*"), // Path traversal
        Pattern.compile(".*[\\x00-\\x1F\\x7F].*"), // Control characters
        Pattern.compile(".*\\\\.*"), // Backslashes (Windows path injection)
        Pattern.compile(".*%[0-9a-f]{2}.*", Pattern.CASE_INSENSITIVE), // URL encoding (suspicious in hostname)
    };

    private XForwardSecurityUtil() {
        // Utility class - no instances
    }

    /**
     * Checks if the given hostname is a private IP address according to RFC 1918
     * or other reserved ranges.
     *
     * @param hostname The hostname to check
     * @return true if the hostname is a private IP address
     */
    public static boolean isPrivateIP(String hostname) {
        if (hostname == null || hostname.trim().isEmpty()) {
            return false;
        }

        String trimmedHostname = hostname.trim().toLowerCase();

        // Check for localhost variants
        if ("localhost".equals(trimmedHostname) || "localhost.localdomain".equals(trimmedHostname)) {
            return true;
        }

        // Check IPv4 private patterns
        if (PRIVATE_IP_V4_PATTERN.matcher(trimmedHostname).find()) {
            return true;
        }

        // Check IPv6 private patterns
        if (PRIVATE_IP_V6_PATTERN.matcher(trimmedHostname).find()) {
            return true;
        }

        // Try to resolve and check if it's a private address
        try {
            InetAddress address = InetAddress.getByName(trimmedHostname);
            return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            // If we can't resolve it, treat it as potentially unsafe
            return false;
        }
    }

    /**
     * Checks if the hostname contains patterns that might indicate malicious intent.
     *
     * @param hostname The hostname to check
     * @return true if malicious patterns are detected
     */
    public static boolean containsMaliciousPatterns(String hostname) {
        if (hostname == null) {
            return false;
        }

        for (Pattern pattern : MALICIOUS_PATTERNS) {
            if (pattern.matcher(hostname).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sanitizes a hostname for logging purposes, removing potentially dangerous characters.
     *
     * @param hostname The hostname to sanitize
     * @return A sanitized version safe for logging
     */
    public static String sanitizeForLogging(String hostname) {
        if (hostname == null) {
            return "null";
        }

        // Remove control characters and limit length for safe logging
        String sanitized = hostname.replaceAll("[\\x00-\\x1F\\x7F]", "?").replaceAll("[<>\"'&]", "?");

        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 97) + "...";
        }

        return sanitized;
    }

    /**
     * Validates that a hostname follows basic format rules.
     *
     * @param hostname The hostname to validate
     * @return true if the hostname format is valid
     */
    public static boolean isValidHostnameFormat(String hostname) {
        if (hostname == null || hostname.trim().isEmpty()) {
            return false;
        }

        String trimmed = hostname.trim();

        // Basic length check
        if (trimmed.length() > 253) {
            return false;
        }

        // Check for valid hostname characters with no consecutive dots
        Pattern validHostnamePattern =
                Pattern.compile("(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$");

        return validHostnamePattern.matcher(trimmed).matches();
    }
}

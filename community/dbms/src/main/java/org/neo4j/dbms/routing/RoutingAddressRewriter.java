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
package org.neo4j.dbms.routing;

import java.util.regex.Pattern;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.util.VisibleForTesting;

public class RoutingAddressRewriter {

    private final String template;
    private final Pattern clientRegex;
    private final Pattern tableRegex;

    public RoutingAddressRewriter(String template, String clientRegex, String tableRegex) {
        this.template = template;
        this.clientRegex = Pattern.compile(clientRegex);
        this.tableRegex = Pattern.compile(tableRegex);
    }

    public RoutingResult rewrite(RoutingResult routingIn, SocketAddress clientAddress) {
        var routeList = routingIn.routeEndpoints().stream()
                .map(src -> rewriteAddress(src, clientAddress))
                .toList();
        var writeList = routingIn.writeEndpoints().stream()
                .map(src -> rewriteAddress(src, clientAddress))
                .toList();
        var readList = routingIn.readEndpoints().stream()
                .map(src -> rewriteAddress(src, clientAddress))
                .toList();

        return new RoutingResult(routeList, writeList, readList, routingIn.ttlMillis());
    }

    @VisibleForTesting
    SocketAddress rewriteAddress(SocketAddress tableAddress, SocketAddress clientAddress) {
        var tableAddressMatcher = tableRegex.matcher(tableAddress.getHostname());
        var clientAddressMatcher = clientRegex.matcher(clientAddress.getHostname());
        if (tableAddressMatcher.matches() && clientAddressMatcher.matches()) {
            String result = template;
            for (var i = 1; i <= tableAddressMatcher.groupCount(); i++) {
                var matchText = String.format("${t:%d}", i);
                result = result.replace(matchText, tableAddressMatcher.group(i));
            }
            for (var i = 1; i <= clientAddressMatcher.groupCount(); i++) {
                var matchText = String.format("${c:%d}", i);
                result = result.replace(matchText, clientAddressMatcher.group(i));
            }
            var port = tableAddress.getPort();
            return new SocketAddress(result, port);
        } else {
            return tableAddress;
        }
    }
}

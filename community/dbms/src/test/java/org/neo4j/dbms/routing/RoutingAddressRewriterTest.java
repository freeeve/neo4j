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

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.configuration.helpers.SocketAddress;

public class RoutingAddressRewriterTest {

    static final int PORT = 8000;

    private static class TestScenario {
        public final RoutingAddressRewriter rewriter;
        public final SocketAddress fromTable;
        public final SocketAddress fromClient;
        public final SocketAddress expected;

        public TestScenario(
                String template,
                String tableRewrite,
                String clientRewrite,
                String fromTable,
                String fromClient,
                String expected) {
            this.rewriter = new RoutingAddressRewriter(template, clientRewrite, tableRewrite);
            this.fromTable = new SocketAddress(fromTable, PORT);
            this.fromClient = new SocketAddress(fromClient, PORT);
            this.expected = new SocketAddress(expected, PORT);
        }
    }

    private static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.of(new TestScenario("template", ".*", ".*", "", "", "template")),
                Arguments.of(new TestScenario("${t:1}", "(.*)", "(.*)", "a", "b", "a")),
                Arguments.of(new TestScenario("${c:1}", "(.*)", "(.*)", "a", "b", "b")),
                Arguments.of(new TestScenario("${t:1}${c:1}", "(.*)", "(.*)", "a", "b", "ab")),
                Arguments.of(new TestScenario(
                        "${t:1}.${t:2}.${c:2}",
                        "(.*?)\\.(.*?)\\.(.*)",
                        "(.*?)\\.(.*)",
                        "pod1.orch-0002.neo4j-dev.io",
                        "db1.databases-private.neo4j-dev.io",
                        "pod1.orch-0002.databases-private.neo4j-dev.io")),
                Arguments.of(new TestScenario(
                        "${t:1}.${t:2}.${c:2}",
                        "(.*?)\\.(.*?)\\.(.*)",
                        "(.*?)\\.(.*)",
                        "pod1.orch-0002.neo4j-dev.io",
                        "db1.ingress1.databases-private.neo4j-dev.io",
                        "pod1.orch-0002.ingress1.databases-private.neo4j-dev.io")));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void shouldRewriteAddressCorrectly(TestScenario testScenario) {
        var actual = testScenario.rewriter.rewriteAddress(testScenario.fromTable, testScenario.fromClient);
        Assertions.assertEquals(testScenario.expected, actual);
    }

    @Test
    void shouldRewriteTable() {
        var template = "${t:1}.${c:1}";
        var clientRewrite = "(.*)";
        var tableRewrite = "(.*)";
        var rewriter = new RoutingAddressRewriter(template, clientRewrite, tableRewrite);

        var address1 = new SocketAddress("pod1", 1);
        var address2 = new SocketAddress("pod2", 2);
        var address3 = new SocketAddress("pod3", 3);

        var readers = List.of(address1, address3);
        var writers = List.of(address3, address2);
        var routers = List.of(address1, address2, address3);

        var table = new RoutingResult(routers, writers, readers, 42);
        var clientAddress = new SocketAddress("client", 4);
        var rewrittenResult = rewriter.rewrite(table, clientAddress);

        var routeList = rewrittenResult.routeEndpoints();
        Assertions.assertEquals(3, routeList.size());
        Assertions.assertEquals(
                List.of(
                        new SocketAddress("pod1.client", 1),
                        new SocketAddress("pod2.client", 2),
                        new SocketAddress("pod3.client", 3)),
                routeList);

        var readList = rewrittenResult.readEndpoints();
        Assertions.assertEquals(2, readList.size());
        Assertions.assertEquals(
                List.of(new SocketAddress("pod1.client", 1), new SocketAddress("pod3.client", 3)), readList);

        var writeList = rewrittenResult.writeEndpoints();
        Assertions.assertEquals(2, writeList.size());
        Assertions.assertEquals(
                List.of(new SocketAddress("pod3.client", 3), new SocketAddress("pod2.client", 2)), writeList);
    }
}

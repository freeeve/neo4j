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
package org.neo4j.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;
import org.neo4j.configuration.connectors.ConnectorType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.internal.helpers.HostnamePort;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.PortUtils;

public class MetricEndpoints {
    public static String getPrometheusResponse(GraphDatabaseService database) throws Exception {
        HostnamePort connectorAddress =
                PortUtils.getConnectorAddress((GraphDatabaseAPI) database, ConnectorType.PROMETHEUS);
        return getPrometheusResponse(connectorAddress);
    }

    public static String getPrometheusResponse(HostnamePort connectorAddress) throws Exception {
        String url = "http://" + connectorAddress + "/metrics";

        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(url)).build();
            var result =
                    client.send(request, HttpResponse.BodyHandlers.ofString()).body();

            try (Scanner s = new Scanner(result).useDelimiter("\\A")) {
                assertTrue(s.hasNext());
                return s.next();
            }
        }
    }
}

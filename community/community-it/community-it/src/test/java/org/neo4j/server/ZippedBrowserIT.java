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
package org.neo4j.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.configuration.connectors.ConnectorType;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

public class ZippedBrowserIT {

    private static DatabaseManagementService dbms;
    private static String severEndpoint;

    @BeforeAll
    static void beforeAll() throws IOException {
        var builder = new TestDatabaseManagementServiceBuilder();
        var tempDir = Files.createTempDirectory("tempDir");
        configureBrowserDir(tempDir);

        dbms = builder.setConfig(HttpConnector.enabled, true)
                .setConfig(HttpConnector.listen_address, new SocketAddress("localhost", 0))
                .setConfig(BoltConnector.enabled, true)
                .setDatabaseRootDirectory(tempDir)
                .build();

        severEndpoint = "http://"
                + resolveDependency(dbms, ConnectorPortRegister.class).getLocalAddress(ConnectorType.HTTP) + "/";
    }

    @AfterAll
    static void teardown() {
        dbms.shutdown();
    }

    @Test
    public void testBrowserZip() throws IOException, InterruptedException {
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(severEndpoint + "browser/"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("<h1>New Improved Browser</h1>\n" + "<h2>Designed by Oskar</h2>\n");
    }

    @Test
    public void testBrowserZipRedirect() throws IOException, InterruptedException {
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(severEndpoint))
                .header("Accept", "text/html")
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("<h1>New Improved Browser</h1>\n" + "<h2>Designed by Oskar</h2>\n");
    }

    private static void configureBrowserDir(Path homeDirectory) throws IOException {
        var webDirectory = homeDirectory.resolve("web");
        Files.createDirectories(webDirectory);

        var zippedDirectory = webDirectory.resolve("neo4j-browser.zip");
        Files.copy(
                Objects.requireNonNull(ZippedBrowserIT.class.getResourceAsStream("neo4j-browser.zip")),
                zippedDirectory);
    }

    private static <T> T resolveDependency(DatabaseManagementService database, Class<T> cls) {
        return ((GraphDatabaseAPI) database.database("neo4j"))
                .getDependencyResolver()
                .resolveDependency(cls);
    }
}

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
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.configuration.SettingValueParsers.FALSE;
import static org.neo4j.configuration.SettingValueParsers.TRUE;
import static org.neo4j.io.fs.FileSystemUtils.readLines;
import static org.neo4j.server.AbstractNeoWebServer.NEO4J_IS_STARTING_MESSAGE;
import static org.neo4j.test.conditions.Conditions.containsAtLeastTheseLines;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.connectors.HttpsConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.log4j.LogConfig;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.test.ports.PortAuthority;
import org.neo4j.test.server.ExclusiveWebContainerTestBase;

class StartupLoggingIT extends ExclusiveWebContainerTestBase {
    @Test
    void shouldLogHelpfulStartupMessages() throws IOException {
        CommunityBootstrapper bootstrapper = new CommunityBootstrapper();
        Map<String, String> propertyPairs = getPropertyPairs();

        bootstrapper.start(testDirectory.homePath(), Path.of("nonexistent-file.conf"), propertyPairs, false, false);
        var resolver = getDependencyResolver(bootstrapper.getDatabaseManagementService());
        URI uri = resolver.resolveDependency(AbstractNeoWebServer.class).getBaseUri();
        bootstrapper.stop();

        List<String> captured = readLines(
                testDirectory.getFileSystem(),
                testDirectory.homePath().resolve(LogConfig.USER_LOG),
                EmptyMemoryTracker.INSTANCE);
        assertThat(captured)
                .satisfies(containsAtLeastTheseLines(
                        warn("Config file \\[nonexistent-file.conf\\] does not exist."),
                        info("Starting..."),
                        info(NEO4J_IS_STARTING_MESSAGE),
                        info("Remote interface available at " + uri),
                        info("id: .*"),
                        info("name: system"),
                        info("creationDate: .*"),
                        info("Started."),
                        info("Stopping..."),
                        info("Stopped.")));
    }

    @Test
    void shouldLogFailuresToSystemErr() throws InterruptedException, IOException {
        // A sister test for EnterpriseBootstrapper lives in com.neo4j.server.enterprise.EnterpriseBootstrapperIT
        // GIVEN
        CommunityBootstrapper bootstrapper = new CommunityBootstrapper();
        ByteArrayOutputStream sysErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(sysErr));
        int port = PortAuthority.allocatePort();
        Config.Builder configBuilder = Config.newBuilder()
                .setDefaults(GraphDatabaseSettings.SERVER_DEFAULTS)
                .set(HttpConnector.listen_address, new SocketAddress("localhost", port));
        Config config = configBuilder.build();

        // WHEN - The port to be used by the Neo4j webserver is already occupied
        CountDownLatch latch = new CountDownLatch(1);
        Thread otherProcessThatHasPort = new Thread(() -> {
            try (ServerSocket s = new ServerSocket(port, 0, InetAddress.getByName(null))) {
                latch.countDown();
                s.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        otherProcessThatHasPort.start();

        // WHEN - We attempt to create the Neo4j webserver
        latch.await();
        bootstrapper.start(testDirectory.homePath(), config, true);

        // THEN - Errors have been written to the correct System.err stream
        sysErr.flush();
        List<String> sysErrMsgs = List.of(sysErr.toString().split("\n"));
        assertThat(sysErrMsgs)
                .satisfies(containsAtLeastTheseLines(
                        Pattern.compile("Failed to start Neo4j on localhost:\\d{1,6}."),
                        // This exception can vary depending on environment. We only care that one is reported.
                        Pattern.compile(".*Exception: .*")));
    }

    private static DependencyResolver getDependencyResolver(DatabaseManagementService managementService) {
        return ((GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME)).getDependencyResolver();
    }

    private Map<String, String> getPropertyPairs() {
        Map<String, String> properties = new HashMap<>();

        properties.put(
                GraphDatabaseSettings.data_directory.name(),
                testDirectory.homePath().toString());
        properties.put(
                GraphDatabaseSettings.logs_directory.name(),
                testDirectory.homePath().toString());

        properties.put(HttpConnector.listen_address.name(), "localhost:0");
        properties.put(HttpConnector.advertised_address.name(), ":0");
        properties.put(HttpConnector.enabled.name(), TRUE);

        properties.put(HttpsConnector.listen_address.name(), "localhost:0");
        properties.put(HttpsConnector.advertised_address.name(), ":0");
        properties.put(HttpsConnector.enabled.name(), FALSE);

        properties.put(BoltConnector.enabled.name(), TRUE);
        properties.put(BoltConnector.listen_address.name(), "localhost:0");
        properties.put(BoltConnector.advertised_address.name(), ":0");
        properties.put(BoltConnector.encryption_level.name(), "DISABLED");

        properties.put(
                GraphDatabaseInternalSettings.databases_root_path.name(),
                testDirectory.absolutePath().toString());
        return properties;
    }

    private static Pattern info(String messagePattern) {
        return line("INFO", messagePattern);
    }

    private static Pattern warn(String messagePattern) {
        return line("WARN", messagePattern);
    }

    private static Pattern line(final String level, final String messagePattern) {
        return Pattern.compile(".*" + level + "\\s+" + messagePattern);
    }
}

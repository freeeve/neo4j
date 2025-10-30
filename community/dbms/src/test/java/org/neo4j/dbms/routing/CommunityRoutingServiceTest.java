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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.configuration.connectors.ConnectorType;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.database.DatabaseContext;
import org.neo4j.dbms.database.DatabaseContextProvider;
import org.neo4j.kernel.availability.DatabaseAvailabilityGuard;
import org.neo4j.kernel.database.Database;
import org.neo4j.kernel.database.DefaultDatabaseResolver;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.VirtualValues;

class CommunityRoutingServiceTest {
    @SuppressWarnings("unchecked")
    private final DatabaseContextProvider<DatabaseContext> databaseContextProvider =
            mock(DatabaseContextProvider.class);

    private final DefaultDatabaseResolver defaultDatabaseResolver = mock(DefaultDatabaseResolver.class);
    private final ConnectorPortRegister portRegister = new ConnectorPortRegister();
    private final Config enabledConfig = Config.defaults(BoltConnector.enabled, true);

    private final String dbName = "foo";

    @Test
    void shouldResolveDefaultDatabaseName() throws RoutingException {
        // given
        when(defaultDatabaseResolver.defaultDatabase(any())).thenReturn(dbName);
        var userName = "joe";
        var service = createService(enabledConfig);
        setupDatabase(true);

        // when
        service.route(null, null, MapValue.EMPTY, false);
        service.route("", null, MapValue.EMPTY, false);
        service.route(" ", null, MapValue.EMPTY, false);
        service.route(null, userName, MapValue.EMPTY, false);

        // then
        verify(defaultDatabaseResolver, times(3)).defaultDatabase(null);
        verify(defaultDatabaseResolver).defaultDatabase(userName);
        verify(databaseContextProvider, times(4)).getDatabaseContext(dbName);
    }

    @Test
    void shouldThrowIfDatabaseDoesNotExist() {
        // given
        var service = createService(enabledConfig);

        // when / then
        assertThatThrownBy(() -> service.route(dbName, null, MapValue.EMPTY, false))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("database does not exist");
        verify(databaseContextProvider).getDatabaseContext(dbName);
    }

    @Test
    void shouldThrowIfDatabaseIsNotAvailable() {
        // given
        var service = createService(enabledConfig);
        setupDatabase(false);

        // when / then
        assertThatThrownBy(() -> service.route(dbName, null, MapValue.EMPTY, false))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("database is unavailable");
        verify(databaseContextProvider).getDatabaseContext(dbName);
    }

    @Test
    void shouldThrowIfBoltIsDisabled() {
        // given
        var service = createService(Config.defaults());
        setupDatabase(true);

        // when / then
        assertThatThrownBy(() -> service.route(dbName, null, MapValue.EMPTY, false))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Bolt is not enabled");
        verify(databaseContextProvider).getDatabaseContext(dbName);
    }

    @Test
    void shouldReturnConfigValues() throws RoutingException {
        // given
        var socketAddress = new SocketAddress(UUID.randomUUID().toString(), 1024 + new Random().nextInt(64512));
        var ttl = Duration.ofMillis(1000 + new Random().nextInt(60000));
        var config = Config.newBuilder()
                .set(BoltConnector.enabled, true)
                .set(BoltConnector.advertised_address, socketAddress)
                .set(GraphDatabaseSettings.routing_ttl, ttl)
                .build();

        var service = createService(config);
        setupDatabase(true);

        // when
        var result = service.route(dbName, null, MapValue.EMPTY, false);

        // then
        assertThat(result.ttlMillis()).isEqualTo(ttl.toMillis());
        assertAddress(result, socketAddress);
        verify(databaseContextProvider).getDatabaseContext(dbName);
    }

    @Test
    void shouldThrowForInvalidProvidedAddress() {
        // given
        var service = createService(enabledConfig);
        setupDatabase(true);

        // when / then
        Stream.of(Values.utf8Value(""), Values.utf8Value(" "), Values.intValue(42))
                .forEach(value -> assertThatThrownBy(() -> service.route(
                                dbName,
                                null,
                                VirtualValues.map(
                                        new String[] {RoutingTableServiceHelpers.ADDRESS_CONTEXT_KEY},
                                        new AnyValue[] {value}),
                                false))
                        .isInstanceOf(RoutingException.class)
                        .hasMessageContaining("value could not be parsed"));
    }

    @Test
    void shouldEchoProvidedAddressIfPossible() throws RoutingException {
        // given
        var clientHost = UUID.randomUUID().toString();
        var serverHost = UUID.randomUUID().toString();
        int clientPort = 1024 + new Random().nextInt(31744);
        int serverPort = 32768 + new Random().nextInt(31744);
        var config = Config.newBuilder()
                .set(BoltConnector.enabled, true)
                .set(BoltConnector.advertised_address, new SocketAddress(serverHost, 0))
                .build();

        var service = createService(config);
        setupDatabase(true);

        // port registry is empty / 0 port from context
        assertEchoCase(clientHost + ":" + 0, service, new SocketAddress(serverHost, 0));

        // port registry is empty / no port from context
        assertEchoCase(clientHost, service, new SocketAddress(clientHost, BoltConnector.DEFAULT_PORT));

        // port registry is empty / port from context
        assertEchoCase(clientHost + ":" + clientPort, service, new SocketAddress(clientHost, clientPort));

        // when
        portRegister.register(ConnectorType.BOLT, new SocketAddress("ignored", serverPort));

        // port registry has value / 0 port from context
        assertEchoCase(clientHost + ":" + 0, service, new SocketAddress(serverHost, serverPort));

        // port registry has value / no port from context
        assertEchoCase(clientHost, service, new SocketAddress(clientHost, BoltConnector.DEFAULT_PORT));

        // port registry has value / port from context
        assertEchoCase(clientHost + ":" + clientPort, service, new SocketAddress(clientHost, clientPort));
    }

    private CommunityRoutingService createService(Config config) {
        return new CommunityRoutingService(databaseContextProvider, defaultDatabaseResolver, portRegister, config);
    }

    private void assertEchoCase(String providedAddress, CommunityRoutingService service, SocketAddress exceptedAddress)
            throws RoutingException {
        // when
        var context = VirtualValues.map(
                new String[] {RoutingTableServiceHelpers.ADDRESS_CONTEXT_KEY},
                new AnyValue[] {Values.utf8Value(providedAddress)});
        var result = service.route(dbName, null, context, false);

        // then
        assertAddress(result, exceptedAddress);
    }

    private void setupDatabase(boolean available) {
        var context = mock(DatabaseContext.class);
        var database = mock(Database.class);
        var availabilityGuard = mock(DatabaseAvailabilityGuard.class);
        when(availabilityGuard.isAvailable()).thenReturn(available);
        when(database.getDatabaseAvailabilityGuard()).thenReturn(availabilityGuard);
        when(context.database()).thenReturn(database);
        when(databaseContextProvider.getDatabaseContext(dbName)).thenReturn(Optional.of(context));
    }

    private static void assertAddress(RoutingResult result, SocketAddress socketAddress) {
        assertThat(result.writeEndpoints()).containsExactly(socketAddress);
        assertThat(result.readEndpoints()).containsExactly(socketAddress);
        assertThat(result.routeEndpoints()).containsExactly(socketAddress);
    }
}

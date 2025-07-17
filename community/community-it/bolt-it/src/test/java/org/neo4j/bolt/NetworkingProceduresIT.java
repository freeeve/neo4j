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
package org.neo4j.bolt;

import static org.assertj.core.data.Index.atIndex;
import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertThat;
import static org.neo4j.values.storable.Values.stringValue;

import java.util.concurrent.atomic.AtomicReference;
import org.neo4j.bolt.test.annotation.BoltTestExtension;
import org.neo4j.bolt.test.annotation.connection.initializer.Authenticated;
import org.neo4j.bolt.test.annotation.test.ProtocolTest;
import org.neo4j.bolt.test.provider.ConnectionProvider;
import org.neo4j.bolt.testing.assertions.AnyValueAssertions;
import org.neo4j.bolt.testing.assertions.ListValueAssertions;
import org.neo4j.bolt.testing.client.BoltTestConnection;
import org.neo4j.bolt.testing.messages.BoltWire;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.VirtualValues;

/**
 * Test procedures related to Networking
 */
@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
@BoltTestExtension
public class NetworkingProceduresIT {

    @ProtocolTest
    void shouldKillConnection(
            BoltWire wire, @Authenticated BoltTestConnection connection, @Authenticated ConnectionProvider provider) {
        var connectionId = getConnectionIdFromSingleConnection(wire, connection);

        try (var otherConnection = provider.create()) {
            otherConnection
                    .send(wire.run(
                            "CALL dbms.killConnection($id)",
                            VirtualValues.map(new String[] {"id"}, new AnyValue[] {connectionId})))
                    .send(wire.pull());

            assertThat(otherConnection)
                    .receivesSuccess()
                    .receivesRecord(fields -> ListValueAssertions.assertThat(fields)
                            .hasSize(3)
                            .satisfies(
                                    element -> AnyValueAssertions.assertThat(element)
                                            .isNotNull(),
                                    atIndex(0))
                            .contains(connectionId, atIndex(0))
                            .contains(stringValue("Connection found"), atIndex(2)))
                    .receivesSuccess();
        }

        assertThat(connection).isEventuallyTerminated();
    }

    @ProtocolTest
    void shouldKillConnectionHandleUnexistingConnections(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var connectionId = Values.stringValue("unknown");

        connection
                .send(wire.run(
                        "CALL dbms.killConnection($id)",
                        VirtualValues.map(new String[] {"id"}, new AnyValue[] {connectionId})))
                .send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .receivesRecord(fields -> ListValueAssertions.assertThat(fields)
                        .hasSize(3)
                        .satisfies(
                                element ->
                                        AnyValueAssertions.assertThat(element).isNotNull(),
                                atIndex(0))
                        .contains(connectionId, atIndex(0))
                        .contains(stringValue("No connection found with this id"), atIndex(2)))
                .receivesSuccess();
    }

    private static AnyValue getConnectionIdFromSingleConnection(BoltWire wire, BoltTestConnection connection) {
        var connectionId = new AtomicReference<AnyValue>();

        connection.send(wire.run("CALL dbms.listConnections()")).send(wire.pull());
        assertThat(connection)
                .receivesSuccess()
                .receivesRecord(fields -> ListValueAssertions.assertThat(fields)
                        .hasSize(7)
                        .satisfies(element -> connectionId.set(element.getFirst())))
                .receivesSuccess();
        return connectionId.get();
    }
}

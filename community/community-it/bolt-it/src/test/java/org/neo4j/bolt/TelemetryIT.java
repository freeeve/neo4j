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

import static org.neo4j.bolt.testing.util.ErrorUtil.useNewMessage;

import java.util.Map;
import org.neo4j.bolt.test.annotation.BoltTestExtension;
import org.neo4j.bolt.test.annotation.connection.initializer.Authenticated;
import org.neo4j.bolt.test.annotation.connection.initializer.Negotiated;
import org.neo4j.bolt.test.annotation.connection.initializer.VersionSelected;
import org.neo4j.bolt.test.annotation.setup.SettingsFunction;
import org.neo4j.bolt.test.annotation.test.ProtocolTest;
import org.neo4j.bolt.test.annotation.wire.selector.IncludeWire;
import org.neo4j.bolt.testing.annotation.Version;
import org.neo4j.bolt.testing.assertions.BoltConnectionAssertions;
import org.neo4j.bolt.testing.client.BoltTestConnection;
import org.neo4j.bolt.testing.messages.BoltWire;
import org.neo4j.bolt.testing.messages.factory.TelemetryMessageBuilder;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;

@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
@BoltTestExtension
public class TelemetryIT {

    @SettingsFunction
    static void customizeSettings(Map<Setting<?>, Object> settings) {
        settings.put(BoltConnector.server_bolt_telemetry_enabled, true);
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 3))
    void shouldFailToProcessTelemetryWhenOldBoltVersion(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection).isEventuallyTerminated();
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4))
    void shouldProcessTelemetry(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute))
                .send(wire.telemetry(TelemetryMessageBuilder::withUnmanagedTransactions))
                .send(wire.telemetry(TelemetryMessageBuilder::withManagedTransactionFunctions))
                .send(wire.telemetry(TelemetryMessageBuilder::withImplicitTransactions));

        BoltConnectionAssertions.assertThat(connection).receivesSuccess(4);
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4))
    void shouldNotProcessTelemetryWhenFailed(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.run("✨✨✨ FIRE ✨✨✨"));
        connection
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute))
                .send(wire.telemetry(TelemetryMessageBuilder::withUnmanagedTransactions))
                .send(wire.telemetry(TelemetryMessageBuilder::withManagedTransactionFunctions))
                .send(wire.telemetry(TelemetryMessageBuilder::withImplicitTransactions))
                .send(wire.pull());

        BoltConnectionAssertions.assertThat(connection).receivesFailure().receivesIgnored(5);
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4), until = @Version(major = 5, minor = 6))
    void shouldFailWhenTelemetryIsReceivedPriorToNegotiationV40(
            @VersionSelected BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the NEGOTIATION state.");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenTelemetryIsReceivedPriorToNegotiationV5x7(
            @VersionSelected BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the NEGOTIATION state.",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'NEGOTIATION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'NEGOTIATION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenTelemetryIsReceivedPriorToNegotiation(
            @VersionSelected BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Message of type TelemetryMessage cannot be handled by a session in the NEGOTIATION state."),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'NEGOTIATION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'NEGOTIATION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4), until = @Version(major = 5, minor = 6))
    void shouldFailWhenTelemetryIsReceivedPriorToAuthenticationV40(
            @Negotiated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the AUTHENTICATION state.");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenTelemetryIsReceivedPriorToAuthenticationV5x7(
            @Negotiated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the AUTHENTICATION state.",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenTelemetryIsReceivedPriorToAuthentication(
            @Negotiated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Message of type TelemetryMessage cannot be handled by a session in the AUTHENTICATION state."),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4), until = @Version(major = 5, minor = 6))
    void shouldFailWhenTelemetryIsInTxReadyV40(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.begin());
        BoltConnectionAssertions.assertThat(connection).receivesSuccess();

        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the IN_TRANSACTION state.");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenTelemetryIsInTxReadyV5x7(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.begin());
        BoltConnectionAssertions.assertThat(connection).receivesSuccess();

        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the IN_TRANSACTION state.",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'IN_TRANSACTION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'IN_TRANSACTION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenTelemetryIsInTxReady(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.begin());
        BoltConnectionAssertions.assertThat(connection).receivesSuccess();

        connection.send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Message of type TelemetryMessage cannot be handled by a session in the IN_TRANSACTION state."),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'IN_TRANSACTION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'IN_TRANSACTION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4), until = @Version(major = 5, minor = 6))
    void shouldFailWhenTelemetryIsReceivedAfterLogoffV40(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute))
                .send(wire.logoff())
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesSuccess(2)
                .receivesFailureV40(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the AUTHENTICATION state.");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenTelemetryIsReceivedAfterLogoffV5x7(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute))
                .send(wire.logoff())
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesSuccess(2)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Message of type TelemetryMessage cannot be handled by a session in the AUTHENTICATION state.",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenTelemetryIsReceivedAfterLogoff(@Authenticated BoltTestConnection connection, BoltWire wire) {
        connection
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute))
                .send(wire.logoff())
                .send(wire.telemetry(TelemetryMessageBuilder::withExecute));

        BoltConnectionAssertions.assertThat(connection)
                .receivesSuccess(2)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Message of type TelemetryMessage cannot be handled by a session in the AUTHENTICATION state."),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "08N10: Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                                "error: connection exception - invalid server state. Message TelemetryMessage cannot be handled by session in the 'AUTHENTICATION' state.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 4))
    void shouldCloseConnectionWhenANonSupportedApiTypeIsSent(
            @Authenticated BoltTestConnection connection, BoltWire wire) {
        connection.send(wire.telemetry(TelemetryMessageBuilder::withANonValidAPIType));

        BoltConnectionAssertions.assertThat(connection).receivesFailure();
    }
}

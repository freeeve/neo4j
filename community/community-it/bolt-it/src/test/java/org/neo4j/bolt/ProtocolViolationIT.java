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

import static org.neo4j.bolt.test.util.ErrorUtil.useNewMessage;
import static org.neo4j.values.storable.Values.longValue;
import static org.neo4j.values.storable.Values.stringValue;

import java.util.List;
import java.util.function.Consumer;
import org.neo4j.bolt.protocol.io.StructType;
import org.neo4j.bolt.test.annotation.BoltTestExtension;
import org.neo4j.bolt.test.annotation.connection.initializer.Authenticated;
import org.neo4j.bolt.test.annotation.test.ProtocolTest;
import org.neo4j.bolt.test.annotation.wire.selector.IncludeWire;
import org.neo4j.bolt.testing.annotation.Version;
import org.neo4j.bolt.testing.assertions.BoltConnectionAssertions;
import org.neo4j.bolt.testing.client.BoltTestConnection;
import org.neo4j.bolt.testing.messages.BoltV40Wire;
import org.neo4j.bolt.testing.messages.BoltWire;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.packstream.io.PackstreamBuf;
import org.neo4j.packstream.struct.StructHeader;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;
import org.neo4j.values.virtual.MapValueBuilder;

@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
@BoltTestExtension
public class ProtocolViolationIT {

    private static void sendRun(BoltTestConnection connection, Consumer<PackstreamBuf> packer) {
        var buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(3, BoltV40Wire.MESSAGE_TAG_RUN))
                .writeString("RETURN $x") // statement
                .writeMapHeader(1) // parameters
                .writeString("x");

        packer.accept(buf);

        connection.send(buf.writeMapHeader(0) // extra
                .raw());
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenNullKeyIsSentV40(@Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> buf.writeMapHeader(1).writeNull().writeString("foo"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected type: Expected STRING but got NONE");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenNullKeyIsSentV57(@Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> buf.writeMapHeader(1).writeNull().writeString("foo"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected type: Expected STRING but got NONE",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22G03",
                                GqlStatusInfoCodes.STATUS_22G03.getGqlStatus(),
                                "error: data exception - invalid value type",
                                // 22G03 has UNKNOWN classification, no parameters and no position, so no diagnostic
                                // record is sent over Bolt.
                                // Instead a default diagnostic record is created on driver side.
                                null,
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N01: Expected the value 192 to be of type STRING, but was of type NONE.",
                                        GqlStatusInfoCodes.STATUS_22N01.getGqlStatus(),
                                        "error: data exception - invalid type. Expected the value 192 to be of type STRING, but was of type NONE.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenNullKeyIsSent(@Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> buf.writeMapHeader(1).writeNull().writeString("foo"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Illegal value for field \"params\": Unexpected type: Expected STRING but got NONE"),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22G03",
                                GqlStatusInfoCodes.STATUS_22G03.getGqlStatus(),
                                "error: data exception - invalid value type",
                                // 22G03 has UNKNOWN classification, no parameters and no position, so no diagnostic
                                // record is sent over Bolt.
                                // Instead a default diagnostic record is created on driver side.
                                null,
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N01: Expected the value 192 to be of type STRING, but was of type NONE.",
                                        GqlStatusInfoCodes.STATUS_22N01.getGqlStatus(),
                                        "error: data exception - invalid type. Expected the value 192 to be of type STRING, but was of type NONE.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenDuplicateKeyIsSentV40(@Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> buf.writeMapHeader(2)
                .writeString("foo")
                .writeString("bar")
                .writeString("foo")
                .writeString("changed_my_mind"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid, "Illegal value for field \"params\": Duplicate map key: \"foo\"");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenDuplicateKeyIsSentV5x7(@Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> buf.writeMapHeader(2)
                .writeString("foo")
                .writeString("bar")
                .writeString("foo")
                .writeString("changed_my_mind"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Duplicate map key: \"foo\"",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "22N54: Multiple conflicting entries specified for 'foo'.",
                                GqlStatusInfoCodes.STATUS_22N54.getGqlStatus(),
                                "error: data exception - invalid map. Multiple conflicting entries specified for 'foo'.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenDuplicateKeyIsSent(@Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> buf.writeMapHeader(2)
                .writeString("foo")
                .writeString("bar")
                .writeString("foo")
                .writeString("changed_my_mind"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo("Illegal value for field \"params\": Duplicate map key: \"foo\""),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCause(
                                "22N54: Multiple conflicting entries specified for 'foo'.",
                                GqlStatusInfoCodes.STATUS_22N54.getGqlStatus(),
                                "error: data exception - invalid map. Multiple conflicting entries specified for 'foo'.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenNodeIsSentWithRunV40(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var properties = new MapValueBuilder();
        properties.add("the_answer", longValue(42));
        properties.add("one_does_not_simply", stringValue("break_decoding"));

        sendRun(connection, buf -> wire.nodeValue(buf, "42", 42, List.of("Broken", "Dreams")));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid, "Illegal value for field \"params\": Unexpected struct tag: 0x4E");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenNodeIsSentWithRunV5x7(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var properties = new MapValueBuilder();
        properties.add("the_answer", longValue(42));
        properties.add("one_does_not_simply", stringValue("break_decoding"));

        sendRun(connection, buf -> wire.nodeValue(buf, "42", 42, List.of("Broken", "Dreams")));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected struct tag: 0x4E",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22N00: The provided value is unsupported and cannot be processed.",
                                GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                                "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N97: Unexpected struct tag: 0x4E.",
                                        GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                        "error: data exception - unexpected struct tag. Unexpected struct tag: 0x4E.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenNodeIsSentWithRun(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var properties = new MapValueBuilder();
        properties.add("the_answer", longValue(42));
        properties.add("one_does_not_simply", stringValue("break_decoding"));

        sendRun(connection, buf -> wire.nodeValue(buf, "42", 42, List.of("Broken", "Dreams")));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Illegal value for field \"params\": Unexpected struct tag: 0x4E"),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22N00: The provided value is unsupported and cannot be processed.",
                                GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                                "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N97: Unexpected struct tag: 0x4E.",
                                        GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                        "error: data exception - unexpected struct tag. Unexpected struct tag: 0x4E.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenRelationshipIsSentWithRunV40(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var properties = new MapValueBuilder();
        properties.add("the_answer", longValue(42));
        properties.add("one_does_not_simply", stringValue("break_decoding"));

        sendRun(connection, buf -> wire.relationshipValue(buf, "42", 42, "21", 21, "84", 84, "RUINS_EXPECTATIONS"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid, "Illegal value for field \"params\": Unexpected struct tag: 0x52");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenRelationshipIsSentWithRunV5x7(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var properties = new MapValueBuilder();
        properties.add("the_answer", longValue(42));
        properties.add("one_does_not_simply", stringValue("break_decoding"));

        sendRun(connection, buf -> wire.relationshipValue(buf, "42", 42, "21", 21, "84", 84, "RUINS_EXPECTATIONS"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected struct tag: 0x52",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22N00: The provided value is unsupported and cannot be processed.",
                                GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                                "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N97: Unexpected struct tag: 0x52.",
                                        GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                        "error: data exception - unexpected struct tag. Unexpected struct tag: 0x52.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenRelationshipIsSentWithRun(BoltWire wire, @Authenticated BoltTestConnection connection) {
        var properties = new MapValueBuilder();
        properties.add("the_answer", longValue(42));
        properties.add("one_does_not_simply", stringValue("break_decoding"));

        sendRun(connection, buf -> wire.relationshipValue(buf, "42", 42, "21", 21, "84", 84, "RUINS_EXPECTATIONS"));

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Illegal value for field \"params\": Unexpected struct tag: 0x52"),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22N00: The provided value is unsupported and cannot be processed.",
                                GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                                "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N97: Unexpected struct tag: 0x52.",
                                        GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                        "error: data exception - unexpected struct tag. Unexpected struct tag: 0x52.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenPathIsSentWithRunV40(BoltWire wire, @Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> {
            buf.writeStructHeader(new StructHeader(3, StructType.PATH.getTag()));

            buf.writeListHeader(2);
            wire.nodeValue(buf, "42", 42, List.of("Computer"));
            wire.nodeValue(buf, "84", 84, List.of("Vendor"));

            buf.writeListHeader(1);
            wire.unboundRelationshipValue(buf, "13", 13, "MAKES");

            buf.writeListHeader(2);
            buf.writeInt(1);
            buf.writeInt(1);
        });

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureV40(
                        Status.Request.Invalid, "Illegal value for field \"params\": Unexpected struct tag: 0x50");

        connection.send(wire.reset());

        BoltConnectionAssertions.assertThat(connection).receivesSuccess();
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenPathIsSentWithRunV5x7(BoltWire wire, @Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> {
            buf.writeStructHeader(new StructHeader(3, StructType.PATH.getTag()));

            buf.writeListHeader(2);
            wire.nodeValue(buf, "42", 42, List.of("Computer"));
            wire.nodeValue(buf, "84", 84, List.of("Vendor"));

            buf.writeListHeader(1);
            wire.unboundRelationshipValue(buf, "13", 13, "MAKES");

            buf.writeListHeader(2);
            buf.writeInt(1);
            buf.writeInt(1);
        });

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected struct tag: 0x50",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22N00: The provided value is unsupported and cannot be processed.",
                                GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                                "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N97: Unexpected struct tag: 0x50.",
                                        GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                        "error: data exception - unexpected struct tag. Unexpected struct tag: 0x50.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));

        connection.send(wire.reset());

        BoltConnectionAssertions.assertThat(connection).receivesSuccess();
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenPathIsSentWithRun(BoltWire wire, @Authenticated BoltTestConnection connection) {
        sendRun(connection, buf -> {
            buf.writeStructHeader(new StructHeader(3, StructType.PATH.getTag()));

            buf.writeListHeader(2);
            wire.nodeValue(buf, "42", 42, List.of("Computer"));
            wire.nodeValue(buf, "84", 84, List.of("Vendor"));

            buf.writeListHeader(1);
            wire.unboundRelationshipValue(buf, "13", 13, "MAKES");

            buf.writeListHeader(2);
            buf.writeInt(1);
            buf.writeInt(1);
        });

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo(
                                        "Illegal value for field \"params\": Unexpected struct tag: 0x50"),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        BoltConnectionAssertions.assertErrorCauseWithInnerCause(
                                "22N00: The provided value is unsupported and cannot be processed.",
                                GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                                "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                                BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                BoltConnectionAssertions.assertErrorCause(
                                        "22N97: Unexpected struct tag: 0x50.",
                                        GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                        "error: data exception - unexpected struct tag. Unexpected struct tag: 0x50.",
                                        BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord(
                                                "CLIENT_ERROR"))));

        connection.send(wire.reset());

        BoltConnectionAssertions.assertThat(connection).receivesSuccess();
    }

    @ProtocolTest
    void shouldTerminateConnectionWhenUnknownMessageIsSent(@Authenticated BoltTestConnection connection) {
        connection.send(PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(1, (short) 0x42))
                .writeListHeader(1)
                .writeInt(42));

        BoltConnectionAssertions.assertThat(connection).isEventuallyTerminated();
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenTryToStartTransactionInFailedStateV40(@Authenticated BoltTestConnection connection) {
        // Failing
        var buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(3, BoltV40Wire.MESSAGE_TAG_RUN))
                .writeString("RETURN $x") // statement
                .writeMapHeader(1) // parameters
                .writeString("foo")
                .writeString("bar");
        connection.send(buf.writeMapHeader(0) // extra
                .raw());

        BoltConnectionAssertions.assertThat(connection).receivesFailure();

        // Sending the begin message
        buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(1, BoltV40Wire.MESSAGE_TAG_BEGIN))
                .writeMapHeader(0) // metadata
        ;
        connection.send(buf.writeMapHeader(0) // extra
                .raw());

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureFuzzyV40(
                        Status.Request.Invalid,
                        // we need to fuzzy check since the string representation
                        // of the begin message change in different protocol versions
                        "cannot be handled by session in the READY state");
        ;
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenTryToStartTransactionInFailedStateV5x7(@Authenticated BoltTestConnection connection) {
        // Failing
        var buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(3, BoltV40Wire.MESSAGE_TAG_RUN))
                .writeString("RETURN $x") // statement
                .writeMapHeader(1) // parameters
                .writeString("foo")
                .writeString("bar");
        connection.send(buf.writeMapHeader(0) // extra
                .raw());

        BoltConnectionAssertions.assertThat(connection).receivesFailure();

        // Sending the begin message
        buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(1, BoltV40Wire.MESSAGE_TAG_BEGIN))
                .writeMapHeader(0) // metadata
        ;
        connection.send(buf.writeMapHeader(0) // extra
                .raw());

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureFuzzy(
                        Status.Request.Invalid,
                        // we need to fuzzy check since the string representation
                        // of the begin message change in different protocol versions
                        "cannot be handled by session in the READY state",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                        "error: connection exception - invalid server state. Message BeginMessage{bookmarks=[], txTimeout=null, accessMode=WRITE, txMetadata=null, databaseName='null', impersonatedUser='null', notificationsConfig=Default, type=EXPLICIT} cannot be handled by session in the 'READY' state.");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldFailWhenTryToStartTransactionInFailedState(@Authenticated BoltTestConnection connection) {
        // Failing
        var buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(3, BoltV40Wire.MESSAGE_TAG_RUN))
                .writeString("RETURN $x") // statement
                .writeMapHeader(1) // parameters
                .writeString("foo")
                .writeString("bar");
        connection.send(buf.writeMapHeader(0) // extra
                .raw());

        BoltConnectionAssertions.assertThat(connection).receivesFailure();

        // Sending the begin message
        buf = PackstreamBuf.allocUnpooled()
                .writeStructHeader(new StructHeader(1, BoltV40Wire.MESSAGE_TAG_BEGIN))
                .writeMapHeader(0) // metadata
        ;
        connection.send(buf.writeMapHeader(0) // extra
                .raw());

        BoltConnectionAssertions.assertThat(connection)
                .receivesFailureFuzzy(
                        Status.Request.Invalid,
                        // we need to fuzzy check since the string representation
                        // of the begin message change in different protocol versions
                        useNewMessage("08N06: General network protocol error.")
                                .whenLegacyFallbackTo("cannot be handled by session in the READY state"),
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        GqlStatusInfoCodes.STATUS_08N10.getGqlStatus(),
                        "error: connection exception - invalid server state. Message BeginMessage{bookmarks=[], txTimeout=null, accessMode=WRITE, txMetadata=null, databaseName='null', impersonatedUser='null', notificationsConfig=Default, type=EXPLICIT} cannot be handled by session in the 'READY' state.");
    }
}

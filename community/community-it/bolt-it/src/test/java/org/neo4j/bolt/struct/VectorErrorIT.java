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
package org.neo4j.bolt.struct;

import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertErrorCause;
import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertErrorCauseWithInnerCause;
import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertErrorClassificationOnDiagnosticRecord;
import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertThat;

import io.netty.buffer.Unpooled;
import java.util.Map;
import org.neo4j.bolt.protocol.io.StructType;
import org.neo4j.bolt.test.annotation.BoltTestExtension;
import org.neo4j.bolt.test.annotation.connection.initializer.Authenticated;
import org.neo4j.bolt.test.annotation.setup.SettingsFunction;
import org.neo4j.bolt.test.annotation.test.ProtocolTest;
import org.neo4j.bolt.test.annotation.wire.selector.IncludeWire;
import org.neo4j.bolt.testing.annotation.Version;
import org.neo4j.bolt.testing.client.BoltTestConnection;
import org.neo4j.bolt.testing.messages.BoltWire;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.packstream.io.TypeMarker;
import org.neo4j.packstream.struct.StructHeader;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;

@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
@BoltTestExtension
public class VectorErrorIT extends AbstractStructArgumentIT {

    @SettingsFunction
    static void customizeSettings(Map<Setting<?>, Object> settings) {
        settings.put(GraphDatabaseInternalSettings.cypher_enable_vector_type, Boolean.TRUE);
        settings.put(GraphDatabaseSettings.default_language, GraphDatabaseSettings.CypherVersion.Cypher25);
    }

    @ProtocolTest
    @IncludeWire(until = @Version(major = 5, minor = 6))
    void shouldFailWhenVectorSentOnIncompatibleProtocol(@Authenticated BoltTestConnection connection) {
        testFailureWithUnknownStructV40(
                connection,
                buf -> buf.writeStructHeader(new StructHeader(2, StructType.VECTOR.getTag()))
                        .writeString("boom"),
                "Illegal value for field \"params\": Unexpected struct tag: 0x56");
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenVectorSentOnIncompatibleProtocolGql(@Authenticated BoltTestConnection connection) {
        testFailureWithUnknownStruct(
                connection,
                buf -> buf.writeStructHeader(new StructHeader(2, StructType.VECTOR.getTag()))
                        .writeString("boom"),
                "Illegal value for field \"params\": Unexpected struct tag: 0x56",
                assertErrorCauseWithInnerCause(
                        "22N00: The provided value is unsupported and cannot be processed.",
                        GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                        "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        assertErrorCause(
                                "22N97: Unexpected struct tag: 0x56.",
                                GqlStatusInfoCodes.STATUS_22N97.getGqlStatus(),
                                "error: data exception - unexpected struct tag. Unexpected struct tag: 0x56.",
                                assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 5, minor = 7), until = @Version(major = 5, minor = 8))
    void shouldFailWhenVectorReceivedOnIncompatibleProtocolGql(
            BoltWire wire, @Authenticated BoltTestConnection connection) {
        connection.send(wire.run("RETURN VECTOR([1, 2, 3], 3, INTEGER64)"));
        connection.send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Struct tag: 0x56 representing type VECTOR is not supported for this protocol version",
                        GqlStatusInfoCodes.STATUS_22N00.getGqlStatus(),
                        "error: data exception - unsupported value. The provided value is unsupported and cannot be processed.",
                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        assertErrorCause(
                                "22NBD: Unsupported struct tag: 0x56.",
                                GqlStatusInfoCodes.STATUS_22NBD.getGqlStatus(),
                                "error: data exception - unsupported struct tag. Unsupported struct tag: 0x56.",
                                assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldBeSuccessfulForValidStruct(@Authenticated BoltTestConnection connection) {
        connection.send(createRunWith(buf -> {
            buf.writeStructHeader(new StructHeader(2, StructType.VECTOR.getTag()));
            buf.writeBytes(Unpooled.buffer().writeByte(TypeMarker.INT8.getValue()));
            buf.writeBytes(Unpooled.buffer().writeBytes(new byte[] {1, 2, 3}));
        }));

        assertThat(connection).receivesSuccess();
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldErrorForInvalidVectorType(@Authenticated BoltTestConnection connection) {
        connection.send(createRunWith(buf -> {
            buf.writeStructHeader(new StructHeader(2, StructType.VECTOR.getTag()));
            buf.writeBytes(Unpooled.buffer().writeByte(TypeMarker.MAP8.getValue()));
            buf.writeBytes(Unpooled.buffer().writeBytes(new byte[] {1, 2, 3}));
        }));

        assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Illegal value for field \"type\":"
                                + " Given type is not a valid vector value type",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        assertErrorCauseWithInnerCause(
                                "08N06: General network protocol error.",
                                GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                                "error: connection exception - protocol error. General network protocol error.",
                                assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                                assertErrorCauseWithInnerCause(
                                        "22G03",
                                        GqlStatusInfoCodes.STATUS_22G03.getGqlStatus(),
                                        "error: data exception - invalid value type",
                                        null,
                                        assertErrorCause(
                                                "22N01: Expected the value 216 to be of type INT8, "
                                                        + "INT16, INT32, INT64, FLOAT32 or FLOAT64, but was of type MAP8.",
                                                GqlStatusInfoCodes.STATUS_22N01.getGqlStatus(),
                                                "error: data exception - invalid type. Expected the value"
                                                        + " 216 to be of type INT8, INT16, INT32, INT64, FLOAT32 or FLOAT64, "
                                                        + "but was of type MAP8.",
                                                assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR")))));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldErrorForInvalidTypeMarkerType(@Authenticated BoltTestConnection connection) {
        connection.send(createRunWith(buf -> {
            buf.writeStructHeader(new StructHeader(2, StructType.VECTOR.getTag()));
            buf.writeInt64(Long.MAX_VALUE);
        }));

        assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected type: Expected BYTES but got INT",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        assertErrorCauseWithInnerCause(
                                "22G03",
                                GqlStatusInfoCodes.STATUS_22G03.getGqlStatus(),
                                "error: data exception - invalid value type",
                                null,
                                assertErrorCause(
                                        "22N01: Expected the value 203 to be of type BYTES, but was of type INT.",
                                        GqlStatusInfoCodes.STATUS_22N01.getGqlStatus(),
                                        "error: data exception - invalid type. Expected the value 203 "
                                                + "to be of type BYTES, but was of type INT.",
                                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"))));
    }

    @ProtocolTest
    @IncludeWire(since = @Version(major = 6, minor = 0))
    void shouldErrorForInvalidVectorContents(@Authenticated BoltTestConnection connection) {
        connection.send(createRunWith(buf -> {
            buf.writeStructHeader(new StructHeader(2, StructType.VECTOR.getTag()));
            buf.writeBytes(Unpooled.buffer().writeByte(TypeMarker.INT8.getValue()));
            buf.writeInt64(Long.MAX_VALUE);
        }));

        assertThat(connection)
                .receivesFailureWithCause(
                        Status.Request.Invalid,
                        "Illegal value for field \"params\": Unexpected type: Expected BYTES but got INT",
                        GqlStatusInfoCodes.STATUS_08N06.getGqlStatus(),
                        "error: connection exception - protocol error. General network protocol error.",
                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"),
                        assertErrorCauseWithInnerCause(
                                "22G03",
                                GqlStatusInfoCodes.STATUS_22G03.getGqlStatus(),
                                "error: data exception - invalid value type",
                                null,
                                assertErrorCause(
                                        "22N01: Expected the value 203 to be of type BYTES, " + "but was of type INT.",
                                        GqlStatusInfoCodes.STATUS_22N01.getGqlStatus(),
                                        "error: data exception - invalid type. Expected the value 203 "
                                                + "to be of type BYTES, but was of type INT.",
                                        assertErrorClassificationOnDiagnosticRecord("CLIENT_ERROR"))));
    }
}

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
package org.neo4j.bolt.protocol.common.message.encoder;

import java.util.HashMap;
import java.util.Map;
import org.neo4j.bolt.negotiation.message.ProtocolCapability;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.boltmessages.response.FailureMessage;
import org.neo4j.boltmessages.response.FailureMetadata;
import org.neo4j.gqlstatus.DiagnosticRecord;
import org.neo4j.packstream.io.PackstreamBuf;
import org.neo4j.packstream.struct.StructWriter;

public final class FailureMessageEncoder implements StructWriter<Connection, FailureMessage> {
    private static final FailureMessageEncoder INSTANCE = new FailureMessageEncoder();

    public static final Map<String, Object> DEFAULT_DIAGNOSTIC_RECORD = Map.of(
            "OPERATION",
            "",
            "OPERATION_CODE",
            "0",
            "CURRENT_SCHEMA",
            "/",
            "_position",
            Map.of(
                    "offset", -1,
                    "line", -1,
                    "column", -1));

    private FailureMessageEncoder() {}

    public static FailureMessageEncoder getInstance() {
        return INSTANCE;
    }

    @Override
    public Class<FailureMessage> getType() {
        return FailureMessage.class;
    }

    @Override
    public short getTag(FailureMessage payload) {
        return FailureMessage.SIGNATURE;
    }

    @Override
    public long getLength(FailureMessage payload) {
        return 1;
    }

    @Override
    public void write(Connection ctx, PackstreamBuf buffer, FailureMessage payload) {
        writeMetadata(ctx, buffer, payload.metadata(), false);
    }

    private void writeMetadata(Connection ctx, PackstreamBuf buffer, FailureMetadata payload, boolean isCause) {
        var diagnosticRecord = getDiagnosticRecord(ctx, payload, isCause);

        var size = payload.cause() != null ? 6 : 5;
        if (diagnosticRecord.isEmpty()) {
            size--;
        }

        if (isCause) {
            size--;
        }

        buffer.writeMapHeader(size);

        if (!isCause) {
            buffer.writeString("neo4j_code");
            buffer.writeString(payload.status().code().serialize());
        }

        buffer.writeString("message");
        buffer.writeString(payload.message());

        buffer.writeString("gql_status");
        buffer.writeString(payload.gqlStatus());

        buffer.writeString("description");
        buffer.writeString(payload.description());

        if (!diagnosticRecord.isEmpty()) {
            buffer.writeString("diagnostic_record");
            buffer.writeMap(diagnosticRecord);
        }

        if (payload.cause() != null) {
            buffer.writeString("cause");
            this.writeMetadata(ctx, buffer, payload.cause(), true);
        }
    }

    private static Map<String, Object> getDiagnosticRecord(Connection ctx, FailureMetadata payload, boolean isCause) {
        var diagnosticRecord = new HashMap<String, Object>();

        for (Map.Entry<String, Object> entry : payload.diagnosticRecord().entrySet()) {
            if (!isDefaultDiagnosticRecordEntry(entry)) {
                diagnosticRecord.put(entry.getKey(), entry.getValue());
            }
        }

        if (!isCause && ctx.hasSelectedProtocolCapability(ProtocolCapability.FABRIC)) {
            diagnosticRecord.put(DiagnosticRecord.LEGACY_MESSAGE_KEY, payload.legacyMessage());
        }

        return diagnosticRecord;
    }

    private static boolean isDefaultDiagnosticRecordEntry(Map.Entry<String, Object> entry) {
        return DEFAULT_DIAGNOSTIC_RECORD.containsKey(entry.getKey())
                && DEFAULT_DIAGNOSTIC_RECORD.get(entry.getKey()).equals(entry.getValue());
    }
}

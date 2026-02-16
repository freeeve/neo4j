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
package org.neo4j.internal.indexcommand;

import static org.neo4j.internal.indexcommand.encode.PositiveNumberEncoder.readNumber;
import static org.neo4j.internal.indexcommand.encode.PositiveNumberEncoder.writeNumber;

import java.io.IOException;
import org.neo4j.common.EntityType;
import org.neo4j.internal.indexcommand.encode.PeekableChannel;
import org.neo4j.internal.indexcommand.encode.ValueStream;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.values.storable.Value;

public class IndexCommandSerializationImplV1 implements IndexCommandSerializationImpl {

    @Override
    public void writeCommand(WritableChannel channel, IndexUpdateCommand<?> command) throws IOException {
        write(channel, command);
    }

    @Override
    public IndexUpdateCommand<?> readCommand(IndexCommandSerialization serialization, ReadableChannel channel)
            throws IOException {
        return read(serialization, channel);
    }

    private static void write(WritableChannel out, IndexUpdateCommand<?> command) throws IOException {
        boolean tokenIndex = isTokenIndex(command);
        writeHeader(out, command, tokenIndex);
        writeNumber(out, command.getIndexId());
        writeEntityId(out, command.getEntityId());
        if (tokenIndex) {
            writeTokenPart(out, (TokenIndexUpdateCommand) command);
        } else {
            writeValuePart(out, (ValueIndexUpdateCommand) command);
        }
    }

    private static IndexUpdateCommand<?> read(IndexCommandSerialization serialization, ReadableChannel in)
            throws IOException {
        byte header = in.get();
        boolean tokenIndex = isTokenIndex(header);
        UpdateMode updateMode = readUpdateMode(header);
        long indexId = readNumber(in);
        long entityId = readEntityId(in);

        if (tokenIndex) {
            return readTokenPart(serialization, in, updateMode, indexId, entityId);
        }
        return readValuePart(serialization, in, updateMode, indexId, entityId);
    }

    private static boolean isTokenIndex(byte header) {
        return (header & (1 << 7)) == 0;
    }

    private static UpdateMode readUpdateMode(byte header) {
        int modeOrdinal = (header >> 5) & 3;
        return switch (modeOrdinal) {
            case 0 -> UpdateMode.ADDED;
            case 1 -> UpdateMode.CHANGED;
            case 2 -> UpdateMode.REMOVED;
            default -> throw new IllegalArgumentException("Weird header: " + header);
        };
    }

    private static void writeValuePart(WritableChannel out, ValueIndexUpdateCommand command) throws IOException {
        switch (command.getUpdateMode()) {
            case ADDED, REMOVED -> writeValueArray(out, command.getAfter());
            case CHANGED -> {
                writeValueArray(out, command.getBefore());
                writeValueArray(out, command.getAfter());
            }
        }
    }

    private static ValueIndexUpdateCommand readValuePart(
            IndexCommandSerialization serialization,
            ReadableChannel in,
            UpdateMode updateMode,
            long indexId,
            long entityId)
            throws IOException {
        return switch (updateMode) {
            case ADDED, REMOVED -> {
                Value[] values = readValueArray(in);
                yield new ValueIndexUpdateCommand(serialization, updateMode, indexId, entityId, null, values);
            }
            case CHANGED -> {
                Value[] before = readValueArray(in);
                Value[] values = readValueArray(in);
                yield new ValueIndexUpdateCommand(serialization, updateMode, indexId, entityId, before, values);
            }
        };
    }

    private static void writeValueArray(WritableChannel out, Value[] values) throws IOException {
        writeNumber(out, values.length);
        for (Value value : values) {
            ValueStream.write(out, value);
        }
    }

    private static Value[] readValueArray(ReadableChannel in) throws IOException {
        int length = (int) readNumber(in);
        Value[] values = new Value[length];

        var peekableChannel = new PeekableChannel(in);
        for (int i = 0; i < length; i++) {
            values[i] = ValueStream.readValue(peekableChannel);
        }

        return values;
    }

    private static void writeTokenPart(WritableChannel out, TokenIndexUpdateCommand command) throws IOException {
        writeNumber(out, command.getEntityType().ordinal());
        switch (command.getUpdateMode()) {
            case ADDED, REMOVED -> writeTokenArray(out, command.getAfter());
            case CHANGED -> {
                writeTokenArray(out, command.getBefore());
                writeTokenArray(out, command.getAfter());
            }
        }
    }

    private static TokenIndexUpdateCommand readTokenPart(
            IndexCommandSerialization serialization,
            ReadableChannel in,
            UpdateMode updateMode,
            long indexId,
            long entityId)
            throws IOException {
        EntityType type = EntityType.of((byte) readNumber(in));
        return switch (updateMode) {
            case CHANGED -> {
                int[] before = readTokenArray(in);
                int[] values = readTokenArray(in);
                yield new TokenIndexUpdateCommand(serialization, indexId, entityId, before, values, type);
            }
            case ADDED, REMOVED ->
                throw new IllegalArgumentException("TokenIndexUpdateCommand can only have update mode CHANGED");
        };
    }

    private static int[] readTokenArray(ReadableChannel in) throws IOException {
        int length = (int) readNumber(in);
        int[] tokens = new int[length];

        for (int i = 0; i < length; i++) {
            tokens[i] = (int) readNumber(in);
        }

        return tokens;
    }

    private static void writeTokenArray(WritableChannel out, int[] tokens) throws IOException {
        writeNumber(out, tokens.length);
        for (int token : tokens) {
            writeNumber(out, token);
        }
    }

    private static void writeHeader(WritableChannel out, IndexUpdateCommand<?> command, boolean tokenIndex)
            throws IOException {
        byte header = 0;
        if (!tokenIndex) {
            header = (byte) (1 << 7);
        }

        header |= (byte) (command.getUpdateMode().ordinal() << 5);
        out.put(header);
    }

    private static boolean isTokenIndex(IndexUpdateCommand<?> command) {
        return command instanceof TokenIndexUpdateCommand;
    }

    public static void writeEntityId(WritableChannel out, long entityId) throws IOException {
        out.putLong(entityId);
    }

    public static long readEntityId(ReadableChannel in) throws IOException {
        return in.getLong();
    }
}

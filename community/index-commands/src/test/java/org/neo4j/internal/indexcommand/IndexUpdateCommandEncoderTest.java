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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.internal.indexcommand.IndexCommandSerializationImpl.V1;
import static org.neo4j.values.storable.Values.longValue;

import java.io.IOException;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.neo4j.common.EntityType;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.InMemoryClosableChannel;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.values.storable.Value;

class IndexUpdateCommandEncoderTest {
    private static final IndexCommandSerializationImpl SERIALIZATION_UNDER_TEST = V1;

    private final InMemoryClosableChannel channel = new InMemoryClosableChannel();
    private final IndexCommandSerialization serialization = new IndexCommandSerialization() {
        @Override
        public void writeIndexUpdateCommand(WritableChannel channel, IndexUpdateCommand<?> command) throws IOException {
            SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        }

        @Override
        public IndexUpdateCommand<?> readIndexUpdateCommand(ReadableChannel channel) throws IOException {
            return SERIALIZATION_UNDER_TEST.readCommand(this, channel);
        }

        @Override
        public KernelVersion kernelVersion() {
            return KernelVersion.GLORIOUS_FUTURE;
        }
    };

    @Test
    void packTokensAdd() throws IOException {
        var command = new TokenIndexUpdateCommand(
                serialization,
                123,
                3456789,
                ArrayUtils.EMPTY_INT_ARRAY,
                new int[] {1, 22, 333},
                EntityType.RELATIONSHIP);
        SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        var readCommand = SERIALIZATION_UNDER_TEST.readCommand(serialization, channel);
        assertCommandsEqual(command, readCommand);
    }

    @Test
    void packTokensChange() throws IOException {
        var command = new TokenIndexUpdateCommand(
                serialization, 123, 3456789, new int[] {12345}, new int[] {1, 22, 333}, EntityType.NODE);
        SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        var readCommand = SERIALIZATION_UNDER_TEST.readCommand(serialization, channel);
        assertCommandsEqual(command, readCommand);
    }

    @Test
    void packTokensRemove() throws IOException {
        var command = new TokenIndexUpdateCommand(
                serialization,
                123,
                3456789,
                ArrayUtils.EMPTY_INT_ARRAY,
                new int[] {1, 22, 333},
                EntityType.RELATIONSHIP);
        SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        var readCommand = SERIALIZATION_UNDER_TEST.readCommand(serialization, channel);
        assertCommandsEqual(command, readCommand);
    }

    @Test
    void packValuesAdd() throws IOException {
        var command = new ValueIndexUpdateCommand(serialization, UpdateMode.ADDED, 123, 3456789, null, new Value[] {
            longValue(1), longValue(22), longValue(333)
        });
        SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        var readCommand = SERIALIZATION_UNDER_TEST.readCommand(serialization, channel);
        assertCommandsEqual(command, readCommand);
    }

    @Test
    void packValuesChange() throws IOException {
        var command = new ValueIndexUpdateCommand(
                serialization, UpdateMode.CHANGED, 123, 3456789, new Value[] {longValue(12345)}, new Value[] {
                    longValue(1), longValue(22), longValue(333)
                });
        SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        var readCommand = SERIALIZATION_UNDER_TEST.readCommand(serialization, channel);
        assertCommandsEqual(command, readCommand);
    }

    @Test
    void packValuesRemove() throws IOException {
        var command = new ValueIndexUpdateCommand(serialization, UpdateMode.REMOVED, 123, 3456789, null, new Value[] {
            longValue(1), longValue(22), longValue(333)
        });
        SERIALIZATION_UNDER_TEST.writeCommand(channel, command);
        var readCommand = SERIALIZATION_UNDER_TEST.readCommand(serialization, channel);
        assertCommandsEqual(command, readCommand);
    }

    private void assertCommandsEqual(IndexUpdateCommand<?> expected, IndexUpdateCommand<?> read) {
        assertEquals(expected.getClass(), read.getClass());
        assertEquals(expected.getUpdateMode(), read.getUpdateMode());
        assertEquals(expected.getIndexId(), read.getIndexId());
        assertEquals(expected.getEntityId(), read.getEntityId());

        switch (expected) {
            case TokenIndexUpdateCommand expectedCast -> {
                TokenIndexUpdateCommand readCast = (TokenIndexUpdateCommand) read;
                assertArrayEquals(expectedCast.getBefore(), readCast.getBefore());
                assertArrayEquals(expectedCast.getAfter(), readCast.getAfter());
            }
            case ValueIndexUpdateCommand expectedCast -> {
                ValueIndexUpdateCommand readCast = (ValueIndexUpdateCommand) read;
                assertArrayEquals(expectedCast.getBefore(), readCast.getBefore());
                assertArrayEquals(expectedCast.getAfter(), readCast.getAfter());
            }
        }
    }
}

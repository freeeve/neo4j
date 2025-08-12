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
package org.neo4j.internal.recordstorage;

import java.io.IOException;
import org.neo4j.internal.indexcommand.IndexCommandSerializationImpl;
import org.neo4j.internal.indexcommand.IndexUpdateCommand;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.kernel.KernelVersion;

class LogCommandSerializationV5_25 extends LogCommandSerializationV5_11 {
    static final LogCommandSerializationV5_25 INSTANCE = new LogCommandSerializationV5_25(KernelVersion.V5_25);
    static final LogCommandSerializationV5_25 V2025_04_INSTANCE =
            new LogCommandSerializationV5_25(KernelVersion.V2025_04);
    static final LogCommandSerializationV5_25 V2025_05_INSTANCE =
            new LogCommandSerializationV5_25(KernelVersion.V2025_05);
    static final LogCommandSerializationV5_25 V2025_07_INSTANCE =
            new LogCommandSerializationV5_25(KernelVersion.V2025_07);
    static final LogCommandSerializationV5_25 V2025_08_INSTANCE =
            new LogCommandSerializationV5_25(KernelVersion.V2025_08);
    static final LogCommandSerializationV5_25 V2025_09_INSTANCE =
            new LogCommandSerializationV5_25(KernelVersion.V2025_09);

    LogCommandSerializationV5_25(KernelVersion kernelVersion) {
        super(kernelVersion);
    }

    @Override
    public IndexUpdateCommand<?> readIndexUpdateCommand(ReadableChannel channel) throws IOException {
        return IndexCommandSerializationImpl.V1.readCommand(this, channel);
    }

    @Override
    public void writeIndexUpdateCommand(WritableChannel channel, IndexUpdateCommand<?> command) throws IOException {
        channel.put(NeoCommandType.INDEX_UPDATE_COMMAND);
        IndexCommandSerializationImpl.V1.writeCommand(channel, command);
    }
}

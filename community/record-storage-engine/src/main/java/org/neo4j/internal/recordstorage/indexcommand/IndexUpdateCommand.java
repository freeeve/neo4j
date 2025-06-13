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
package org.neo4j.internal.recordstorage.indexcommand;

import java.io.IOException;
import org.neo4j.internal.recordstorage.LogCommandSerialization;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.storageengine.api.StorageCommand;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.string.Mask;

public abstract sealed class IndexUpdateCommand<T> implements StorageCommand
        permits TokenIndexUpdateCommand, ValueIndexUpdateCommand {
    protected final UpdateMode updateMode;
    protected final long indexId;
    protected final long entityId;
    private final LogCommandSerialization serialization;

    public IndexUpdateCommand(
            LogCommandSerialization serialization, UpdateMode updateMode, long indexId, long entityId) {
        this.serialization = serialization;
        this.updateMode = updateMode;
        this.indexId = indexId;
        this.entityId = entityId;
    }

    @Override
    public KernelVersion kernelVersion() {
        return serialization.kernelVersion();
    }

    public UpdateMode getUpdateMode() {
        return updateMode;
    }

    public long getIndexId() {
        return indexId;
    }

    public long getEntityId() {
        return entityId;
    }

    public abstract T getBefore();

    public abstract T getAfter();

    @Override
    public String toString() {
        return toString(Mask.NO);
    }

    @Override
    public abstract String toString(Mask mask);

    @Override
    public void serialize(WritableChannel channel) throws IOException {
        serialization.writeIndexUpdateCommand(channel, this);
    }
}

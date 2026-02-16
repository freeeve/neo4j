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

import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;

import java.util.Arrays;
import java.util.Objects;
import org.neo4j.common.EntityType;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.string.Mask;

public final class TokenIndexUpdateCommand extends IndexUpdateCommand<int[]> {
    public static final long SHALLOW_SIZE = shallowSizeOfInstance(TokenIndexUpdateCommand.class);

    private final int[] before;
    private final int[] values;
    private final EntityType entityType;

    public TokenIndexUpdateCommand(
            IndexCommandSerialization serialization,
            long indexId,
            long entityId,
            int[] before,
            int[] values,
            EntityType entityType) {
        super(serialization, UpdateMode.CHANGED, indexId, entityId);
        this.before = before;
        this.values = values;
        this.entityType = entityType;
    }

    @Override
    public int[] getBefore() {
        return before;
    }

    @Override
    public int[] getAfter() {
        return values;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    @Override
    public String toString(Mask mask) {
        return String.format(
                "TokenIndexUpdateCommand[mode:%s, indexId:%d, entityId:%d, entityType:%s, before:%s, after:%s]",
                updateMode, indexId, entityId, entityType.name(), Arrays.toString(before), Arrays.toString(values));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TokenIndexUpdateCommand that)) {
            return false;
        }

        return Arrays.equals(before, that.before)
                && Arrays.equals(values, that.values)
                && entityType == that.entityType;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(before);
        result = 31 * result + Arrays.hashCode(values);
        result = 31 * result + Objects.hashCode(entityType);
        return result;
    }
}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.collection.trackable.HeapTrackingCollections;
import org.neo4j.internal.indexcommand.IndexCommandSerialization;
import org.neo4j.internal.indexcommand.IndexUpdateCommand;
import org.neo4j.internal.indexcommand.IndexUpdatesState;
import org.neo4j.internal.indexcommand.TokenIndexUpdateCommand;
import org.neo4j.internal.indexcommand.ValueIndexUpdateCommand;
import org.neo4j.internal.recordstorage.RecordState;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.EagerValueIndexEntryUpdate;
import org.neo4j.storageengine.api.StorageCommand;
import org.neo4j.storageengine.api.TokenIndexEntryUpdate;
import org.neo4j.storageengine.api.UpdateMode;
import org.neo4j.values.storable.Value;

public class IndexRecordState implements RecordState, IndexUpdatesState {

    private final IndexCommandSerialization serialization;
    private final List<TokenIndexEntryUpdate> tokenIndexUpdates = new ArrayList<>();
    private final Map<IndexEntityPair, EagerValueIndexEntryUpdate> valueIndexUpdates = new HashMap<>();

    public IndexRecordState(IndexCommandSerialization serialization) {
        this.serialization = serialization;
    }

    @Override
    public void extractCommands(Collection<StorageCommand> target, MemoryTracker memoryTracker) {
        int valueIndexCommandsSize = valueIndexUpdates.size();
        int tokenIndexCommandsSize = tokenIndexUpdates.size();
        if (valueIndexCommandsSize + tokenIndexCommandsSize == 0) {
            return;
        }
        memoryTracker.allocateHeap(valueIndexCommandsSize * ValueIndexUpdateCommand.SHALLOW_SIZE);
        memoryTracker.allocateHeap(tokenIndexCommandsSize * TokenIndexUpdateCommand.SHALLOW_SIZE);
        extractValueCommands(target, memoryTracker);
        extractTokenCommands(target, memoryTracker);
    }

    private void extractTokenCommands(Collection<StorageCommand> target, MemoryTracker memoryTracker) {
        try (var tokenIndexIndexCommands =
                HeapTrackingCollections.<TokenIndexUpdateCommand>newArrayList(memoryTracker)) {
            for (var update : tokenIndexUpdates) {
                tokenIndexIndexCommands.add(new TokenIndexUpdateCommand(
                        serialization,
                        update.indexKey().getId(),
                        update.getEntityId(),
                        update.removed(),
                        update.added()));
            }

            tokenIndexIndexCommands.sort(IndexComamdComparator.INDEX_COMMANDS_COMPARATOR);
            target.addAll(tokenIndexIndexCommands);
        }
    }

    private void extractValueCommands(Collection<StorageCommand> target, MemoryTracker memoryTracker) {
        try (var valueIndexCommands = HeapTrackingCollections.<ValueIndexUpdateCommand>newArrayList(memoryTracker)) {
            for (var update : valueIndexUpdates.values()) {
                Value[] before = null;
                if (update.updateMode() == UpdateMode.CHANGED) {
                    before = update.beforeValues();
                }

                valueIndexCommands.add(new ValueIndexUpdateCommand(
                        serialization,
                        update.updateMode(),
                        update.indexKey().getId(),
                        update.getEntityId(),
                        before,
                        update.values()));
            }
            valueIndexCommands.sort(IndexComamdComparator.INDEX_COMMANDS_COMPARATOR);
            target.addAll(valueIndexCommands);
        }
    }

    @Override
    public void addTokenUpdate(TokenIndexEntryUpdate tokenIndexUpdate) {
        tokenIndexUpdates.add(tokenIndexUpdate);
    }

    @Override
    public void putValueUpdate(IndexEntityPair key, EagerValueIndexEntryUpdate update) {
        valueIndexUpdates.put(key, update);
    }

    @Override
    public EagerValueIndexEntryUpdate getValueUpdate(IndexEntityPair key) {
        return valueIndexUpdates.get(key);
    }

    @Override
    public void close() throws Exception {}

    private static class IndexComamdComparator implements Comparator<IndexUpdateCommand<?>> {

        static final IndexComamdComparator INDEX_COMMANDS_COMPARATOR = new IndexComamdComparator();

        private IndexComamdComparator() {}

        @Override
        public int compare(IndexUpdateCommand o1, IndexUpdateCommand o2) {
            int result = Long.compare(o1.getIndexId(), o2.getIndexId());
            if (result != 0) {
                return result;
            }
            return Long.compare(o1.getEntityId(), o2.getEntityId());
        }
    }
}

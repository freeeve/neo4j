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
package org.neo4j.kernel.impl.store;

import org.neo4j.batchimport.api.input.PropertySizeCalculator;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.internal.id.BatchingIdSequence;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.impl.store.record.PropertyBlock;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.values.storable.Value;

/**
 * Calculates record size that property values will occupy if encoded into a {@link PropertyStore}.
 * Contains state and is designed for multiple uses from a single thread only.
 * Does actual encoding of property values, dry-run style.
 */
public class PropertyValueRecordSizeCalculator implements PropertySizeCalculator {
    private final BatchingIdSequence stringRecordIds = new BatchingIdSequence();
    private final DynamicRecordAllocator stringRecordCounter;
    private final BatchingIdSequence arrayRecordIds = new BatchingIdSequence();
    private final DynamicRecordAllocator arrayRecordCounter;
    private String storeFormat;

    private final long propertyRecordSize;
    private final long stringRecordSize;
    private final long arrayRecordSize;

    public PropertyValueRecordSizeCalculator(PropertyStore propertyStore) {
        this(
                propertyStore.getRecordSize(),
                propertyStore.getStringStore().getRecordSize(),
                propertyStore.getStringStore().getRecordDataSize(),
                propertyStore.getArrayStore().getRecordSize(),
                propertyStore.getArrayStore().getRecordDataSize(),
                propertyStore.getConfiguration().get(GraphDatabaseSettings.db_format));
    }

    public PropertyValueRecordSizeCalculator(
            int propertyRecordSize,
            int stringRecordSize,
            int stringRecordDataSize,
            int arrayRecordSize,
            int arrayRecordDataSize,
            String storeFormat) {
        this.propertyRecordSize = propertyRecordSize;
        this.stringRecordSize = stringRecordSize;
        this.arrayRecordSize = arrayRecordSize;
        this.stringRecordCounter = new StandardDynamicRecordAllocator(stringRecordIds, stringRecordDataSize);
        this.arrayRecordCounter = new StandardDynamicRecordAllocator(arrayRecordIds, arrayRecordDataSize);
        this.storeFormat = storeFormat;
    }

    @Override
    public long calculateSize(Value[] values, CursorContext cursorContext, MemoryTracker memoryTracker) {
        stringRecordIds.reset();
        arrayRecordIds.reset();

        long propertyRecordsUsed = 0;
        long freeBlocksInCurrentRecord = 0;
        for (Value value : values) {
            PropertyBlock block = new PropertyBlock();
            PropertyStore.encodeValue(
                    block,
                    0 /*doesn't matter*/,
                    value,
                    stringRecordCounter,
                    arrayRecordCounter,
                    cursorContext,
                    memoryTracker,
                    storeFormat);
            if (block.getValueBlocks().length > freeBlocksInCurrentRecord) {
                propertyRecordsUsed++;
                freeBlocksInCurrentRecord = PropertyType.getPayloadSizeLongs();
            }
            freeBlocksInCurrentRecord -= block.getValueBlocks().length;
        }

        long size = propertyRecordsUsed * propertyRecordSize;
        size += stringRecordIds.peek() * stringRecordSize;
        size += arrayRecordIds.peek() * arrayRecordSize;
        return size;
    }
}

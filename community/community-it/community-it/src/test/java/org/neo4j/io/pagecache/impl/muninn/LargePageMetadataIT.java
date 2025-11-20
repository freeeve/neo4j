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
package org.neo4j.io.pagecache.impl.muninn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.io.ByteUnit.GibiByte;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.mem.MemoryAllocator;
import org.neo4j.memory.EmptyMemoryTracker;

class LargePageMetadataIT {
    @Test
    void veryLargePageMetadataMustBeFullyAccessible() {
        // We need roughly 2 GiBs of memory for the meta-data here, which is why this is an IT and not a Test.
        // We add one extra page worth of data to the size here, to avoid ending up on a "convenient" boundary.
        int pageSize = (int) ByteUnit.kibiBytes(8);
        long pageCacheSize = ByteUnit.gibiBytes(513) + pageSize;
        int pages = Math.toIntExact(pageCacheSize / pageSize);

        try (MemoryAllocator mman = MemoryAllocator.createAllocator(GibiByte.toBytes(2), EmptyMemoryTracker.INSTANCE)) {
            PageMetadata pageMetadata = new PageMetadata(pages, pageSize, mman);

            // Verify we end up with the correct number of pages.
            assertThat(pageMetadata.getPageCount()).isEqualTo(pages);

            // Spot-check the accessibility in the bulk of the pages.
            IntStream.range(0, pages / 32)
                    .parallel()
                    .forEach(id -> verifyPageMetaDataIsAccessible(pageMetadata, id * 32));

            // Thoroughly check the accessibility around the tail end of the page list.
            IntStream.range(pages - 2000, pages)
                    .parallel()
                    .forEach(id -> verifyPageMetaDataIsAccessible(pageMetadata, id));
        }
    }

    private static void verifyPageMetaDataIsAccessible(PageMetadata pageMetadata, int id) {
        long ref = pageMetadata.deref(id);
        PageMetadata.incrementUsage(ref);
        PageMetadata.incrementUsage(ref);
        assertFalse(PageMetadata.decrementUsage(ref));
        assertTrue(PageMetadata.decrementUsage(ref));
        assertEquals(id, pageMetadata.toId(ref));
    }
}

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
package org.neo4j.kernel.impl.transaction.log.pruning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.impl.transaction.log.LogFileInformation;
import org.neo4j.logging.NullLogProvider;

class FileCountThresholdTest {
    private final Path file = mock(Path.class);
    private final long version = 1L;
    private final LogFileInformation source = mock(LogFileInformation.class);

    @Test
    void shouldReturnFalseWhenTheMaxNonEmptyLogCountIsNotReached() {
        // given
        final int maxNonEmptyLogCount = 2;
        final FileCountThreshold threshold = new FileCountThreshold(maxNonEmptyLogCount, NullLogProvider.getInstance());

        // when
        threshold.init();
        final boolean result = threshold.reached(file, version, source);

        // then
        assertFalse(result);
    }

    @Test
    void shouldReturnTrueWhenTheMaxNonEmptyLogCountIsReached() throws IOException {
        // given
        final int maxNonEmptyLogCount = 2;
        final FileCountThreshold threshold = new FileCountThreshold(maxNonEmptyLogCount, NullLogProvider.getInstance());
        when(source.getLastEntryAppendIndex()).thenReturn(5L);
        when(source.getPreviousAppendIndexFromHeader(anyLong())).thenReturn(3L);

        // when
        threshold.init();
        threshold.reached(file, version, source);
        final boolean result = threshold.reached(file, version, source);

        // then
        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenTheMaxNonEmptyLogCountIsReachedButNotTheMinimumOneWholeChunk() throws IOException {
        // given
        final int maxNonEmptyLogCount = 2;
        final FileCountThreshold threshold = new FileCountThreshold(maxNonEmptyLogCount, NullLogProvider.getInstance());
        when(source.getLastEntryAppendIndex()).thenReturn(5L);
        when(source.getPreviousAppendIndexFromHeader(anyLong())).thenReturn(5L);

        // then
        threshold.init();
        threshold.reached(file, version, source);
        assertFalse(threshold.reached(file, version, source));

        // But then when also satisfying the minimum of one whole chunk it should be true
        when(source.getPreviousAppendIndexFromHeader(anyLong())).thenReturn(4L);
        assertTrue(threshold.reached(file, version, source));
    }

    @Test
    void shouldResetTheCounterWhenInitIsCalled() {
        // given
        final int maxNonEmptyLogCount = 2;
        final FileCountThreshold threshold = new FileCountThreshold(maxNonEmptyLogCount, NullLogProvider.getInstance());

        // when
        threshold.init();
        threshold.reached(file, version, source);
        threshold.reached(file, version, source);
        threshold.init();
        final boolean result = threshold.reached(file, version, source);

        // then
        assertFalse(result);
    }
}

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
package org.neo4j.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class JobHandlesTest {
    @Test
    void shouldPassThroughSpecifiedException() throws Exception {
        // given
        JobHandle<Void> failingJob = mock(JobHandle.class);
        when(failingJob.get()).thenThrow(new ExecutionException(new IOException()));

        // when/then
        assertThatThrownBy(
                        () -> JobHandles.getAllResults(List.of(failingJob), IOException.class, RuntimeException::new))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldUseFallbackExceptionWrap() throws Exception {
        // given
        JobHandle<Void> failingJob = mock(JobHandle.class);
        when(failingJob.get()).thenThrow(new ExecutionException(new ArrayIndexOutOfBoundsException()));

        // when/then
        assertThatThrownBy(() ->
                        JobHandles.getAllResults(List.of(failingJob), IOException.class, IllegalStateException::new))
                .isInstanceOf(IllegalStateException.class);
    }
}

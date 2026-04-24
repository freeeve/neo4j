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
package org.neo4j.graphdb.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class IndexPopulationProgressTest {
    @Test
    void none() {
        assertThat(IndexPopulationProgress.NONE.getCompletedPercentage()).isCloseTo(0, within(0.01f));
    }

    @Test
    void done() {
        assertThat(IndexPopulationProgress.DONE.getCompletedPercentage()).isCloseTo(100, within(0.01f));
    }

    @Test
    void negativeCompleted() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new IndexPopulationProgress(-1, 1));
    }

    @Test
    void negativeTotal() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new IndexPopulationProgress(0, -1));
    }

    @Test
    void completedGreaterThanTotal() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new IndexPopulationProgress(2, 1));
    }

    @Test
    void getCompletedPercentage() {
        IndexPopulationProgress progress = new IndexPopulationProgress(1, 2);
        assertThat(progress.getCompletedPercentage()).isCloseTo(50.0f, within(0.01f));
    }
}

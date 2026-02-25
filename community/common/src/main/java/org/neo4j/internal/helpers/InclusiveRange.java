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
package org.neo4j.internal.helpers;

import java.util.Objects;
import org.neo4j.util.Preconditions;

public record InclusiveRange<T extends Comparable<T>>(T min, T max) {
    public InclusiveRange {
        Preconditions.checkArgument(
                Objects.requireNonNull(min).compareTo(Objects.requireNonNull(max)) <= 0,
                "min must be less than or equal to max");
    }

    /// Checks if the provide `value` is within the range inclusively.
    /// @param value the `value` to check if inclusively within the range
    /// @return effectively `value != null && min <= value && value <= max`
    public boolean contains(T value) {
        return value != null && min.compareTo(value) <= 0 && max.compareTo(value) >= 0;
    }

    /// Checks if the range is before the provided `value`.
    /// @param value the `value` to check if the range is completely before
    /// @return effectively `value != null && max < value`
    public boolean isBefore(T value) {
        return value != null && max.compareTo(value) < 0;
    }

    /// Checks if the range is after the provided `value`.
    /// @param value the `value` to check if the range is completely after
    /// @return effectively `value != null && value < min`
    public boolean isAfter(T value) {
        return value != null && value.compareTo(min) < 0;
    }
}

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
package org.neo4j.fleetmanagement.communication.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import java.util.Objects;

public class DataPoint {
    @JsonPropertyDescription("Map of string key-value pairs providing additional context for the metric")
    private Map<String, String> tags;

    @JsonPropertyDescription("Double value representing the metric measurement")
    private final Double value;

    public DataPoint(Double value) {
        this.value = value;
    }

    public DataPoint(Integer value) {
        this.value = value.doubleValue();
    }

    public DataPoint(Long value) {
        this.value = value.doubleValue();
    }

    public DataPoint(Map<String, String> tags, Double value) {
        this.tags = tags;
        this.value = value;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DataPoint dataPoint = (DataPoint) o;
        return Objects.equals(tags, dataPoint.tags) && Objects.equals(value, dataPoint.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, value);
    }
}

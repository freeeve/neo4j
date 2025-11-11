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

package org.neo4j.gqlstatus;

import java.util.Map;
import java.util.Optional;

/**
 * Interface that defines the properties accepted in the Diagnostic Records.
 * <p>
 * This interface has limited implementations to ensure the standards are being followed.
 */
public sealed interface DiagnosticRecordProperty<T>
        permits GqlStandardDiagnosticRecordProperty, NonGqlStandardDiagnosticRecordProperty {

    /**
     * The key used in the {@link DiagnosticRecord} map.
     */
    String key();

    /**
     * The default for the property case the value is not set while building the
     * {@link DiagnosticRecord}.
     * <p>
     * Those are only used for unset values from the {@link GqlStandardDiagnosticRecordProperty}
     * and for properties returned by the {@link Neo4jDiagnosticRecordProperty} stream method.
     */
    default Optional<T> defaultValue() {
        return Optional.empty();
    }

    /**
     * Helper method for combining the key and the default value in a single object.
     */
    default Optional<Map.Entry<String, T>> defaultEntry() {
        return defaultValue().map(v -> Map.entry(key(), v));
    }

    /**
     * Checks if the given value should be omitted from the {@link DiagnosticRecord}
     */
    default boolean isValueOmitted(T value) {
        return false;
    }

    /**
     * Serializes the complex values used by properties in Map, List, Integer, String, Boolean, etc.
     */
    default Object serializeValue(Object value) {
        return value;
    }

    /**
     * Returns true in case of the property is disabled.
     * <p>
     * Disabled properties shouldn't be placed in the {@link DiagnosticRecord}
     */
    default boolean disabled() {
        return false;
    }
}

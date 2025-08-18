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
package org.neo4j.internal.schema;

import java.util.Objects;

/**
 * Describes the provider for a specific {@link IndexType} and version via a standard name/version string
 * <br>
 * <strong>PLEASE NOTE</strong> that any new name/version pairs must be added to {@link AllIndexProviderDescriptors}
 */
public record IndexProviderDescriptor(String key, String version) {

    public IndexProviderDescriptor {
        Objects.requireNonNull(key, "Key must not be null.");
        Objects.requireNonNull(version, "Null provider version prohibited.");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Empty provider key prohibited.");
        }
    }

    /**
     * @return a combination of {@link #key ()} and {@link #version ()} with a '-' in between.
     */
    public String name() {
        return key + "-" + version;
    }
}

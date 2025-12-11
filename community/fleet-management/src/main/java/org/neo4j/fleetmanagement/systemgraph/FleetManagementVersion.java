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
package org.neo4j.fleetmanagement.systemgraph;

import static org.neo4j.dbms.database.KnownSystemComponentVersion.UNKNOWN_VERSION;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.database.ComponentVersion;
import org.neo4j.dbms.database.SystemGraphComponent;

public enum FleetManagementVersion implements ComponentVersion {
    V0(0, Neo4jVersions.VERSION_202511),

    FLEET_MANAGEMENT_UNKNOWN_VERSION(UNKNOWN_VERSION, String.format("no '%s' found", FLEET_MANAGEMENT_COMPONENT));

    public static final FleetManagementVersion CURRENT = highest();

    FleetManagementVersion(int version, String description) {
        this.version = version;
        this.description = description;
    }

    private final String description;
    private final int version;

    public static Optional<FleetManagementVersion> fromVersionNumber(Integer versionNumber) {
        if (versionNumber != null) {
            for (FleetManagementVersion componentVersion : FleetManagementVersion.values()) {
                if (componentVersion.version == versionNumber) {
                    return Optional.of(componentVersion);
                }
            }
        }
        return Optional.empty();
    }

    private static FleetManagementVersion highest() {
        return Arrays.stream(FleetManagementVersion.values())
                .max(Comparator.comparingInt(FleetManagementVersion::getVersion))
                .orElseThrow(() -> new IllegalStateException("There are no versions registered"));
    }

    @Override
    public String toString() {
        return description + '(' + version + ')';
    }

    FleetManagementVersion atLeast(FleetManagementVersion currentVersion) {
        return (currentVersion != null && currentVersion.version > this.version) ? currentVersion : this;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public SystemGraphComponent.Name getComponentName() {
        return FLEET_MANAGEMENT_COMPONENT;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isCurrent(Config config) {
        return this == CURRENT;
    }

    @Override
    public boolean migrationSupported() {
        return true;
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }
}

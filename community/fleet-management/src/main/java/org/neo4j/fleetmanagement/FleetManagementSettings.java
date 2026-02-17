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
package org.neo4j.fleetmanagement;

import static org.neo4j.configuration.SettingValueParsers.BOOL;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Description;
import org.neo4j.configuration.SettingImpl;
import org.neo4j.configuration.SettingsDeclaration;
import org.neo4j.graphdb.config.Setting;

@ServiceProvider
public class FleetManagementSettings implements SettingsDeclaration {
    @Description("Enable Fleet Manager functionality for monitoring with Neo4j Aura.")
    public static final Setting<Boolean> fleet_manager_enabled =
            SettingImpl.newBuilder("dbms.fleet_manager.enabled", BOOL, true).build();
}

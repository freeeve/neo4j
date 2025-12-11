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
package org.neo4j.test;

import static org.neo4j.configuration.GraphDatabaseSettings.logical_log_rotation_threshold;
import static org.neo4j.configuration.GraphDatabaseSettings.pagecache_memory;

import java.util.Map;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.SettingMigrator;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.logging.InternalLog;

@ServiceProvider
public class DefaultSettingValuesOverride implements SettingMigrator {

    @Override
    public void migrate(Map<String, String> values, Map<String, String> defaultValues, InternalLog log) {
        overrideDefault(logical_log_rotation_threshold, "256k", values, defaultValues);
        overrideDefault(pagecache_memory, "8m", values, defaultValues);
    }

    private static void overrideDefault(
            Setting<?> setting, String value, Map<String, String> values, Map<String, String> defaultValues) {
        if (!values.containsKey(setting.name())) {
            defaultValues.put(setting.name(), value);
        }
    }
}

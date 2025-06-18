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
package org.neo4j.cloud.storage;

import static org.neo4j.configuration.SettingImpl.newBuilder;
import static org.neo4j.configuration.SettingValueParsers.PATH;

import java.nio.file.Path;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Description;
import org.neo4j.configuration.SettingsDeclaration;
import org.neo4j.graphdb.config.Setting;

@ServiceProvider
public class SharedStorageSettingsDeclaration implements SettingsDeclaration {
    @Description("The Path to use when building chunks to be uploaded to the external storage provider.")
    public static final Setting<Path> temp_chunk_path = newBuilder(
                    StorageSettingsDeclaration.INTERNAL_CONFIG_PREFIX + ".temp_chunk_path",
                    PATH,
                    Path.of(System.getProperty("java.io.tmpdir")))
            .internal()
            .build();
}

package org.neo4j.fleetmanagement.topology;

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

import static java.util.Objects.requireNonNull;
import static org.neo4j.configuration.GraphDatabaseSettings.neo4j_home;

import java.nio.file.Path;
import java.util.List;
import org.neo4j.configuration.Config;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.FileSystemUtils;
import org.neo4j.kernel.diagnostics.providers.PackagingDiagnostics;
import org.neo4j.logging.Log;
import org.neo4j.memory.EmptyMemoryTracker;

public class PackagingInformation {

    private static final String PACKAGE_TYPE = "Package Type:";

    public static String getPackaging(Config config, FileSystemAbstraction fs, Log userLog) {
        Path packagingInfoFile = config.get(neo4j_home).resolve(PackagingDiagnostics.PACKAGING_INFO_FILENAME);
        try {
            List<String> lines = FileSystemUtils.readLines(fs, packagingInfoFile, EmptyMemoryTracker.INSTANCE);
            for (String line : requireNonNull(lines)) {
                if (line.startsWith(PACKAGE_TYPE)) {
                    return line.substring(PACKAGE_TYPE.length()).trim();
                }
            }
        } catch (Exception e) {
            userLog.error("Unable to read packaging information from " + packagingInfoFile + " - " + e);
            return "unknown";
        }
        return "unknown";
    }
}

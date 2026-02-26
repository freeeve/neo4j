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
package org.neo4j.udc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.logging.InternalLog;

@ServiceProvider
public class CustomPluginUDC implements UserDataCollectorSource {
    private static final Predicate<String> JAR_FILE_PATTERN =
            Pattern.compile(".*\\.jar$", Pattern.CASE_INSENSITIVE).asPredicate();
    private static final Predicate<String> GDS_JAR_PATTERN = Pattern.compile(
                    "^neo4j-graph-data-science-.*\\.jar$", Pattern.CASE_INSENSITIVE)
            .asPredicate();
    private static final Predicate<String> APOC_CORE_JAR_PATTERN =
            Pattern.compile("^apoc-.*-core\\.jar$", Pattern.CASE_INSENSITIVE).asPredicate();
    private static final Predicate<String> APOC_EXTENDED_JAR_PATTERN = Pattern.compile(
                    "^apoc-.*-(all|extended)\\.jar$", Pattern.CASE_INSENSITIVE)
            .asPredicate();
    private static final Predicate<String> GEN_AI_PATTERN =
            Pattern.compile("^neo4j-genai-.*\\.jar$", Pattern.CASE_INSENSITIVE).asPredicate();
    private static final Predicate<String> BLOOM_PATTERN =
            Pattern.compile("^bloom-plugin-.*\\.jar$", Pattern.CASE_INSENSITIVE).asPredicate();

    public static final String APOC_EXTENDED_PRESENT_KEY = "apocExtendedPresent";
    public static final String EXTERNAL_PLUGIN_PRESENT_KEY = "externalPluginPresent";

    @Override
    public Map<String, String> getData(
            DatabaseManagementService databaseManagementService,
            FileSystemAbstraction fs,
            Config config,
            InternalLog log) {
        boolean apocExtendedPresent = false;
        boolean externalPluginPresent = false;
        Path pluginsFolder = config.get(GraphDatabaseSettings.plugin_dir);
        try {
            // Use file names of jars to estimate usage of third party plugins despite limitations
            // as we don't want to undo the security work that is applied to plugins
            Path[] jars = fs.listFiles(
                    pluginsFolder,
                    name -> JAR_FILE_PATTERN.test(name.getFileName().toString()));
            for (Path jar : jars) {
                String jarName = jar.getFileName().toString();
                // Explicitly identify apoc extended use
                if (APOC_EXTENDED_JAR_PATTERN.test(jarName)) {
                    apocExtendedPresent = true;
                    continue;
                }
                // Filter out Aura supported plugins
                if (!(APOC_CORE_JAR_PATTERN.test(jarName)
                        || GDS_JAR_PATTERN.test(jarName)
                        || GEN_AI_PATTERN.test(jarName)
                        || BLOOM_PATTERN.test(jarName))) {
                    externalPluginPresent = true;
                }
            }
        } catch (IOException e) {
            // Folder doesn't exist, or isn't accessible, assume no plugins
        }
        return Map.of(
                APOC_EXTENDED_PRESENT_KEY, Boolean.toString(apocExtendedPresent),
                EXTERNAL_PLUGIN_PRESENT_KEY, Boolean.toString(externalPluginPresent));
    }
}

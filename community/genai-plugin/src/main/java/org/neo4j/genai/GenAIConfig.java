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
package org.neo4j.genai;

import static java.lang.String.format;
import static org.neo4j.configuration.GraphDatabaseSettings.configuration_directory;
import static org.neo4j.internal.helpers.ProcessUtils.executeCommandWithOutput;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.NullLog;
import org.neo4j.logging.internal.LogService;
import org.neo4j.util.Preconditions;

public class GenAIConfig extends LifecycleAdapter {
    public static final String GENAI_OPENAI_BASE_URL = "genai.openai.baseurl";
    public static final String GENAI_AZURE_OPENAI_BASE_URL = "genai.azureopenai.baseurl";

    private static final Map<String, Object> configDefaultValues = Map.of(
            GENAI_OPENAI_BASE_URL, "https://api.openai.com/v1",
            GENAI_AZURE_OPENAI_BASE_URL, "https://%s.openai.azure.com/openai/v1");

    private final Config neo4jConfig;
    private final Log log;

    protected final Map<String, Object> settings = new ConcurrentHashMap<>();

    private static GenAIConfig theInstance;

    private boolean expandCommands;
    private Duration commandEvaluationTimeout;
    private File genaiConfFile;

    public GenAIConfig(Config neo4jConfig, LogService log, GlobalProcedures globalProceduresRegistry) {
        this.neo4jConfig = neo4jConfig;
        this.commandEvaluationTimeout =
                neo4jConfig.get(GraphDatabaseInternalSettings.config_command_evaluation_timeout);
        if (this.commandEvaluationTimeout == null) {
            this.commandEvaluationTimeout =
                    GraphDatabaseInternalSettings.config_command_evaluation_timeout.defaultValue();
        }
        this.expandCommands = neo4jConfig.expandCommands();
        this.log = log.getInternalLog(GenAIConfig.class);
        theInstance = this;

        // expose this config instance via `@Context instance config`
        globalProceduresRegistry.registerComponent(GenAIConfig.class, ctx -> this, true);
    }

    // use only for unit tests
    public GenAIConfig(Config neo4jConfig) {
        this.neo4jConfig = neo4jConfig;
        this.log = NullLog.getInstance();
        theInstance = this;
    }

    private String evaluateIfCommand(String settingName, String entry) {
        if (Config.isCommand(entry)) {
            Preconditions.checkArgument(
                    expandCommands,
                    format(
                            "%s is a command, but config is not explicitly told to expand it. (Missing --expand-commands argument?)",
                            entry));
            String str = entry.trim();
            String command = str.substring(2, str.length() - 1);
            log.info("Executing external script to retrieve value of setting " + settingName);
            String result = executeCommandWithOutput(command, commandEvaluationTimeout);
            return result == null ? "" : result.trim();
        }
        return entry;
    }

    @Override
    public void init() {
        // Grab NEO4J_CONF from environment.
        // If not set get it from database internal setting: "server.directories.configuration"
        String neo4jConfFolder = System.getenv().getOrDefault("NEO4J_CONF", determineNeo4jConfFolder());
        if (System.getProperty("NEO4J_CONF") == null) {
            System.setProperty("NEO4J_CONF", neo4jConfFolder);
            log.debug("NEO4J_CONF was not set; setting it to %s", neo4jConfFolder);
        } else {
            log.debug("NEO4J_CONF already set to %s; leaving as is", System.getProperty("NEO4J_CONF"));
        }
        genaiConfFile = new File(neo4jConfFolder + "/genai.conf");
        // Command Expansion required check from Neo4j
        if (genaiConfFile.exists() && this.expandCommands) {
            Config.Builder.validateFilePermissionForCommandExpansion(List.of(genaiConfFile.toPath()));
        }

        loadConfiguration();
    }

    protected String determineNeo4jConfFolder() {
        return neo4jConfig.get(configuration_directory).toString();
    }

    public void fromFile(Path file) {
        if (file == null || Files.notExists(file)) {
            log.warn("Config file [%s] does not exist.", file);
            return;
        }

        try {
            if (Files.isDirectory(file)) {
                log.warn("Config file [%s] is a directory.", file);
            } else {
                try (Reader reader =
                        new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                    new Properties() {
                        @Override
                        public synchronized Object put(Object key, Object value) {
                            String setting = key.toString();
                            settings.put(setting, value);
                            return null;
                        }
                    }.load(reader);
                }
            }
        } catch (IOException e) {
            log.error("Unable to load config file for the GenAI Plugin [%s]: %s", file, e.getMessage());
        }
    }

    protected void loadConfiguration() {
        fromFile(genaiConfFile.toPath());

        // Command Expansion if needed
        settings.keySet()
                .forEach(configKey -> settings.put(
                        configKey,
                        evaluateIfCommand(configKey, settings.get(configKey).toString())));

        // set config settings not explicitly set in genai.conf to their default value
        configDefaultValues.forEach((k, v) -> {
            if (!settings.containsKey(k)) {
                settings.put(k, v);
                log.info("setting GenAI config to default value: " + k + "=" + v);
            }
        });
    }

    public static GenAIConfig instance() {
        return theInstance;
    }

    public void setProperty(String key, Object value) {
        settings.put(key, value);
    }

    public Object getProperty(String key) {
        return settings.get(key);
    }

    public Config getNeo4jConfig() {
        return neo4jConfig;
    }

    public <T> T getNeo4jProperty(Setting<T> key) {
        return neo4jConfig.get(key);
    }

    public String getStringProperty(String key) {
        return (String) settings.get(key);
    }
}

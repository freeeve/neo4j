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
package org.neo4j.cli;

import static java.lang.String.format;
import static org.neo4j.configuration.GraphDatabaseSettings.logs_directory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.collections.impl.set.mutable.MutableSetFactoryImpl;
import org.neo4j.cloud.storage.SharedStorageSettingsDeclaration;
import org.neo4j.cloud.storage.StoragePath;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.connectors.HttpsConnector;
import org.neo4j.configuration.helpers.DatabaseNamePattern;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.kernel.api.exceptions.ConsoleFriendlyException;
import org.neo4j.kernel.diagnostics.providers.SystemDiagnostics;
import org.neo4j.kernel.internal.Version;
import org.neo4j.logging.Level;
import org.neo4j.logging.log4j.LogConfig;
import org.neo4j.logging.log4j.Neo4jLoggerContext;
import org.neo4j.time.Stopwatch;
import picocli.CommandLine;

/**
 * A parent for administration commands that provides the following functionality to its children:
 * <ul>
 *     <li>The command is forked, in order to configure its JVM</li>
 *     <li>Apart from the {@value Config#DEFAULT_CONFIG_FILE_NAME}, configuration can be also supplied with
 *     (in descending order of priority):
 *       <ul>
 *         <li>--additional-config option</li>
 *         <li>a command-specific configuration file</li>
 *         <li>{@value #ADMIN_CONFIG_FILE_NAME} configuration file</li>
 *       </ul>
 *       All those extra configuration options have higher priority than {@value Config#DEFAULT_CONFIG_FILE_NAME}.
 *     </li>
 * </ul>
 */
public abstract class AbstractAdminCommand extends AbstractCommand {

    public static final String COMMAND_CONFIG_FILE_NAME_PATTERN = "neo4j-admin-%s.conf";
    public static final String ADMIN_CONFIG_FILE_NAME = "neo4j-admin.conf";
    public static final String CRASH_INFO_TIMEOUT = "NEO4J_ADMIN_CRASH_INFO_DUMP_TIMEOUT_SECONDS";
    private static final DateTimeFormatter SPACELESS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd.HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final String EXCEPTION_FILE_NAME_TEMPLATE = "neo4j-admin-exception-trace-%s.log";

    @CommandLine.Option(
            names = "--additional-config",
            paramLabel = "<file>",
            description = "Configuration file with additional configuration.")
    private Path additionalConfig;

    protected AbstractAdminCommand(ExecutionContext ctx) {
        super(ctx);
    }

    /**
     * Returns command extra (NOT {@value Config#DEFAULT_CONFIG_FILE_NAME}) configuration files
     * ordered by priority.
     * The method throws {@link IllegalArgumentException} if a config file provided with
     * --additional-config options does not exist.
     */
    public List<Path> getCommandConfigs() throws IllegalArgumentException {
        List<Path> configs = new ArrayList<>(3);

        if (additionalConfig != null) {
            if (!configFileExists(additionalConfig)) {
                throw new CommandFailedException(String.format("File %s does not exist", additionalConfig));
            }

            configs.add(additionalConfig);
        }

        commandConfigName()
                .map(configName -> String.format(COMMAND_CONFIG_FILE_NAME_PATTERN, configName))
                .map(ctx.confDir()::resolve)
                .filter(this::configFileExists)
                .ifPresent(configs::add);

        Path adminConfig = ctx.confDir().resolve(ADMIN_CONFIG_FILE_NAME);
        if (configFileExists(adminConfig)) {
            configs.add(adminConfig);
        }

        return configs;
    }

    @Override
    protected List<Path> configFiles() {
        List<Path> commandConfigs = getCommandConfigs();
        commandConfigs.addAll(super.configFiles());
        return commandConfigs;
    }

    private boolean configFileExists(Path path) {
        return ctx.fs().fileExists(path) && !ctx.fs().isDirectory(path);
    }

    /**
     * This is an extension point that should be used by commands that support a command-specific config file.
     * This method can be overridden to provide the part of the file name specific to the command.
     * The provided name will be used in {@value #COMMAND_CONFIG_FILE_NAME_PATTERN} pattern.
     */
    protected Optional<String> commandConfigName() {
        return Optional.empty();
    }

    /**
     * Creates a configuration builder with the logic common to all admin commands applied to it.
     * All children of this abstract command, should use this method when building its configuration.
     */
    protected Config.Builder createPrefilledConfigBuilder() {
        return createPrefilledConfigBuilder(null);
    }

    /**
     * Creates a configuration builder with the logic common to all admin commands applied to it.
     */
    protected Config.Builder createPrefilledConfigBuilder(Path storageTempPath) {
        List<Path> commandConfigs = getCommandConfigs();
        var configBuilder = Config.newBuilder().fromFileNoThrow(ctx.confDir().resolve(Config.DEFAULT_CONFIG_FILE_NAME));
        commandConfigs.reversed().forEach(configBuilder::fromFileNoThrow);
        configBuilder.commandExpansion(allowCommandExpansion).set(GraphDatabaseSettings.neo4j_home, ctx.homeDir());
        configBuilder.set(BoltConnector.enabled, Boolean.FALSE);
        configBuilder.set(HttpConnector.enabled, Boolean.FALSE);
        configBuilder.set(HttpsConnector.enabled, Boolean.FALSE);
        if (storageTempPath != null) {
            configBuilder.set(SharedStorageSettingsDeclaration.temp_chunk_path, storageTempPath);
        }
        return configBuilder;
    }

    protected Path requireExisting(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            throw new CommandFailedException(format("Path '%s' does not exist.", p), e);
        }
    }

    protected Path normalizeAndValidateIfStoragePathDirectory(Path path) throws CommandFailedException {
        final var normalized = path.normalize();
        if (normalized instanceof StoragePath storagePath && !storagePath.isDirectory()) {
            throw new CommandFailedException("The path '%s' is not a directory - please add a terminal '/' to your path"
                    .formatted(storagePath.toUri()));
        }

        return normalized;
    }

    protected static Set<String> getDbNames(Config config, FileSystemAbstraction fs, DatabaseNamePattern database)
            throws CommandFailedException {
        if (!database.containsPattern()) {
            return Set.of(database.getDatabaseName());
        } else {
            Set<String> dbNames = MutableSetFactoryImpl.INSTANCE.empty();
            Path databasesDir = Neo4jLayout.of(config).databasesDirectory();
            try {
                for (Path path : fs.listFiles(databasesDir)) {
                    if (fs.isDirectory(path)) {
                        String name = path.getFileName().toString();
                        if (database.matches(name)) {
                            dbNames.add(name);
                        }
                    }
                }
            } catch (IOException e) {
                throw new CommandFailedException(
                        format("Failed to list databases: %s: %s", e.getClass().getSimpleName(), e.getMessage()), e);
            }
            if (dbNames.isEmpty()) {
                throw new CommandFailedException(
                        "Pattern '" + database.getDatabaseName() + "' did not match any database");
            }
            return dbNames;
        }
    }

    // hook the raw execute to log info
    @Override
    protected void wrappedExecute() throws Exception {
        Stopwatch start = Stopwatch.start();
        try {
            execute();
        } catch (Throwable ex) {
            logCrashInformation(ex, start.elapsed());
            throw ex;
        }
    }

    @SuppressWarnings("resource")
    protected void println(String message) {
        ctx.out().println(message);
    }

    @SuppressWarnings("resource")
    protected void printf(String message, Object... args) {
        ctx.out().printf(message, args);
    }

    private void logCrashInformation(Throwable ex, Duration elapsed) {
        try {
            int timeout = Integer.parseInt(System.getenv().getOrDefault(CRASH_INFO_TIMEOUT, "3"));
            if (!verbose && elapsed.toMillis() < TimeUnit.SECONDS.toMillis(timeout)) {
                return;
            }
            var config = createPrefilledConfigBuilder().build();
            var exceptionFile = config.get(logs_directory)
                    .resolve(format(EXCEPTION_FILE_NAME_TEMPLATE, SPACELESS_DATE_FORMATTER.format(Instant.now())));
            ctx.fs().mkdirs(exceptionFile.getParent());
            try (Neo4jLoggerContext exceptionLoggerCtx =
                    LogConfig.createTemporaryLoggerToSingleFile(ctx.fs(), exceptionFile, Level.INFO, false)) {
                var exceptionLogger = exceptionLoggerCtx.getLogger(getClass());
                exceptionLogger.info("This file is to aid Neo4j support.");
                // log exception early in case there are issues with any of the extra info
                exceptionLogger.error("Fatal exception thrown", ex);
                if (ConsoleFriendlyException.willPrettyPrint(ex)) {
                    ((ConsoleFriendlyException) ex)
                            .addSupplementaryMessage(format(
                                    "Full exception details written to: %s%nPlease provide this file if requesting neo4j support",
                                    exceptionFile));
                } else {
                    ctx.err().println("Full exception details written to: " + exceptionFile);
                    ctx.err().println("Please provide this file if requesting neo4j support");
                }
                // Dump everything that might be useful later into the file
                var runtime = ManagementFactory.getRuntimeMXBean();
                exceptionLogger.info("Process Started at: " + Instant.ofEpochMilli(runtime.getStartTime()));
                var originalArgs = String.join(
                        " ", spec.root().commandLine().getParseResult().originalArgs());
                exceptionLogger.info("CommandLine: " + originalArgs);
                exceptionLogger.info("neo4j version: " + Version.getNeo4jVersion());
                SystemDiagnostics.JAVA_VIRTUAL_MACHINE.dump(exceptionLogger::info);
                SystemDiagnostics.CLASSPATH.dump(exceptionLogger::info);
                SystemDiagnostics.OPERATING_SYSTEM.dump(exceptionLogger::info);
                SystemDiagnostics.SYSTEM_MEMORY.dump(exceptionLogger::info);
                SystemDiagnostics.JAVA_MEMORY.dump(exceptionLogger::info);
                exceptionLogger.info("Configuration files used (ordered by priority):");
                configFiles().forEach(file -> exceptionLogger.info(file.toAbsolutePath()));
            }
        } catch (Throwable e) {
            // suppress any errors trying to write diagnostics
            ex.addSuppressed(e);
        }
    }
}

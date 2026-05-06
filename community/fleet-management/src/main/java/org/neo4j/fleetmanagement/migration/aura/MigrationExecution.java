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
package org.neo4j.fleetmanagement.migration.aura;

import static java.lang.String.format;
import static org.neo4j.export.DumpUploader.makeDumpUploader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Callable;
import org.apache.commons.io.FileUtils;
import org.neo4j.cli.ContextInjectingFactory;
import org.neo4j.commandline.dbms.DumpCommand;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.database.DatabaseContext;
import org.neo4j.export.UploadURLFactory;
import org.neo4j.export.Uploader;
import org.neo4j.export.aura.AuraClient;
import org.neo4j.export.aura.AuraConsole;
import org.neo4j.export.aura.AuraURLFactory;
import org.neo4j.export.providers.SignedUpload;
import org.neo4j.export.providers.SignedUploadURLFactory;
import org.neo4j.fleetmanagement.communication.model.MigrationToAura;
import org.neo4j.fleetmanagement.communication.model.MigrationTokenResponse;
import org.neo4j.fleetmanagement.communication.model.UpdateMigrationStatusMessage;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.logging.Log;
import picocli.CommandLine;

public class MigrationExecution {

    public static final int MIN_USABLE_SPACE_FACTOR = 2;
    private final Log userLog;
    private final Logger fleetManagerLog;
    private final Config config;
    private final CapturingExecutionContext ctx;
    private final ApiConnector apiConnector;
    private final DatabaseContext databaseContext;

    public MigrationExecution(
            Log userLog,
            Logger fleetManagerLog,
            Config config,
            CapturingExecutionContext ctx,
            ApiConnector apiConnector,
            DatabaseContext databaseContext) {
        this.userLog = userLog;
        this.fleetManagerLog = fleetManagerLog;
        this.config = config;
        this.ctx = ctx;
        this.apiConnector = apiConnector;
        this.databaseContext = databaseContext;
    }

    public void reportStatus() {
        UpdateMigrationStatusMessage msg = ctx.newUpdateMessageWithLogs();
        apiConnector.call(msg, Upstream.Endpoint.MIGRATION_UPDATE_STATUS);
    }

    public void processMigration(MigrationToAura migrationToAura) {
        Path dumpDirectory = prepareDump(migrationToAura);
        if (migrationToAura.isUploadPending()) {
            doUploadAndImport(migrationToAura, dumpDirectory);
        } else {
            fleetManagerLog.debug(
                    "dump upload can't start yet. Instance %s creation successful: %b, upload already running: %b",
                    migrationToAura.targetAuraInstanceId,
                    migrationToAura.isInstanceCreationSuccessful(),
                    migrationToAura.uploadStartedAt != null);
        }
    }

    private void doUploadAndImport(MigrationToAura migrationToAura, Path dumpFolder) {
        ctx.setMigrationStep(UpdateMigrationStatusMessage.MigrationStep.UPLOAD);
        try {
            onStepStarted();
            MigrationTokenResponse migrationTokenResponse = getMigrationImportToken();
            userLog.info(
                    "Uploading database %s to Aura instance at %s",
                    migrationToAura.sourceDatabaseName, migrationTokenResponse.targetAuraInstanceBoltUrl);
            AuraConsole auraConsole = new AuraURLFactory()
                    .buildConsoleURI(
                            migrationTokenResponse.targetAuraInstanceBoltUrl,
                            Boolean.parseBoolean(System.getenv("P2C_DEV_MODE")));

            AuraClient auraClient = new AuraClient.AuraClientBuilder(ctx)
                    .withAuraConsole(auraConsole)
                    .withConsent(false)
                    .withBoltURI(migrationTokenResponse.targetAuraInstanceBoltUrl)
                    .withBearerToken(migrationTokenResponse.migrationToken)
                    .withDefaults()
                    .build();

            UploadURLFactory uploadUrlFactory = (signedURIBodyResponse, c, boltURI) -> {
                SignedUpload uploader =
                        new SignedUploadURLFactory().fromAuraResponse(signedURIBodyResponse, ctx, boltURI);
                return (verbose, src) -> {
                    uploader.copy(verbose, src);
                    onStepSuccess();
                    ctx.setMigrationStep(UpdateMigrationStatusMessage.MigrationStep.IMPORT);
                    onStepStarted();
                };
            };
            Uploader uploader = makeDumpUploader(
                    dumpFolder,
                    migrationToAura.sourceDatabaseName,
                    ctx,
                    Logger.isDebugEnabled(),
                    migrationTokenResponse.targetAuraInstanceBoltUrl,
                    uploadUrlFactory);

            uploader.process(auraClient);
            userLog.info(
                    "Database %s uploaded to Aura instance at %s",
                    migrationToAura.sourceDatabaseName, migrationTokenResponse.targetAuraInstanceBoltUrl);
            onStepSuccess();
        } catch (Throwable e) {
            userLog.error(format("Failed to upload and import dump for migration %s", migrationToAura.migrationId), e);
            onStepError(e);
            throw new RuntimeException(e);
        } finally {
            userLog.info("Upload and import logs:%n%s", ctx.getExecutionOutput());
        }
    }

    private void startDatabase(DatabaseContext databaseContext) {
        String databaseName = databaseContext.database().getNamedDatabaseId().name();
        if (databaseContext.database().isStarted()) {
            fleetManagerLog.debug("Database %s is already started", databaseName);
        } else {
            userLog.info("Starting database %s", databaseName);
            databaseContext.database().start();
            userLog.info("Database %s started", databaseName);
        }
    }

    private Path prepareDump(MigrationToAura migrationToAura) {
        try {
            var dumpDirectory = getDumpsRootDirectory().resolve("migration_" + migrationToAura.migrationId);
            if (validDumpExists(migrationToAura, migrationToAura.sourceDatabaseName, dumpDirectory)) {
                return dumpDirectory;
            }

            onStepStarted();
            checkPrerequisites(databaseContext);
            doOnStoppedDatabase(databaseContext, migrationToAura.restartDatabaseAfterMigration, () -> {
                dumpDatabase(migrationToAura.sourceDatabaseName, dumpDirectory);
                onStepSuccess();
                return null;
            });

            return dumpDirectory;
        } catch (Throwable e) {
            userLog.error(format("Failed to create dump for migration %s", migrationToAura.migrationId), e);
            onStepError(e);
            throw new RuntimeException(e);
        }
    }

    private boolean validDumpExists(MigrationToAura migrationToAura, String sourceDatabaseName, Path dumpDirectory) {
        var dumpFile =
                dumpDirectory.resolve(getDumpFileName(sourceDatabaseName)).toFile();
        if (migrationToAura.isDumpSuccessful() && dumpFile.exists() && dumpFile.isFile() && dumpFile.canRead()) {
            userLog.info(
                    "Valid dump at %s already exists with change timestamp %s, completion reported at %s, will use it",
                    dumpDirectory, Instant.ofEpochMilli(dumpFile.lastModified()), migrationToAura.dumpCompletedAt);
            return true;
        }
        fleetManagerLog.debug(
                "No valid dump found at %s. Dump successful: %b, file exists: %b, is file: %b, can read: %b",
                dumpDirectory,
                migrationToAura.isDumpSuccessful(),
                dumpFile.exists(),
                dumpFile.isFile(),
                dumpFile.canRead());
        return false;
    }

    private static String getDumpFileName(String sourceDatabaseName) {
        return String.format("%s.dump", sourceDatabaseName);
    }

    private void checkPrerequisites(DatabaseContext databaseContext) throws IOException {
        Path databaseDirectory = databaseContext.database().getDatabaseLayout().databaseDirectory();
        long databaseDirectorySize = FileUtils.sizeOfDirectory(databaseDirectory.toFile());
        fleetManagerLog.debug("Database directory %s size: %d bytes", databaseDirectory, databaseDirectorySize);

        Path dumpsRootDirectory = getDumpsRootDirectory();
        boolean mkdirsSuccess = dumpsRootDirectory.toFile().mkdirs();
        if (!mkdirsSuccess && !dumpsRootDirectory.toFile().exists()) {
            throw new IOException(format("Failed to create dumps root directory at %s", dumpsRootDirectory));
        }

        long usableSpace = dumpsRootDirectory.toFile().getUsableSpace();

        if (getMinUsableSpace(databaseDirectorySize) > usableSpace) {
            throw new IOException(format(
                    "Not enough disk space to dump the database. Required: %d bytes, available: %d bytes",
                    getMinUsableSpace(databaseDirectorySize), usableSpace));
        }
    }

    private long getMinUsableSpace(long databaseDirectorySize) {
        return databaseDirectorySize * MIN_USABLE_SPACE_FACTOR;
    }

    private Path getDumpsRootDirectory() {
        return Path.of(
                config.get(GraphDatabaseSettings.database_dumps_root_path).toString());
    }

    private void dumpDatabase(String databaseName, Path dumpDirectory) throws IOException {
        try {
            userLog.info("Dumping database %s to %s", databaseName, dumpDirectory);
            Path dumpFile = dumpDirectory.resolve(getDumpFileName(databaseName));
            boolean mkdirsSuccess = dumpDirectory.toFile().mkdirs();
            if (!mkdirsSuccess && !dumpDirectory.toFile().exists()) {
                throw new IOException(format("Failed to create dump directory at %s", dumpDirectory));
            }
            if (dumpFile.toFile().exists()) {
                userLog.info("Found old dump file %s, removing it", dumpFile);
                boolean deleteSuccess = dumpFile.toFile().delete();
                if (!deleteSuccess) {
                    throw new IOException(format("Failed to delete old dump file at %s", dumpFile));
                }
            }
            int exitCode = new CommandLine(DumpCommand.class, new ContextInjectingFactory(ctx))
                    .execute(databaseName, "--to-path", dumpDirectory.toString());
            if (exitCode != 0) {
                throw new RuntimeException(format("Dump command exited with code %d", exitCode));
            }
            userLog.info("Database %s dumped to %s", databaseName, dumpDirectory);
        } finally {
            userLog.info("Dump logs:%n %s", ctx.getExecutionOutput());
        }
    }

    private void doOnStoppedDatabase(
            DatabaseContext databaseContext, boolean restartDatabaseAfterMigration, Callable<Void> action)
            throws Exception {
        String databaseName = databaseContext.database().getNamedDatabaseId().name();
        if (databaseContext.database().isStarted()) {
            userLog.info("Stopping database %s", databaseName);
            databaseContext.database().stop();
            databaseContext.database().shutdown();
            userLog.info("Database %s stopped", databaseName);
        } else {
            userLog.info("Database %s is already stopped", databaseName);
        }
        try {
            action.call();
        } finally {
            if (restartDatabaseAfterMigration) {
                startDatabase(databaseContext);
            }
        }
    }

    private void onStepStarted() {
        UpdateMigrationStatusMessage msg = ctx.newUpdateMessage();
        msg.startedAt = Instant.now();

        apiConnector.call(msg, Upstream.Endpoint.MIGRATION_UPDATE_STATUS);
    }

    private void onStepSuccess() {
        UpdateMigrationStatusMessage msg = ctx.newUpdateMessageWithLogs();
        msg.completedAt = Instant.now();

        apiConnector.call(msg, Upstream.Endpoint.MIGRATION_UPDATE_STATUS);
    }

    private void onStepError(Throwable exception) {
        UpdateMigrationStatusMessage msg = ctx.newUpdateMessageWithLogs();
        msg.completedAt = Instant.now();
        msg.error = new UpdateMigrationStatusMessage.MigrationStepError(exception);

        apiConnector.call(msg, Upstream.Endpoint.MIGRATION_UPDATE_STATUS);
    }

    private MigrationTokenResponse getMigrationImportToken() {
        return apiConnector.call(
                ctx.newMigrationTokenRequest(), Upstream.Endpoint.MIGRATION_IMPORT_TOKEN, MigrationTokenResponse.class);
    }
}

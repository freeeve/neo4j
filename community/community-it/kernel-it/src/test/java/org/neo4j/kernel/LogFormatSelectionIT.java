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
package org.neo4j.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.configuration.GraphDatabaseSettings.preallocate_logical_logs;
import static org.neo4j.test.UpgradeTestUtil.assertKernelVersion;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.database.DbmsRuntimeVersion;
import org.neo4j.graphdb.Transaction;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.kernel.impl.transaction.log.LogFormatVersionProvider;
import org.neo4j.kernel.impl.transaction.log.entry.LogFormat;
import org.neo4j.kernel.impl.transaction.log.files.LogFiles;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.LatestVersions;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.UpgradeTestUtil;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.Neo4jLayoutExtension;

@Neo4jLayoutExtension
class LogFormatSelectionIT {

    @Inject
    private Neo4jLayout neo4jLayout;

    private TestDatabaseManagementServiceBuilder builder;
    private DatabaseManagementService managementService;

    @AfterEach
    void shutdown() {
        if (managementService != null) {
            managementService.shutdown();
            managementService = null;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_DATABASE_NAME, SYSTEM_DATABASE_NAME})
    void logFormatProviderShouldBeUpdatedOnUpgrade(String dbName) throws Throwable {
        ZippedStoreCommunity.REC_AF11_V50_ALL.unzip(neo4jLayout.homeDirectory());

        createBuilderNoAutomaticUpgrade();
        managementService = builder.build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) managementService.database(dbName);
        assertKernelVersionAndLogFormat(db, KernelVersion.V5_0);

        UpgradeTestUtil.upgradeDatabase(
                managementService, db, KernelVersion.V5_0, LatestVersions.LATEST_KERNEL_VERSION);
        assertKernelVersionAndLogFormat(db, LatestVersions.LATEST_KERNEL_VERSION);
        LogFiles logFiles = db.getDependencyResolver().resolveDependency(LogFiles.class);
        shutdown();
        checkLogFormatOfLatestFiles(logFiles, LatestVersions.LATEST_KERNEL_VERSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_DATABASE_NAME, SYSTEM_DATABASE_NAME})
    void upgradeToFuture(String dbName) throws IOException {
        createBuilder();
        managementService = builder.build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) managementService.database(dbName);

        // Some data
        try (Transaction tx = db.beginTx()) {
            tx.createNode();
            tx.commit();
        }

        managementService.shutdown();
        createBuilderNoAutomaticUpgrade();
        managementService = configureGloriousFutureAsLatest(builder).build();
        db = (GraphDatabaseAPI) managementService.database(dbName);

        assertKernelVersionAndLogFormat(db, LatestVersions.LATEST_KERNEL_VERSION);

        UpgradeTestUtil.upgradeDatabase(
                managementService, db, LatestVersions.LATEST_KERNEL_VERSION, KernelVersion.GLORIOUS_FUTURE);
        assertKernelVersionAndLogFormat(db, KernelVersion.GLORIOUS_FUTURE);

        LogFiles logFiles = db.getDependencyResolver().resolveDependency(LogFiles.class);
        shutdown();
        checkLogFormatOfLatestFiles(logFiles, KernelVersion.GLORIOUS_FUTURE);
    }

    private static void checkLogFormatOfLatestFiles(LogFiles logFiles, KernelVersion gloriousFuture)
            throws IOException {
        assertThat(logFiles.getLogFile()
                        .extractHeader(logFiles.getLogFile().getHighestLogVersion())
                        .getLogFormatVersion())
                .isEqualTo(LogFormat.fromKernelVersion(gloriousFuture));
        assertThat(logFiles.getCheckpointFile()
                        .extractHeader(logFiles.getCheckpointFile().getHighestLogVersion())
                        .getLogFormatVersion())
                .isEqualTo(LogFormat.fromKernelVersion(gloriousFuture));
    }

    private TestDatabaseManagementServiceBuilder configureGloriousFutureAsLatest(
            TestDatabaseManagementServiceBuilder builder) {
        return builder.setConfig(
                        GraphDatabaseInternalSettings.latest_runtime_version,
                        DbmsRuntimeVersion.GLORIOUS_FUTURE.getVersion())
                .setConfig(
                        GraphDatabaseInternalSettings.latest_kernel_version, KernelVersion.GLORIOUS_FUTURE.version());
    }

    void assertKernelVersionAndLogFormat(GraphDatabaseAPI db, KernelVersion expectedKernelVersion) {
        assertKernelVersion(db, expectedKernelVersion);
        assertThat(db.getDependencyResolver()
                        .resolveDependency(LogFormatVersionProvider.class)
                        .getCurrentLogFormat())
                .isEqualTo(LogFormat.fromKernelVersion(expectedKernelVersion));
    }

    private void createBuilder() {
        if (builder == null) {
            builder = new TestDatabaseManagementServiceBuilder(neo4jLayout)
                    .setConfig(preallocate_logical_logs, false)
                    .setConfig(GraphDatabaseSettings.keep_logical_logs, "keep_all");
        }
    }

    private void createBuilderNoAutomaticUpgrade() {
        if (builder == null) {
            builder = new TestDatabaseManagementServiceBuilder(neo4jLayout)
                    .setConfig(preallocate_logical_logs, false)
                    .setConfig(GraphDatabaseSettings.keep_logical_logs, "keep_all")
                    .setConfig(GraphDatabaseInternalSettings.automatic_upgrade_enabled, false);
        }
    }
}

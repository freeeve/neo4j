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
package org.neo4j.graphdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.consistency.ConsistencyCheckService;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.database.Database;
import org.neo4j.kernel.database.DatabaseLifeShutdownCoordinator;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.LifecycleStatus;
import org.neo4j.test.OtherThreadExecutor;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;
import org.neo4j.util.concurrent.BinaryLatch;

@TestDirectoryExtension
class IdRollbackOnShutdownIT {

    @Inject
    TestDirectory directory;

    @Test
    void shouldNotWriteToIdGeneratorOnRollbackAfterShutdown() throws Exception {
        // Given
        DatabaseManagementService dbms = new TestDatabaseManagementServiceBuilder(directory.homePath())
                .setConfig(GraphDatabaseSettings.shutdown_transaction_end_timeout, Duration.ofMillis(1))
                .setConfig(
                        GraphDatabaseInternalSettings.shutdown_terminated_transaction_wait_timeout,
                        Duration.ofMillis(1))
                .build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) dbms.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);

        BinaryLatch waitTx = new BinaryLatch();
        BinaryLatch waitShutdown = new BinaryLatch();
        BinaryLatch continueShutdown = new BinaryLatch();
        DatabaseLayout layout = db.databaseLayout();
        LifeSupport life =
                db.getDependencyResolver().resolveDependency(Database.class).getLife();

        // When
        life.addLifecycleListener((instance, from, to) -> {
            if (instance instanceof DatabaseLifeShutdownCoordinator
                    && LifecycleStatus.SHUTTING_DOWN == from
                    && LifecycleStatus.SHUTDOWN == to) {
                waitShutdown.release();
                continueShutdown.await();
            }
        });

        try (OtherThreadExecutor executor = new OtherThreadExecutor("test")) {
            Future<Object> future = executor.executeDontWait(() -> {
                try (var tx = db.beginTx()) {
                    tx.createNode();
                    // We have an ID, lets allow shutdown
                    waitTx.release();
                    // Wait for shutdown checkpoint to run
                    waitShutdown.await();
                    // Then roll back the TX
                    tx.rollback();
                } finally {
                    // Then let the shutdown continue (close all files etc...)
                    continueShutdown.release();
                }
                return null;
            });

            // Wait for TX to start and allocate an ID
            waitTx.await();
            // Then shut down
            dbms.shutdown();

            // Then
            ConsistencyCheckService.Result result = new ConsistencyCheckService(layout)
                    .with(Config.defaults(GraphDatabaseSettings.pagecache_memory, ByteUnit.mebiBytes(8)))
                    .with(directory.getFileSystem())
                    .with(directory.homePath())
                    .runFullConsistencyCheck();

            assertThat(result.isSuccessful()).as(result.summary().toString()).isTrue();
            assertThatThrownBy(future::get).rootCause().isInstanceOfAny(DatabaseShutdownException.class);
        }
    }
}

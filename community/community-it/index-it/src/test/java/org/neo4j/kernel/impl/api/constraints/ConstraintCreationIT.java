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
package org.neo4j.kernel.impl.api.constraints;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.neo4j.test.LatestVersions.LATEST_KERNEL_VERSION;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.ConstraintViolationException;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.ConstraintCreator;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.kernel.api.IndexMonitor;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.database.DatabaseMonitors;
import org.neo4j.kernel.impl.api.index.IndexProviderMap;
import org.neo4j.kernel.impl.api.index.IndexingService;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.impl.locking.forseti.ForsetiClient;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.Barrier;
import org.neo4j.test.OtherThreadExecutor;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.token.TokenHolders;

@DbmsExtension
class ConstraintCreationIT {
    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private IndexProviderMap indexProviderMap;

    @Inject
    private TokenHolders tokenHolders;

    @Inject
    private IndexingService indexingService;

    @Inject
    private DatabaseMonitors databaseMonitors;

    private static final Label LABEL = Label.label("label1");
    private long indexId;
    private long nbrIndexesOnStart;

    @BeforeEach
    void setUp() {
        try (Transaction tx = db.beginTx()) {
            nbrIndexesOnStart = Iterables.count(tx.schema().getIndexes());
            // The id the index belonging to the constraint should get
            indexId = nbrIndexesOnStart + 1;
        }
    }

    @Test
    void shouldNotLeaveNativeIndexFilesHangingAroundIfConstraintCreationFails() {
        // given
        attemptAndFailConstraintCreation();

        // then
        Path indexDir = indexProviderMap
                .getDefaultProvider(LATEST_KERNEL_VERSION)
                .directoryStructure()
                .directoryForIndex(indexId);

        assertFalse(Files.exists(indexDir));
    }

    @Test
    void conflictInValidationShouldNotCrashShowIndexes() throws Exception {
        try (Transaction tx = db.beginTx()) {
            tx.createNode(LABEL).setProperty("prop", true);
            tx.commit();
        }
        int labelId = tokenHolders.labelTokens().getIdByName(LABEL.name());

        Barrier.Control populationDone = new Barrier.Control();
        databaseMonitors.addMonitorListener(new IndexMonitor.MonitorAdapter() {
            @Override
            public void populationCompleteOn(IndexDescriptor descriptor) {
                populationDone.reached();
            }
        });
        try (OtherThreadExecutor executor = new OtherThreadExecutor("CreateConstraint")) {
            Future<Object> future = executor.executeDontWait(() -> {
                try (Transaction tx = db.beginTx()) {
                    ConstraintCreator creator = tx.schema().constraintFor(LABEL).assertPropertyIsUnique("prop");
                    assertThatThrownBy(creator::create).isInstanceOf(ConstraintViolationException.class);
                }
                return null;
            });
            populationDone.await();
            try (InternalTransaction readTx = (InternalTransaction) db.beginTx()) {
                readTx.kernelTransaction().locks().acquireSharedLabelLock(labelId);
                try (InternalTransaction conflictCreationTx = (InternalTransaction) db.beginTx()) {
                    // Take lock eagerly
                    conflictCreationTx.kernelTransaction().locks().acquireSharedLabelLock(labelId);
                    populationDone.release();
                    // Await constraint validation blocked by above lock
                    executor.waitUntilWaiting(details -> details.isAt(ForsetiClient.class, "acquireExclusive"));

                    // Create conflict
                    conflictCreationTx.createNode(LABEL).setProperty("prop", true);
                    conflictCreationTx.commit();
                }

                // Now the constraint is waiting for validation (to find the conflict). Let's check the status
                assertThatCode(() -> readTx.execute("SHOW INDEXES YIELD *").resultAsString())
                        .doesNotThrowAnyException();
            }
            future.get();
        }
    }

    private void attemptAndFailConstraintCreation() {
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < 2; i++) {
                Node node1 = tx.createNode(LABEL);
                node1.setProperty("prop", true);
            }

            tx.commit();
        }

        // when
        assertThrows(ConstraintViolationException.class, () -> {
            try (Transaction tx = db.beginTx()) {
                tx.schema().constraintFor(LABEL).assertPropertyIsUnique("prop").create();
                tx.commit();
            }
        });

        // then
        try (Transaction tx = db.beginTx()) {
            assertEquals(nbrIndexesOnStart, Iterables.count(tx.schema().getIndexes()));
        }
    }
}

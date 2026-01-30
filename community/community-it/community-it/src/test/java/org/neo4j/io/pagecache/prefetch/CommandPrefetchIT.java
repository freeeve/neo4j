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
package org.neo4j.io.pagecache.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.test.extension.SkipOnSpd.Note.incompatible;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.neo4j.collection.Dependencies;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.internal.recordstorage.RecordStorageEngineFactory;
import org.neo4j.io.layout.recordstorage.RecordDatabaseFile;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.storageengine.api.StorageEngineFactory;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.SkipOnSpd;

@ImpermanentDbmsExtension(configurationCallback = "configure")
@SkipOnSpd(
        notes = incompatible,
        reason = "No prefetching on the leader makes this test incompatible. Covered by spd specific tests")
class CommandPrefetchIT {

    @Inject
    GraphDatabaseService db;

    TestPrefetcher prefetcher;

    @ExtensionCallback
    void configure(TestDatabaseManagementServiceBuilder builder) {
        prefetcher = new TestPrefetcher();
        builder.setExternalDependencies(Dependencies.dependenciesOf(prefetcher));
        builder.setConfig(GraphDatabaseInternalSettings.prefetch_on_commit, true);
    }

    @Test
    void transactionCommitSubmitsEntityFilesToPrefetcher() {
        prefetcher.tasks.clear();
        try (var tx = db.beginTx()) {
            tx.createNode().createRelationshipTo(tx.createNode(), RelationshipType.withName("name"));
            tx.commit();
        }
        String databaseName = db.databaseName();
        List<String> expected = ((GraphDatabaseAPI) db)
                                .getDependencyResolver()
                                .resolveDependency(StorageEngineFactory.class)
                                .id()
                        == RecordStorageEngineFactory.ID
                ? List.of(RecordDatabaseFile.NODE_STORE.getName(), RecordDatabaseFile.RELATIONSHIP_STORE.getName())
                : List.of("block.x1.db");
        var prefetchedFiles = prefetcher.tasks.stream()
                .filter(file -> file.toString().contains(databaseName))
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
        assertThat(prefetchedFiles).containsAll(expected);
    }

    @Test
    void transactionCommitSubmitsPropertyFilesPrefetcher() {
        prefetcher.tasks.clear();
        try (var tx = db.beginTx()) {
            Node node = tx.createNode();
            node.setProperty("property", "property");
            node.setProperty("largeStringProperty", "X".repeat(1024));
            node.setProperty("largeArrayProperty", new long[1024]);
            tx.commit();
        }
        String databaseName = db.databaseName();
        List<String> expected = ((GraphDatabaseAPI) db)
                                .getDependencyResolver()
                                .resolveDependency(StorageEngineFactory.class)
                                .id()
                        == RecordStorageEngineFactory.ID
                ? List.of(
                        RecordDatabaseFile.PROPERTY_STORE.getName(),
                        RecordDatabaseFile.PROPERTY_ARRAY_STORE.getName(),
                        RecordDatabaseFile.PROPERTY_STRING_STORE.getName())
                : List.of("block.x1.db", "block.big_values.db");
        var prefetchedFiles = prefetcher.tasks.stream()
                .filter(file -> file.toString().contains(databaseName))
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
        assertThat(prefetchedFiles).containsAll(expected);
    }

    private static class TestPrefetcher extends LifecycleAdapter implements PagePrefetcher {

        private final ConcurrentLinkedQueue<Path> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void submit(Path path, long[] pages) {
            if (path.toString().contains(NamedDatabaseId.SYSTEM_DATABASE_NAME)) {
                return;
            }
            tasks.offer(path);
        }
    }
}

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
package org.neo4j.kernel.impl.api.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.consistency.ConsistencyCheckService;
import org.neo4j.consistency.checking.ConsistencyCheckIncompleteException;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.ConstraintViolationException;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.DeadlockDetectedException;
import org.neo4j.test.Race;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.utils.TestDirectory;
import org.neo4j.values.storable.ValueType;

@DbmsExtension(configurationCallback = "configure")
@RandomSupportExtension
class ParallelIndexUpdatesIT {
    @Inject
    private TestDirectory directory;

    @Inject
    private FileSystemAbstraction fs;

    @Inject
    private DatabaseManagementService dbms;

    @Inject
    private GraphDatabaseService db;

    @Inject
    private DatabaseLayout databaseLayout;

    @Inject
    private RandomSupport random;

    @ExtensionCallback
    void configure(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(GraphDatabaseInternalSettings.parallel_index_updates_apply, true);
    }

    /**
     * This test first and foremost tests so that parallel index apply produces correct results,
     * but more specifically it exercises parallel application where each transaction updates multiple indexes -
     * where there was an issue in IndexingService#applyUpdates() causing deadlocks between index writers
     * due to writers for multiple being kept open and updated back and forth.
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16})
    void shouldApplyIndexUpdatesInParallelOnManyIndexes(int threadCount) throws ConsistencyCheckIncompleteException {
        // given
        int numTokens = 10;
        List<String> keys = IntStream.range(0, numTokens).mapToObj(i -> "p" + i).toList();
        List<Label> labels = IntStream.range(0, numTokens)
                .mapToObj(i -> Label.label("L" + i))
                .toList();
        List<RelationshipType> relationshipTypes = IntStream.range(0, numTokens)
                .mapToObj(i -> RelationshipType.withName("T" + i))
                .toList();
        createManyIndexes(keys, labels, relationshipTypes);

        // when
        createData(keys, labels, relationshipTypes, threadCount, false, false);

        // then all should look good
        dbms.shutdown();
        var result = new ConsistencyCheckService(databaseLayout)
                .with(fs)
                .with(directory.file("report"))
                .runFullConsistencyCheck();
        assertThat(result.isSuccessful()).isTrue();
    }

    /**
     * This test is similar to shouldApplyIndexUpdatesInParallelOnManyIndexes, but with fewer keys,
     * which means fewer indexes in total. Each transaction will trigger updates to all indexes
     * to test application to same index in parallel.
     */
    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20})
    void shouldApplyIndexUpdatesInParallelLessKeys(int threadCount) throws ConsistencyCheckIncompleteException {
        // given
        int numTokens = 3;
        List<String> keys = IntStream.range(0, numTokens).mapToObj(i -> "p" + i).toList();
        List<Label> labels = IntStream.range(0, numTokens)
                .mapToObj(i -> Label.label("L" + i))
                .toList();
        List<RelationshipType> relationshipTypes = IntStream.range(0, numTokens)
                .mapToObj(i -> RelationshipType.withName("T" + i))
                .toList();
        createAllIndexes(keys, labels, relationshipTypes);

        // when
        createData(keys, labels, relationshipTypes, threadCount, true, false);

        // then all should look good
        dbms.shutdown();
        var result = new ConsistencyCheckService(databaseLayout)
                .with(fs)
                .with(directory.file("report"))
                .runFullConsistencyCheck();
        assertThat(result.isSuccessful()).isTrue();
    }

    /**
     * Testing that parallel application works when adding many properties with the same value
     */
    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20})
    void shouldApplyIndexUpdatesInParallelSameValue(int threadCount) throws ConsistencyCheckIncompleteException {
        // given
        int numTokens = 3;
        List<String> keys = IntStream.range(0, numTokens).mapToObj(i -> "p" + i).toList();
        List<Label> labels = IntStream.range(0, numTokens)
                .mapToObj(i -> Label.label("L" + i))
                .toList();
        List<RelationshipType> relationshipTypes = IntStream.range(0, numTokens)
                .mapToObj(i -> RelationshipType.withName("T" + i))
                .toList();
        createAllIndexes(keys, labels, relationshipTypes);

        // when
        createData(keys, labels, relationshipTypes, threadCount, true, true);

        // then all should look good
        dbms.shutdown();
        var result = new ConsistencyCheckService(databaseLayout)
                .with(fs)
                .with(directory.file("report"))
                .runFullConsistencyCheck();
        assertThat(result.isSuccessful()).isTrue();
    }

    /**
     * Testing that we can handle both additions and removals to the index in parallel.
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16})
    void shouldHandleParallelIndexUpdatesOnManyIndexes(int threadCount) throws ConsistencyCheckIncompleteException {
        // given
        int numTokens = 10;
        List<String> keys = IntStream.range(0, numTokens).mapToObj(i -> "p" + i).toList();
        List<Label> labels = IntStream.range(0, numTokens)
                .mapToObj(i -> Label.label("L" + i))
                .toList();
        List<RelationshipType> relationshipTypes = IntStream.range(0, numTokens)
                .mapToObj(i -> RelationshipType.withName("T" + i))
                .toList();
        createManyIndexes(keys, labels, relationshipTypes);

        // when
        createData(keys, labels, relationshipTypes, threadCount, false, false);

        // and then update some data
        updateData(keys, labels, relationshipTypes, threadCount);

        // then all should look good
        dbms.shutdown();
        var result = new ConsistencyCheckService(databaseLayout)
                .with(fs)
                .with(directory.file("report"))
                .runFullConsistencyCheck();
        assertThat(result.isSuccessful()).isTrue();
    }

    /**
     * Testing that we can handle both small and long-running transactions in parallel
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16})
    void shouldHandleParallelIndexUpdatesWithLongRunningTransactions(int threadCount) throws Throwable {
        // given
        int numTokens = 10;
        List<String> keys = IntStream.range(0, numTokens).mapToObj(i -> "p" + i).toList();
        List<Label> labels = IntStream.range(0, numTokens)
                .mapToObj(i -> Label.label("L" + i))
                .toList();
        List<RelationshipType> relationshipTypes = IntStream.range(0, numTokens)
                .mapToObj(i -> RelationshipType.withName("T" + i))
                .toList();
        createManyIndexes(keys, labels, relationshipTypes);

        // when
        createData(keys, labels, relationshipTypes, threadCount, false, false);

        var race = new Race();
        race.addContestants(8, () -> createTx(keys, labels, relationshipTypes, 200, false, false), 50);
        var completion = race.goAsync();

        // and then update some data
        updateData(keys, labels, relationshipTypes, threadCount);

        completion.await(10, TimeUnit.MINUTES);
        // then all should look good
        dbms.shutdown();
        var result = new ConsistencyCheckService(databaseLayout)
                .with(fs)
                .with(directory.file("report"))
                .runFullConsistencyCheck();
        assertThat(result.isSuccessful()).isTrue();
    }

    private void createData(
            List<String> keys,
            List<Label> labels,
            List<RelationshipType> relTypes,
            int threads,
            boolean allKeys,
            boolean sameValue) {
        int numTransactionsPerThread = 200;
        var race = new Race();
        race.addContestants(
                threads, () -> createTx(keys, labels, relTypes, 10, allKeys, sameValue), numTransactionsPerThread);
        race.goUnchecked();
    }

    private void updateData(List<String> keys, List<Label> labels, List<RelationshipType> relTypes, int threads) {
        int numTransactionsPerThread = 200;
        var race = new Race();
        race.addContestants(
                threads,
                () -> {
                    var prob = random.nextFloat();
                    if (prob < 0.3) {
                        createTx(keys, labels, relTypes, 10, false, false);
                    } else {
                        updateTx(labels, relTypes);
                    }
                },
                numTransactionsPerThread);
        race.goUnchecked();
    }

    private void createTx(
            List<String> keys,
            List<Label> labels,
            List<RelationshipType> relTypes,
            int numNodesPerTx,
            boolean allKeys,
            boolean sameValue) {
        try (var tx = db.beginTx()) {
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < numNodesPerTx; i++) {
                var node = tx.createNode(random.selection(labels.toArray(new Label[0]), 1, labels.size(), false));
                setRandomProperties(keys, node, allKeys, sameValue);
                nodes.add(node);
            }
            for (int i = 0; i < numNodesPerTx; i++) {
                var startNode = random.among(nodes);
                var endNode = random.among(nodes);
                var relationship = startNode.createRelationshipTo(endNode, random.among(relTypes));
                setRandomProperties(keys, relationship, allKeys, sameValue);
            }
            tx.commit();
        }
    }

    private void updateTx(List<Label> labels, List<RelationshipType> relTypes) {
        int numNodesPerTx = 10;
        try (var tx = db.beginTx()) {
            for (int i = 0; i < numNodesPerTx; i++) {
                var label = random.among(labels);
                var node = random.among(tx.findNodes(label).stream().toList());
                var props = node.getAllProperties();
                if (!props.isEmpty()) {
                    var propKey = random.among(props).getKey();
                    node.removeProperty(propKey);
                }
            }
            for (int i = 0; i < numNodesPerTx; i++) {
                var relType = random.among(relTypes);
                var relationship =
                        random.among(tx.findRelationships(relType).stream().toList());
                var props = relationship.getAllProperties();
                if (!props.isEmpty()) {
                    var propKey = random.among(props).getKey();
                    relationship.removeProperty(propKey);
                }
            }
            tx.commit();
        } catch (DeadlockDetectedException e) {
            // This can happen due to the way we update nodes and relationships in random order, ignore
        }
    }

    private void setRandomProperties(List<String> keys, Entity entity, boolean allKeyUpdates, boolean sameValue) {
        var valueTypes = new ValueType[] {ValueType.INT, ValueType.STRING, ValueType.DATE_TIME};
        var keyArray = keys.toArray(new String[0]);
        var keysToUpdate = allKeyUpdates ? keyArray : random.selection(keyArray, 1, keys.size(), false);
        for (var key : keysToUpdate) {
            var value =
                    sameValue ? key : random.nextValue(random.among(valueTypes)).asObjectCopy();
            entity.setProperty(key, value);
        }
    }

    private void createManyIndexes(List<String> keys, List<Label> labels, List<RelationshipType> relationshipTypes) {
        int maxIndexes = keys.size() * labels.size() + keys.size() * relationshipTypes.size();
        int numIndexes = random.nextInt(maxIndexes / 2, maxIndexes);
        try (var tx = db.beginTx()) {
            for (int i = 0; i < numIndexes; i++) {
                var indexCreator = random.nextBoolean()
                        ? tx.schema().indexFor(random.among(labels))
                        : tx.schema().indexFor(random.among(relationshipTypes));
                indexCreator = indexCreator.withName("MyIndex" + i).on(random.among(keys));
                try {
                    indexCreator.create();
                } catch (ConstraintViolationException e) {
                    // This is OK, since rng is bound to produce some duplicates
                }
            }
            tx.commit();
        }
        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(10, TimeUnit.SECONDS);
        }
    }

    private void createAllIndexes(List<String> keys, List<Label> labels, List<RelationshipType> relationshipTypes) {
        try (var tx = db.beginTx()) {
            for (var key : keys) {
                for (var label : labels) {
                    tx.schema()
                            .indexFor(label)
                            .withName(label.name() + key)
                            .on(key)
                            .create();
                }
                for (var relType : relationshipTypes) {
                    tx.schema()
                            .indexFor(relType)
                            .withName(relType.name() + key)
                            .on(key)
                            .create();
                }
            }
            tx.commit();
        }
        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(10, TimeUnit.SECONDS);
        }
    }
}

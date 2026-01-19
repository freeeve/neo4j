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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.internal.helpers.collection.Iterables.single;

import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.kernel.impl.MyRelTypes;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;

@ImpermanentDbmsExtension
class DenseNodeIT {
    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private Config config;

    @Test
    void testBringingNodeOverDenseThresholdIsConsistent() {
        // GIVEN
        String rootId;
        try (Transaction tx = db.beginTx()) {
            Node root = tx.createNode();
            rootId = root.getElementId();
            createRelationshipsOnNode(tx, root, 40);
            tx.commit();
        }

        // WHEN
        try (Transaction tx = db.beginTx()) {
            Node root = tx.getNodeByElementId(rootId);
            createRelationshipsOnNode(tx, root, 60);

            // THEN
            assertEquals(100, root.getDegree());
            assertEquals(100, root.getDegree(Direction.OUTGOING));
            assertEquals(0, root.getDegree(Direction.INCOMING));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type0")));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type1")));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type2")));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type3")));
            tx.commit();
        }

        try (Transaction tx = db.beginTx()) {
            Node root = tx.getNodeByElementId(rootId);

            assertEquals(100, root.getDegree());
            assertEquals(100, root.getDegree(Direction.OUTGOING));
            assertEquals(0, root.getDegree(Direction.INCOMING));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type0")));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type1")));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type2")));
            assertEquals(25, root.getDegree(RelationshipType.withName("Type3")));
            tx.commit();
        }
    }

    @Test
    void deletingRelationshipsFromDenseNodeIsConsistent() {
        // GIVEN
        String rootId;
        try (Transaction tx = db.beginTx()) {
            Node root = tx.createNode();
            rootId = root.getElementId();
            createRelationshipsOnNode(tx, root, 100);
            tx.commit();
        }

        try (Transaction tx = db.beginTx()) {
            Node root = tx.getNodeByElementId(rootId);
            deleteRelationshipsFromNode(root, 80);

            assertEquals(20, root.getDegree());
            assertEquals(20, root.getDegree(Direction.OUTGOING));
            assertEquals(0, root.getDegree(Direction.INCOMING));
            tx.commit();
        }

        try (Transaction tx = db.beginTx()) {
            Node root = tx.getNodeByElementId(rootId);
            assertEquals(20, root.getDegree());
            assertEquals(20, root.getDegree(Direction.OUTGOING));
            assertEquals(0, root.getDegree(Direction.INCOMING));
            tx.commit();
        }
    }

    @Test
    void movingBilaterallyOfTheDenseNodeThresholdIsConsistent() {
        // GIVEN
        String rootId;
        // WHEN
        try (Transaction tx = db.beginTx()) {
            Node root = tx.createNode();
            rootId = root.getElementId();
            createRelationshipsOnNode(tx, root, 100);
            deleteRelationshipsFromNode(root, 80);

            assertEquals(20, root.getDegree());
            assertEquals(20, root.getDegree(Direction.OUTGOING));
            assertEquals(0, root.getDegree(Direction.INCOMING));

            tx.commit();
        }

        // THEN
        try (Transaction tx = db.beginTx()) {
            Node root = tx.getNodeByElementId(rootId);
            assertEquals(20, root.getDegree());
            assertEquals(20, root.getDegree(Direction.OUTGOING));
            assertEquals(0, root.getDegree(Direction.INCOMING));
            tx.commit();
        }
    }

    @Test
    void testBringingTwoConnectedNodesOverDenseThresholdIsConsistent() {
        // GIVEN
        String sourceId;
        String sinkId;
        try (Transaction tx = db.beginTx()) {
            Node source = tx.createNode();
            sourceId = source.getElementId();
            Node sink = tx.createNode();
            sinkId = sink.getElementId();
            createRelationshipsBetweenNodes(source, sink, 40);
            tx.commit();
        }

        // WHEN
        try (Transaction tx = db.beginTx()) {
            Node source = tx.getNodeByElementId(sourceId);
            Node sink = tx.getNodeByElementId(sinkId);
            createRelationshipsBetweenNodes(source, sink, 60);

            // THEN
            assertEquals(100, source.getDegree());
            assertEquals(100, source.getDegree(Direction.OUTGOING));
            assertEquals(0, source.getDegree(Direction.INCOMING));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type0")));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type1")));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type2")));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type3")));

            assertEquals(100, sink.getDegree());
            assertEquals(0, sink.getDegree(Direction.OUTGOING));
            assertEquals(100, sink.getDegree(Direction.INCOMING));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type0")));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type1")));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type2")));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type3")));
            tx.commit();
        }

        try (Transaction tx = db.beginTx()) {
            Node source = tx.getNodeByElementId(sourceId);
            Node sink = tx.getNodeByElementId(sinkId);

            assertEquals(100, source.getDegree());
            assertEquals(100, source.getDegree(Direction.OUTGOING));
            assertEquals(0, source.getDegree(Direction.INCOMING));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type0")));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type1")));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type2")));
            assertEquals(25, source.getDegree(RelationshipType.withName("Type3")));

            assertEquals(100, sink.getDegree());
            assertEquals(0, sink.getDegree(Direction.OUTGOING));
            assertEquals(100, sink.getDegree(Direction.INCOMING));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type0")));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type1")));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type2")));
            assertEquals(25, sink.getDegree(RelationshipType.withName("Type3")));
            tx.commit();
        }
    }

    @Test
    void shouldBeAbleToCreateRelationshipsInEmptyDenseNode() {
        // GIVEN
        String nodeId;
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode();
            nodeId = node.getElementId();
            createRelationshipsBetweenNodes(node, tx.createNode(), denseNodeThreshold(db) + 1);
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            Iterables.forEach(tx.getNodeByElementId(nodeId).getRelationships(), Relationship::delete);
            tx.commit();
        }

        // WHEN
        String relId;
        try (Transaction tx = db.beginTx()) {
            relId = tx.getNodeByElementId(nodeId)
                    .createRelationshipTo(tx.createNode(), MyRelTypes.TEST)
                    .getElementId();
            tx.commit();
        }

        try (Transaction tx = db.beginTx()) {
            // THEN
            Node node = tx.getNodeByElementId(nodeId);
            Relationship rel = tx.getRelationshipByElementId(relId);
            assertEquals(rel, single(node.getRelationships()));
            tx.commit();
        }
    }

    private int denseNodeThreshold(GraphDatabaseAPI db) {
        return config.get(GraphDatabaseSettings.dense_node_threshold);
    }

    private static void deleteRelationshipsFromNode(Node root, int numberOfRelationships) {
        int deleted = 0;
        try (ResourceIterable<Relationship> relationships = root.getRelationships()) {
            for (final var relationship : relationships) {
                relationship.delete();
                deleted++;
                if (deleted == numberOfRelationships) {
                    break;
                }
            }
        }
    }

    private static void createRelationshipsOnNode(Transaction tx, Node root, int numberOfRelationships) {
        for (int i = 0; i < numberOfRelationships; i++) {
            root.createRelationshipTo(tx.createNode(), RelationshipType.withName("Type" + (i % 4)))
                    .setProperty("" + i, i);
        }
    }

    private static void createRelationshipsBetweenNodes(Node source, Node sink, int numberOfRelationships) {
        for (int i = 0; i < numberOfRelationships; i++) {
            source.createRelationshipTo(sink, RelationshipType.withName("Type" + (i % 4)))
                    .setProperty("" + i, i);
        }
    }
}

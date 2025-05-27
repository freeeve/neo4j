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
package org.neo4j.kernel.impl.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.kernel.impl.AbstractNeo4jTestCase;
import org.neo4j.kernel.impl.MyRelTypes;

class Neo4jConstraintsTest extends AbstractNeo4jTestCase {
    private final String key = "testproperty";

    @Test
    void testDeleteReferenceNodeOrLastNodeIsOk() {
        for (int i = 0; i < 10; i++) {
            createNode();
        }
        try (Transaction transaction = getGraphDb().beginTx();
                ResourceIterable<Node> allNodes = transaction.getAllNodes()) {
            for (Node node : allNodes) {
                Iterables.forEach(node.getRelationships(), Relationship::delete);
                node.delete();
            }
            transaction.commit();
        }
        try (Transaction transaction = getGraphDb().beginTx();
                ResourceIterable<Node> allNodes = transaction.getAllNodes()) {
            assertThat(allNodes).hasSize(0);
            transaction.commit();
        }
    }

    @Test
    void testDeleteNodeWithRel1() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode1 = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            txNode1.createRelationshipTo(txNode2, MyRelTypes.TEST);
            txNode1.delete();
            assertThrows(Exception.class, transaction::commit);
        }
    }

    @Test
    void testDeleteNodeWithRel2() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode1 = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            txNode1.createRelationshipTo(txNode2, MyRelTypes.TEST);
            txNode2.delete();
            txNode1.delete();
            assertThrows(Exception.class, transaction::commit);
        }
    }

    @Test
    void testDeleteNodeWithRel3() {
        // make sure we can delete in wrong order
        String node0 = createNode();
        String node1 = createNode();
        String node2 = createNode();
        String rel1;
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode0 = transaction.getNodeByElementId(node0);
            var txNode1 = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            Relationship rel0 = txNode0.createRelationshipTo(txNode1, MyRelTypes.TEST);
            rel1 = txNode0.createRelationshipTo(txNode2, MyRelTypes.TEST).getElementId();
            txNode1.delete();
            rel0.delete();
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode0 = transaction.getNodeByElementId(node0);
            var txNode2 = transaction.getNodeByElementId(node2);
            var txRel1 = transaction.getRelationshipByElementId(rel1);

            txNode2.delete();
            txRel1.delete();
            txNode0.delete();
            transaction.commit();
        }
    }

    @Test
    void testCreateRelOnDeletedNode() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node1);
            txNode.delete();
            assertThrows(
                    Exception.class,
                    () -> txNode.createRelationshipTo(transaction.getNodeByElementId(node2), MyRelTypes.TEST));
        }
        try (Transaction transaction = getGraphDb().beginTx()) {
            transaction.getNodeByElementId(node2).delete();
            transaction.getNodeByElementId(node1).delete();
            transaction.commit();
        }
    }

    @Test
    void testAddPropertyDeletedNode() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);

            txNode.delete();
            assertThrows(Exception.class, () -> txNode.setProperty(key, 1));
        }
    }

    @Test
    void testRemovePropertyDeletedNode() {
        GraphDatabaseService database = getGraphDb();
        try (Transaction transaction = database.beginTx()) {
            Node node = transaction.createNode();
            node.setProperty(key, 1);
            node.delete();
            assertThrows(Exception.class, () -> {
                node.removeProperty(key);
                transaction.commit();
            });
        }
    }

    @Test
    void testChangePropertyDeletedNode() {
        String node = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node);
            txNode.setProperty(key, 1);
            txNode.delete();
            assertThrows(Exception.class, () -> {
                txNode.setProperty(key, 2);
                transaction.commit();
            });
        }
    }

    @Test
    void testAddPropertyDeletedRelationship() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode1 = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            Relationship rel = txNode1.createRelationshipTo(txNode2, MyRelTypes.TEST);
            rel.delete();
            assertThrows(Exception.class, () -> {
                rel.setProperty(key, 1);
                transaction.commit();
            });
            txNode1.delete();
            txNode2.delete();
            transaction.commit();
        }
    }

    @Test
    void testRemovePropertyDeletedRelationship() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode1 = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            Relationship rel = txNode1.createRelationshipTo(txNode2, MyRelTypes.TEST);
            rel.setProperty(key, 1);
            rel.delete();
            assertThrows(Exception.class, () -> {
                rel.removeProperty(key);
                transaction.commit();
            });
            txNode1.delete();
            txNode2.delete();
            transaction.commit();
        }
    }

    @Test
    void testChangePropertyDeletedRelationship() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            Relationship rel = txNode.createRelationshipTo(txNode2, MyRelTypes.TEST);
            rel.setProperty(key, 1);
            rel.delete();
            assertThrows(Exception.class, () -> {
                rel.setProperty(key, 2);
                transaction.commit();
            });
            txNode.delete();
            txNode2.delete();
            transaction.commit();
        }
    }

    @Test
    void testMultipleDeleteNode() {
        String node1 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node1);
            txNode.delete();
            assertThrows(Exception.class, () -> {
                txNode.delete();
                transaction.commit();
            });
        }
    }

    @Test
    void testMultipleDeleteRelationship() {
        String node1 = createNode();
        String node2 = createNode();
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode1 = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            Relationship rel = txNode1.createRelationshipTo(txNode2, MyRelTypes.TEST);
            rel.delete();
            txNode1.delete();
            txNode2.delete();
            assertThrows(Exception.class, () -> {
                rel.delete();
                transaction.commit();
            });
            transaction.commit();
        }
    }

    @Test
    void testIllegalPropertyType() {
        Node node1;
        try (Transaction tx = getGraphDb().beginTx()) {
            node1 = tx.createNode();
            assertThrows(Exception.class, () -> node1.setProperty(key, new Object()));
        }
    }

    @Test
    void testNodeRelDeleteSemantics() {
        String node1 = createNode();
        String node2 = createNode();
        String rel1Id;
        String rel2Id;
        try (Transaction transaction = getGraphDb().beginTx()) {
            var txNode = transaction.getNodeByElementId(node1);
            var txNode2 = transaction.getNodeByElementId(node2);

            Relationship rel1 = txNode.createRelationshipTo(txNode2, MyRelTypes.TEST);
            rel1Id = rel1.getElementId();
            rel2Id = txNode.createRelationshipTo(txNode2, MyRelTypes.TEST).getElementId();
            txNode.setProperty("key1", "value1");
            rel1.setProperty("key1", "value1");
            transaction.commit();
        }

        try (Transaction transaction = getGraphDb().beginTx()) {
            var node = transaction.getNodeByElementId(node1);
            var secondNode = transaction.getNodeByElementId(node2);
            var relationshipOne = transaction.getRelationshipByElementId(rel1Id);
            var relationshipTwo = transaction.getRelationshipByElementId(rel2Id);
            node.delete();
            assertThrows(NotFoundException.class, () -> node.getProperty("key1"));
            assertThrows(NotFoundException.class, () -> node.setProperty("key1", "value2"));
            assertThrows(NotFoundException.class, () -> node.removeProperty("key1"));
            secondNode.delete();
            assertThrows(NotFoundException.class, secondNode::delete);
            assertThrows(NotFoundException.class, () -> node.getProperty("key1"));
            assertThrows(NotFoundException.class, () -> node.setProperty("key1", "value2"));
            assertThrows(NotFoundException.class, () -> node.removeProperty("key1"));
            assertEquals("value1", relationshipOne.getProperty("key1"));
            relationshipOne.delete();
            assertThrows(NotFoundException.class, relationshipOne::delete);
            assertThrows(NotFoundException.class, () -> relationshipOne.getProperty("key1"));
            assertThrows(NotFoundException.class, () -> relationshipOne.setProperty("key1", "value2"));
            assertThrows(NotFoundException.class, () -> relationshipOne.removeProperty("key1"));
            assertThrows(NotFoundException.class, () -> relationshipOne.getProperty("key1"));
            assertThrows(NotFoundException.class, () -> relationshipOne.setProperty("key1", "value2"));
            assertThrows(NotFoundException.class, () -> relationshipOne.removeProperty("key1"));
            assertThrows(NotFoundException.class, () -> secondNode.createRelationshipTo(node, MyRelTypes.TEST));
            assertThrows(NotFoundException.class, () -> secondNode.createRelationshipTo(node, MyRelTypes.TEST));

            assertEquals(node, relationshipOne.getStartNode());
            assertEquals(secondNode, relationshipTwo.getEndNode());
            Node[] nodes = relationshipOne.getNodes();
            assertEquals(node, nodes[0]);
            assertEquals(secondNode, nodes[1]);
            assertEquals(secondNode, relationshipOne.getOtherNode(node));
            relationshipTwo.delete();
        }
    }
}

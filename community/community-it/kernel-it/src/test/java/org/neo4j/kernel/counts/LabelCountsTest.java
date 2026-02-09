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
package org.neo4j.kernel.counts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphdb.Label.label;

import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.token.api.TokenConstants;

@ImpermanentDbmsExtension
class LabelCountsTest {
    @Inject
    private GraphDatabaseAPI db;

    @Test
    void shouldGetNumberOfNodesWithLabel() {
        // given
        try (Transaction tx = db.beginTx()) {
            tx.createNode(label("Foo"));
            tx.createNode(label("Bar"));
            tx.createNode(label("Bar"));

            tx.commit();
        }

        // when
        long fooCount = numberOfNodesWith(label("Foo"));
        long barCount = numberOfNodesWith(label("Bar"));

        // then
        assertEquals(1, fooCount);
        assertEquals(2, barCount);
    }

    @Test
    void shouldAccountForDeletedNodes() {
        // given
        String nodeId;
        try (Transaction tx = db.beginTx()) {
            nodeId = tx.createNode(label("Foo")).getElementId();
            tx.createNode(label("Foo"));

            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            tx.getNodeByElementId(nodeId).delete();

            tx.commit();
        }

        // when
        long fooCount = numberOfNodesWith(label("Foo"));

        // then
        assertEquals(1, fooCount);
    }

    @Test
    void shouldAccountForDeletedNodesWithMultipleLabels() {
        // given
        String nodeId;
        try (Transaction tx = db.beginTx()) {
            nodeId = tx.createNode(label("Foo"), label("Bar")).getElementId();
            tx.createNode(label("Foo"));
            tx.createNode(label("Bar"));

            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            tx.getNodeByElementId(nodeId).delete();

            tx.commit();
        }

        // when
        long fooCount = numberOfNodesWith(label("Foo"));
        long barCount = numberOfNodesWith(label("Bar"));

        // then
        assertEquals(1, fooCount);
        assertEquals(1, barCount);
    }

    @Test
    void shouldAccountForAddedLabels() {
        // given
        String n1Id;
        String n2Id;
        String n3Id;
        try (Transaction tx = db.beginTx()) {
            n1Id = tx.createNode(label("Foo")).getElementId();
            n2Id = tx.createNode().getElementId();
            n3Id = tx.createNode().getElementId();

            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            tx.getNodeByElementId(n1Id).addLabel(label("Bar"));
            tx.getNodeByElementId(n2Id).addLabel(label("Bar"));
            tx.getNodeByElementId(n3Id).addLabel(label("Foo"));

            tx.commit();
        }

        // when
        long fooCount = numberOfNodesWith(label("Foo"));
        long barCount = numberOfNodesWith(label("Bar"));

        // then
        assertEquals(2, fooCount);
        assertEquals(2, barCount);
    }

    @Test
    void shouldAccountForRemovedLabels() {
        // given
        String n1Id;
        String n2Id;
        String n3Id;
        try (Transaction tx = db.beginTx()) {
            n1Id = tx.createNode(label("Foo"), label("Bar")).getElementId();
            n2Id = tx.createNode(label("Bar")).getElementId();
            n3Id = tx.createNode(label("Foo")).getElementId();

            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            tx.getNodeByElementId(n1Id).removeLabel(label("Bar"));
            tx.getNodeByElementId(n2Id).removeLabel(label("Bar"));
            tx.getNodeByElementId(n3Id).removeLabel(label("Foo"));

            tx.commit();
        }

        // when
        long fooCount = numberOfNodesWith(label("Foo"));
        long barCount = numberOfNodesWith(label("Bar"));

        // then
        assertEquals(1, fooCount);
        assertEquals(0, barCount);
    }

    /** Transactional version of {@link #countsForNode(Transaction, Label)} */
    private long numberOfNodesWith(Label label) {
        try (Transaction tx = db.beginTx()) {
            long nodeCount = countsForNode(tx, label);
            tx.commit();
            return nodeCount;
        }
    }

    /** @param label the label to get the number of nodes of, or {@code null} to get the total number of nodes. */
    private static long countsForNode(Transaction tx, Label label) {
        KernelTransaction transaction = ((InternalTransaction) tx).kernelTransaction();
        Read read = transaction.dataRead();
        int labelId;
        if (label == null) {
            labelId = TokenConstants.ANY_LABEL;
        } else {
            if (TokenConstants.NO_TOKEN == (labelId = transaction.tokenRead().nodeLabel(label.name()))) {
                return 0;
            }
        }
        return read.countsForNode(labelId);
    }
}

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
package org.neo4j.kernel.impl.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Iterator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.BranchState;

class TestCustomPathExpander extends TraversalTestBase {

    @Test
    void testCircularGraph() {
        /* Layout
         *
         * (a)---(b)===(c)---(e)
         *         \   /
         *          (d)
         */
        createGraph("a NEXT_1 b", "b NEXT_3 c", "b NEXT_2 c", "c NEXT_5 d", "c NEXT_3 e", "d NEXT_7 b");
        try (Transaction transaction = beginTx()) {
            var expander = new CustomPathExpander(true, 1);
            Iterator<Node> itr = transaction
                    .traversalDescription()
                    .expand(expander)
                    .traverse(transaction.getNodeById(node("a").getId()))
                    .nodes()
                    .iterator();
            assertOrder(transaction, itr, "a", "b", "c", "e");
            transaction.commit();
        }
        try (Transaction transaction = beginTx()) {
            var expander = new CustomPathExpander(true, 2);
            Iterator<Node> itr = transaction
                    .traversalDescription()
                    .expand(expander)
                    .traverse(transaction.getNodeById(node("a").getId()))
                    .nodes()
                    .iterator();
            assertOrder(transaction, itr, "a", "b", "c", "d");
            transaction.commit();
        }
    }

    private static void assertOrder(Transaction transaction, Iterator<Node> itr, String... names) {
        for (String name : names) {
            Node node = transaction.getNodeById(itr.next().getId());
            assertEquals(
                    getNodeWithName(transaction, name), node, "expected " + name + ", was " + node.getProperty("name"));
        }
        assertFalse(itr.hasNext());
    }

    private static class CustomPathExpander implements PathExpander<Path> {
        private final boolean isForward;
        private final int delta;
        private final int stepDelta;

        CustomPathExpander(boolean isForward, int delta) {
            this.isForward = isForward;
            this.delta = delta;
            stepDelta = (isForward ? delta : -1 * delta);
        }

        private int getStep(Relationship r) {
            return Integer.parseInt(r.getType().name().substring("NEXT_".length()));
        }

        private Relationship getLastRelationship(Path path) {
            return isForward
                    ? path.lastRelationship()
                    : path.relationships().iterator().next();
        }

        @Override
        public ResourceIterable<Relationship> expand(Path path, BranchState<Path> state) {
            Node node = isForward ? path.endNode() : path.startNode();
            var expansions = node.getRelationships().stream()
                    .filter(r -> (path.length() == 0) || getStep(r) == getStep(getLastRelationship(path)) + stepDelta)
                    .collect(Collectors.toSet());
            return ResourceIterable.of(expansions);
        }

        @Override
        public PathExpander<Path> reverse() {
            return new CustomPathExpander(!isForward, delta);
        }
    }
}

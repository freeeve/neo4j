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
package org.neo4j.cypher.cucumber.user.function;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserAggregationFunction;
import org.neo4j.procedure.UserAggregationResult;
import org.neo4j.procedure.UserAggregationUpdate;
import org.neo4j.procedure.UserFunction;

public class NodeHashFunction {

    @Context
    public Transaction transaction;

    @UserFunction("test.hash.node")
    @Description("Node hash")
    public long nodeHash(@Name("node") Object input) {
        if (input instanceof Node node) {
            return calculateNodeHash(node);
        } else {
            return 0;
        }
    }

    @UserAggregationFunction(name = "test.hash.nodeAggregation")
    public NodeHashAggregator nodeHashAggregation() {
        return new NodeHashAggregator();
    }

    @UserFunction("test.hash.tx.allNodes")
    @Description("All nodes hash")
    public long txAllNodesDegreeHash(@Name("node") Node node) {
        return transaction.getAllNodes().stream().count()
                + node.getAllProperties().size();
    }

    public static class NodeHashAggregator {
        private final AtomicInteger accumulatedHash = new AtomicInteger(0);

        @UserAggregationUpdate
        public void update(@Name("node") Object input) {
            if (input instanceof Node node) {
                accumulatedHash.addAndGet(calculateNodeHash(node));
            }
        }

        @UserAggregationResult
        public long result() {
            return accumulatedHash.get();
        }
    }

    private static int calculateNodeHash(Node node) {
        final var sortedLabelNamesHash = StreamSupport.stream(node.getLabels().spliterator(), false)
                .map(Label::name)
                .sorted()
                .toList()
                .hashCode();
        final var propsHash = node.getAllProperties().hashCode();
        return Objects.hash(sortedLabelNamesHash, propsHash);
    }
}

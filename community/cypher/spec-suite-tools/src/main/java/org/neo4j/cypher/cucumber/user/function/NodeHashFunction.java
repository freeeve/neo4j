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

import java.util.stream.StreamSupport;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

public class NodeHashFunction {

    @UserFunction("test.nodeHash")
    @Description("Node hash")
    public long nodeHash(@Name("node") Object input) {
        if (input instanceof Node node) {
            final var sortedLabelNamesHash = StreamSupport.stream(
                            node.getLabels().spliterator(), false)
                    .map(Label::name)
                    .sorted()
                    .toList()
                    .hashCode();
            final var propsHash = node.getAllProperties().hashCode();
            return ((long) sortedLabelNamesHash) + propsHash;
        } else {
            return 0;
        }
    }
}

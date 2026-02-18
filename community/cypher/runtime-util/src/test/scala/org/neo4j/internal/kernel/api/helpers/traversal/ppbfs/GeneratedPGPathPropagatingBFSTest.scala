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
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs

import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode.Trail
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.cypher.internal.util.test_helpers.InMemoryGraph
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.GeneratedPGPathPropagatingBFSTest.testGraphs

class GeneratedPGPathPropagatingBFSTest extends CypherFunSuite with PGPathPropagatingBFSTestBase {

  test(
    s"running the algorithm gives the same results as naive search"
  ) {
    for {
      nfa <- Seq(
        `(s) ((a)-->(b))* (t)`,
        `(s) ((a)-->(b))+ (t)`,
        `(s) ((a)--(b))+ (t)`,
        `(s) ((a)--(b)--(c))* (t)`,
        `(s) ((a)--(b)--(c))* (t) [single transition]`
      )
      graph <- testGraphs
      into <- Seq(true, false)
      grouped <- Seq(true, false)
      pathMode <- Seq(TraversalPathMode.Trail, TraversalPathMode.Walk)
      k <- if (pathMode == Trail) Seq(Int.MaxValue, 1, 2) else Seq(1, 2, 5)
    } {
      var f = fixture()
        .withGraph(graph.graph)
        .from(graph.source)
        .withNfa(nfa)
        .withPathMode(pathMode)

      if (pathMode == TraversalPathMode.Walk) {
        f = f.withMaxDepth(3)
      }

      if (into) {
        f = f.into(graph.target)
      }

      if (grouped) {
        f = f.grouped
      }

      if (k != Int.MaxValue) {
        f = f.withK(k)
      }

      withClue(s"\ngraph=${graph.render}\nnfa=$nfa\ninto=$into\npathMode=$pathMode\ngrouped=$grouped\nk=$k\n") {
        f.assertExpected()
      }
    }
  }
}

object GeneratedPGPathPropagatingBFSTest {

  private case class NamedGraph(render: String, graph: InMemoryGraph, source: Long, target: Long)

  /** a generated series of graphs consisting of a variable length chain of relationships from (start) to (end),
   * with another variable length chain connecting two nodes from the original */
  private lazy val testGraphs: Seq[NamedGraph] = {
    for {
      mainLength <- 1 to 4
      secondaryLength <- 1 to 3
      n1i <- 0 until mainLength
      n2i <- 0 until mainLength
    } yield {
      val builder = new InMemoryGraph.Builder

      val nodes = builder.line(mainLength)
      val start = nodes.head
      val end = nodes.last

      val n1 = nodes(n1i)
      val n2 = nodes(n2i)
      builder.chainRel(n1, n2, secondaryLength)

      NamedGraph(
        s"(n$start)-$mainLength->(n$end), (n$n1)-$secondaryLength->(n$n2)",
        builder.build(),
        start,
        end
      )
    }
  }
}

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
package org.neo4j.cypher.internal.runtime.spec.tests

import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNode
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationship
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite

object LockNodesTestBase

abstract class LockNodesTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should lock all nodes") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .lockNodes("x")
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x")
      .withRows(nodes.map(Array(_)))
      .withLockedNodes(nodes.map(_.getId).toSet, onlyCheckContains = true)
  }

  test("should not lock nodes if no result") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .lockNodes("x")
      .filter("false")
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x")
      .withNoRows()
      .withLockedNodes(Set.empty, onlyCheckContains = true)
  }

  test("should lock all nodes, duplicates and ref slots") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("xRef")
      .lockNodes("xRef")
      .unwind("[x, x, x] AS xRef")
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("xRef")
      .withRows(nodes.flatMap(n => Seq.fill(3)(Array(n))))
      .withLockedNodes(nodes.map(_.getId).toSet, onlyCheckContains = true)
  }

  test("should lock all nodes on the RHS of an apply") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .apply()
      .|.lockNodes("x")
      .|.argument("x")
      .allNodeScan("x")
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x")
      .withRows(nodes.map(Array(_)))
      .withLockedNodes(nodes.map(_.getId).toSet, onlyCheckContains = true)
  }

  test("should profile lockNodes") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .lockNodes("x")
      .allNodeScan("x")
      .build()

    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    queryProfile.operatorProfile(0).rows() shouldBe sizeHint // produce results
    queryProfile.operatorProfile(1).rows() shouldBe sizeHint // lockNodes
    queryProfile.operatorProfile(2).rows() shouldBe sizeHint // allNodeScan
  }
}

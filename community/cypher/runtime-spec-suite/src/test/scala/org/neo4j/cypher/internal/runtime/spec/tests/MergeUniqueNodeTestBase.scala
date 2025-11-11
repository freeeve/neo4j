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
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.schema.IndexType
import org.neo4j.internal.helpers.collection.Iterators
import org.neo4j.lock.LockType.EXCLUSIVE
import org.neo4j.lock.LockType.SHARED
import org.neo4j.lock.ResourceType
import org.neo4j.lock.ResourceType.INDEX_ENTRY
import org.neo4j.lock.ResourceType.LABEL

import scala.util.Random

abstract class MergeUniqueNodeTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  protected val sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should grab shared lock when finding a node") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }
    val propToFind = Random.nextInt(sizeHint)

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> s"$propToFind"))
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes(propToFind)
    runtimeResult should beColumns("x").withSingleRow(expected).withLocks(
      (SHARED, INDEX_ENTRY),
      (SHARED, LABEL)
    ).withNoUpdates()
  }

  test("should grab shared lock when finding a node (multiple properties)") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop1", "prop2")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop1" -> i, "prop2" -> s"$i")
        },
        "Honey"
      )
    }
    val propToFind = Random.nextInt(sizeHint)

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop1" -> s"$propToFind", "prop2" -> s"'$propToFind'"))
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes(propToFind)
    runtimeResult should beColumns("x").withSingleRow(expected).withLocks(
      (SHARED, INDEX_ENTRY),
      (SHARED, LABEL)
    ).withNoUpdates()
  }

  test("should grab an exclusive lock when not finding a node") {
    givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }
    val propToFind = sizeHint + 1

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> s"$propToFind"))
      .build(readOnly = false)

    // then
    execute(logicalQuery, runtime) should beColumns("x").withRows(rowCount(1)).withLocks(
      (EXCLUSIVE, ResourceType.NODE),
      (EXCLUSIVE, INDEX_ENTRY),
      (SHARED, LABEL),
      (SHARED, LABEL),
      (EXCLUSIVE, ResourceType.NODE_RELATIONSHIP_GROUP_DELETE)
    ).withStatistics(nodesCreated = 1, labelsAdded = 1, propertiesSet = 1)
    execute(logicalQuery, runtime) should beColumns("x").withRows(rowCount(1))
      .withNoUpdates()
  }

  test("should exact seek nodes of a locking unique index with a property") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 10 == 0 => Map("prop" -> i)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> "20"))
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes(20)
    runtimeResult should beColumns("x").withSingleRow(expected).withLocks(
      (SHARED, INDEX_ENTRY),
      (SHARED, LABEL)
    ).withNoUpdates()
  }

  test("should exact seek nodes of a locking composite unique index with properties") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop1", "prop2")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 10 == 0 => Map("prop1" -> i, "prop2" -> s"$i")
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .nodeIndexOperator("x:Honey(prop1 = 20, prop2 = '20')", unique = true)
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes(20)
    runtimeResult should beColumns("x").withSingleRow(expected).withLocks((SHARED, INDEX_ENTRY), (SHARED, LABEL))
  }

  test("should support composite unique index and unique locking") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop", "prop2")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 10 == 0 => Map("prop" -> i, "prop2" -> i.toString)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> "10", "prop2" -> "'10'"))
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes(10)
    runtimeResult should beColumns("x").withSingleRow(expected).withLocks((SHARED, INDEX_ENTRY), (SHARED, LABEL))
  }

  test("mergeUnique should perform on match side effect") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }
    val propToFind = Random.nextInt(sizeHint)

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode(
        "x",
        "Honey",
        Seq("prop" -> s"$propToFind"),
        onMatch = Seq("p1" -> "true", "p2" -> "42"),
        onCreate = Seq("p1" -> "false", "p2" -> "'forty-two'")
      )
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val expected = nodes(propToFind)
    expected.getProperty("p1") shouldBe true
    expected.getProperty("p2") shouldBe 42
    runtimeResult should beColumns("x").withSingleRow(expected).withStatistics(propertiesSet = 2)
  }

  test("mergeUnique should perform on create side effect") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode(
        "x",
        "Honey",
        Seq("prop" -> s"$sizeHint"),
        onMatch = Seq("p1" -> "true", "p2" -> "42"),
        onCreate = Seq("p1" -> "false", "p2" -> "'forty-two'")
      )
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val newNode = Iterators.single(tx.findNodes(Label.label("Honey"), "prop", sizeHint))
    newNode.getProperty("p1") shouldBe false
    newNode.getProperty("p2") shouldBe "forty-two"
    runtimeResult should beColumns("x").withSingleRow(newNode).withStatistics(
      nodesCreated = 1,
      labelsAdded = 1,
      propertiesSet = 3
    )
  }

  test("profile dbhits on match") {
    givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }
    val propToFind = Random.nextInt(sizeHint)

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> s"$propToFind"))
      .build(readOnly = false)

    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    queryProfile.operatorProfile(2).dbHits() should equal(2)
  }

  test("profile dbhits on create") {
    givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> s"$sizeHint"))
      .build(readOnly = false)

    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    queryProfile.operatorProfile(2).dbHits() should equal(2)
  }

  test("profile rows on match") {
    givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }
    val propToFind = Random.nextInt(sizeHint)

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> s"$propToFind"))
      .build(readOnly = false)

    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    queryProfile.operatorProfile(2).rows() should equal(1)
  }

  test("profile rows on create") {
    givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .filter("true")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> s"$sizeHint"))
      .build(readOnly = false)

    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    queryProfile.operatorProfile(2).rows() should equal(1)
  }

  test("should cache properties in mergeUniqueNode") {
    val nodes = givenGraph {
      uniqueNodeIndex(IndexType.RANGE, "Honey", "prop")
      nodeGraph(5, "Milk")
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 10 == 0 => Map("prop" -> i)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "prop")
      .projection("cache[x.prop] AS prop")
      .mergeUniqueNode("x", "Honey", Seq("prop" -> "10"), cacheValues = true)
      .build(readOnly = false)

    val runtimeResult = profile(logicalQuery, runtime)
    consume(runtimeResult)

    // then
    runtimeResult should beColumns("x", "prop").withSingleRow(nodes(10), 10)
    val queryProfile = runtimeResult.runtimeResult.queryProfile()
    queryProfile.operatorProfile(1).dbHits() shouldBe 0
  }

}

object MergeUniqueNodeTestBase

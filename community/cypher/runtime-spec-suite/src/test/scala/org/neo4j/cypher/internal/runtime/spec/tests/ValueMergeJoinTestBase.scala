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
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationship
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RecordingRuntimeResult
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.RelationshipType

import scala.jdk.CollectionConverters.IterableHasAsScala

object ValueMergeJoinTestBase

abstract class ValueMergeJoinTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  val sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  private val printTable = false

  private def printTable(runtimeResult: RecordingRuntimeResult): Unit = {
    if (printTable) {
      println(runtimeResult.table())
    } else {
      runtimeResult.awaitAll()
    }
  }

  test("should support simple merge join on cached properties") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.zipWithIndex.map { case (n, i) => Array(n.getElementId, n.getElementId, i, i) }

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support simple merge join on rhs of apply") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .apply()
      .|.valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .|.nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.zipWithIndex.map { case (n, i) => Array(n.getElementId, n.getElementId, i, i) }

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected ++ expected)
  }

  test("should support merge join under apply when rhs has more rows ") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      val aNodes = nodePropertyGraph(
        sizeHint / 2,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )

      val bNodes = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
      (aNodes, bNodes)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .apply()
      .|.valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .|.nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes._1.zip(nodes._2).zipWithIndex.map { case ((n1, n2), i) =>
      Array(n1.getElementId, n2.getElementId, i, i)
    }

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected ++ expected)
  }

  test("should support merge join under apply when lhs has more rows ") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      val aNodes = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )

      val bNodes = nodePropertyGraph(
        sizeHint / 2,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
      (aNodes, bNodes)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .apply()
      .|.valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .|.nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes._1.zip(nodes._2).zipWithIndex.map { case ((n1, n2), i) =>
      Array(n1.getElementId, n2.getElementId, i, i)
    }

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected ++ expected)
  }

  test("should support merge join under apply when lhs is empty ") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .apply()
      .|.valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .|.nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }

  test("should support merge join under apply when rhs is empty ") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .apply()
      .|.valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .|.nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }

  test("should support merge join under apply with filter ") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (aNodes, bNodes) = givenGraph {
      val a = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )

      val b = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
      (a, b)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .apply()
      .|.valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.|.filter("cache[b.prop] % x = 0")
      .|.|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .|.nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .unwind("[2, 5] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected1 = aNodes.zip(bNodes).zipWithIndex.filter { case (_, i) => i % 2 == 0 }.map { case ((a, b), i) =>
      Array(a.getElementId, b.getElementId, i, i)
    }
    val expected2 = aNodes.zip(bNodes).zipWithIndex.filter { case (_, i) => i % 5 == 0 }.map { case ((a, b), i) =>
      Array(a.getElementId, b.getElementId, i, i)
    }

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected1 ++ expected2)
  }

  test("should support skip") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.skip(30)
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .skip(10)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.zipWithIndex.drop(30).map { case (n, i) => Array(n.getElementId, n.getElementId, i, i) }

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support expand on rhs") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      val n = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
      n.map(nn => {
        val other = tx.createNode()
        nn.createRelationshipTo(other, RelationshipType.withName("HAS"))
        (nn, other)
      })
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`other.id`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "elementId(other) as `other.id`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.expandAll("(b)-[:HAS]->(other)")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.zipWithIndex.map { case ((n, other), i) =>
      Array(n.getElementId, n.getElementId, other.getElementId)
    }

    runtimeResult should beColumns("a.id", "b.id", "other.id").withRows(expected)
  }

  test("should support join where rhs pattern exists") {

    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val as = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )
      val bs = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
      bs.zipWithIndex.foreach {
        case (b, i) if i % 2 == 0 =>
          val other = tx.createNode(Label.label("C"))
          b.createRelationshipTo(other, RelationshipType.withName("HAS_C"))
        case _ =>
      }
      (as, bs)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.apply()
      .|.|.limit(1)
      .|.|.filter("c:C")
      .|.|.expandAll("(b)-[:HAS_C]->(c)")
      .|.|.argument("b")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val aprops = as.zipWithIndex
    val bprops = bs.zipWithIndex
    val expected = for {
      (a, ai) <- aprops
      (b, bi) <- bprops
      if ai == bi && bi % 2 == 0
    } yield Array(a.getElementId, b.getElementId, ai, bi)

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)

  }

  test("should join on cached properties with filter") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.filter("cache[b.prop] < 20")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .filter("cache[a.prop] < 10")
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.take(10).zipWithIndex.map { case (n, i) => Array(n.getElementId, n.getElementId, i, i) }

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should join on cached properties with more filters") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.filter("cache[b.prop] < 50")
      .|.filter("cache[b.prop] < 20 OR cache[b.prop] > 30")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .filter("cache[a.prop] <= 40 ")
      .filter("cache[a.prop] < 10 OR cache[a.prop] > 20")
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.zipWithIndex.filter {
      case (_, i) => i < 10 || (30 < i && i <= 40)
    } map { case (n, i) => Array(n.getElementId, n.getElementId, i, i) }

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should join on cached properties with limit on rhs") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.limit(20)
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected =
      nodes.take(20).map(n => Array(n.getElementId, n.getElementId, n.getProperty("prop"), n.getProperty("prop")))

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should join on cached properties with limit on lhs") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .limit(20)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected =
      nodes.take(20).map(n => Array(n.getElementId, n.getElementId, n.getProperty("prop"), n.getProperty("prop")))

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should join with limit on top") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .limit(20)
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected =
      nodes.take(20).map(n => Array(n.getElementId, n.getElementId, n.getProperty("prop"), n.getProperty("prop")))

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should join with top on top") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val nodes = givenGraph {
      nodePropertyGraph(
        20,
        {
          case i => Map("prop" -> i)
        },
        "A",
        "B"
      )
    }

    val top = 10
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .top(top, "`a.prop` ASC")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected =
      nodes.take(top).map(n => Array(n.getElementId, n.getElementId, n.getProperty("prop"), n.getProperty("prop")))

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should join with ordered aggregation on top") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val as = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 2))
        },
        "A"
      )
      val bs = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 6))
        },
        "B"
      )
      (as, bs)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "count")
      .projection(
        "elementId(a) AS `a.id`"
      )
      .orderedAggregation(Seq("a AS a"), Seq("count(b) AS `count`"), Seq("a"))
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val matchingAs = for {
      (a, ai) <- as.zipWithIndex.map { case (n, i) => (n, i - i % 2) }
      (_, bi) <- bs.zipWithIndex.map { case (n, i) => (n, i - i % 6) }
      if ai == bi
    } yield a.getElementId

    // count nodes grouped by id
    val expected = matchingAs.groupBy(identity).view.mapValues(_.size).toArray.map {
      case (aId, count) => Array(aId, count)
    }

    printTable(runtimeResult)
    runtimeResult should beColumns("a.id", "count").withRows(expected)
  }

  test("should support join where some rows match") {

    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val as = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 2))
        },
        "A"
      )
      val bs = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 3))
        },
        "B"
      )
      (as, bs)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val aprops = (0 until sizeHint).map(i => i - i % 2).zip(as)
    val bprops = (0 until sizeHint).map(i => i - i % 3).zip(bs)
    val expected = for {
      (ap, a) <- aprops
      (bp, b) <- bprops
      if ap == bp
    } yield Array(a.getElementId, b.getElementId, ap, bp)

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support join where some values are null") {

    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val as = nodePropertyGraph(
        sizeHint,
        {
          case i if i % 2 == 0 => Map("prop" -> i)
          case _               => Map.empty
        },
        "A"
      )
      val bs = nodePropertyGraph(
        sizeHint,
        {
          case i if i % 3 == 0 => Map("prop" -> i)
          case _               => Map.empty
        },
        "B"
      )
      (as, bs)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val aprops = (0 until sizeHint).zip(as)
    val bprops = (0 until sizeHint).zip(bs)
    val expected = for {
      (ap, a) <- aprops
      (bp, b) <- bprops
      if ap == bp && ap % 2 == 0 && bp % 3 == 0
    } yield Array(a.getElementId, b.getElementId, ap, bp)

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support join distinct on top") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val as = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 2))
        },
        "A"
      )
      val bs = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 3))
        },
        "B"
      )
      (as, bs)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .distinct("`a.prop` AS `a.prop`", "`b.prop` AS `b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val aprops = (0 until sizeHint).map(i => i - i % 2).zip(as).distinctBy(x => x._1)
    val bprops = (0 until sizeHint).map(i => i - i % 3).zip(bs).distinctBy(x => x._1)
    val expected = for {
      (ap, a) <- aprops
      (bp, b) <- bprops
      if ap == bp
    } yield Array(a.getElementId, b.getElementId, ap, bp)

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should create relationships") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 2))
        },
        "A"
      )
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 3))
        },
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`r.id`")
      .projection(
        "elementId(r) AS `r.id`"
      )
      .create(createRelationship("r", "a", "R", "b", OUTGOING))
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    consume(runtimeResult)

    // then
    val aprops = (0 until sizeHint).map(i => i - i % 2)
    val bprops = (0 until sizeHint).map(i => i - i % 3)
    val expected = for {
      (ap) <- aprops
      (bp) <- bprops
      if ap == bp
    } yield Array(ap)

    val x = tx.getAllRelationships.asScala.map(r => r.getElementId)
    runtimeResult should beColumns("r.id").withRows(singleColumn(x)).withStatistics(
      relationshipsCreated = expected.size
    )
  }

  test("should support join where no rows match - 1") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> (i - i % 2))
        },
        "A"
      )
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> ((i - i % 2) + 1))
        },
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }

  test("should support join where no rows match - 2") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> 0)
        },
        "A"
      )
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> 1)
        },
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }

  test("should support join where no rows match - 3") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> 1)
        },
        "A"
      )
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> 0)
        },
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }

  test("should support join when all values are equal") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val as = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> 1)
        },
        "A"
      )
      val bs = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> 1)
        },
        "B"
      )
      (as, bs)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for {
      a <- as
      b <- bs
    } yield Array(a.getElementId, b.getElementId, 1, 1)

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support join where lhs has more rows") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val aNodes = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )
      val bNodes = nodePropertyGraph(
        sizeHint / 2,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
      (aNodes, bNodes)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = as.zip(bs).zipWithIndex.map { case ((a, b), i) => Array(a.getElementId, b.getElementId, i, i) }

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support join where rhs has more rows") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    val (as, bs) = givenGraph {
      val aNodes = nodePropertyGraph(
        sizeHint / 2,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )
      val bNodes = nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
      (aNodes, bNodes)

    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = as.zip(bs).zipWithIndex.map { case ((a, b), i) => Array(a.getElementId, b.getElementId, i, i) }

    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withRows(expected)
  }

  test("should support join where lhs is empty") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "B"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns(
      "a.id",
      "b.id",
      "a.prop",
      "b.prop"
    ).withNoRows()
  }

  test("should support join where rhs is empty") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        },
        "A"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }

  test("should support join where both sides are empty") {
    // given
    nodeIndex("A", "prop")
    nodeIndex("B", "prop")
    givenGraph {
      nodePropertyGraph(
        1,
        {
          case i => Map("prop" -> i)
        },
        "C"
      )
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("`a.id`", "`b.id`", "`a.prop`", "`b.prop`")
      .projection(
        "elementId(a) AS `a.id`",
        "elementId(b) AS `b.id`",
        "cache[a.prop] AS `a.prop`",
        "cache[b.prop] AS `b.prop`"
      )
      .valueMergeJoin("cache[a.prop] = cache[b.prop]")
      .|.nodeIndexOperator("b:B(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .nodeIndexOperator("a:A(prop)", indexOrder = IndexOrderAscending, getValue = _ => GetValue)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("a.id", "b.id", "a.prop", "b.prop").withNoRows()
  }
}

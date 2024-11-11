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

import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.expressions.CollectDistinct
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation.ArgumentAsc
import org.neo4j.cypher.internal.expressions.FunctionInvocation.ArgumentDesc
import org.neo4j.cypher.internal.expressions.functions.Avg
import org.neo4j.cypher.internal.expressions.functions.Collect
import org.neo4j.cypher.internal.expressions.functions.Count
import org.neo4j.cypher.internal.expressions.functions.Max
import org.neo4j.cypher.internal.expressions.functions.Min
import org.neo4j.cypher.internal.expressions.functions.StdDev
import org.neo4j.cypher.internal.expressions.functions.StdDevP
import org.neo4j.cypher.internal.expressions.functions.Sum
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.graphdb.Node
import org.neo4j.values.storable.DoubleValue
import org.neo4j.values.storable.DurationValue
import org.neo4j.values.storable.IntegralValue
import org.neo4j.values.storable.StringValue
import org.neo4j.values.storable.Values
import org.neo4j.values.storable.Values.intValue
import org.neo4j.values.virtual.ListValue

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Collections

import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.Random

abstract class CollectDistinctTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  val sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should collectDistinct") {
    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("p" -> i % 10) }, "Honey")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Map.empty[String, Expression], Map("c" -> collectDistinct(prop("x", "p"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRows(singleRow((0 to 9).toArray))
  }

  test("should collectDistinct with limit") {
    // given
    val limit = sizeHint / 10
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i if i < limit => Map("p" -> i % 10)
          case _              => Map("p" -> "THIS SHOULD NOT BE SEEN")
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Map.empty[String, Expression], Map("c" -> collectDistinct(prop("x", "p"))))
      .limit(limit)
      .sort("x ASC")
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRows(singleRow((0 to 9).toArray))
  }

  test("should collectDistinct under apply") {
    val (aNodes, _) = givenGraph { bipartiteGraph(sizeHint, "A", "B", "R") }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .apply()
      .|.aggregation(Map.empty[String, Expression], Map("c" -> collectDistinct(varFor("a"))))
      .|.sort("a ASC")
      .|.expandAll("(a)-->(b)")
      .|.argument("a")
      .nodeByLabelScan("a", "A", IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)
    val expected = aNodes.map(n => Array(java.util.List.of(n)))
    runtimeResult should beColumns("c").withRows(expected)
  }

  test("should collectDistinct under apply when all arguments are filtered out") {
    val nodesPerLabel = 100
    val (aNodes, _) = givenGraph { bipartiteGraph(nodesPerLabel, "A", "B", "R") }
    val limit = nodesPerLabel / 2

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .apply()
      .|.aggregation(Map.empty[String, Expression], Map("c" -> collectDistinct(varFor("a"))))
      .|.limit(limit)
      .|.expandAll("(a)-->(b)")
      .|.filter("false")
      .|.argument("a")
      .nodeByLabelScan("a", "A", IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    val expected = aNodes.map(_ => Array[Any](Collections.emptyList()))

    runtimeResult should beColumns("c").withRows(expected)
  }

  test("should collectDistinct on single grouping column") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i: Int => Map("num" -> i, "name" -> s"bob${i % 10}")
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("name", "c")
      .aggregation(Map("name" -> prop("x", "name")), Map("c" -> collectDistinct(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "c").withRows(
      for (i <- 0 until 10) yield {
        Array[Any](s"bob$i", (0 until sizeHint / 10).map(j => intValue(j * 10 + i)).toArray)
      },
      listInAnyOrder = isParallel
    )
  }

  test("should collectDistinct on single grouping column with limit") {
    // given
    val groupSize = 10
    val groupCount = 2
    val input = inputValues((0 until sizeHint).map(i => Array[Any](s"bob${i / groupSize}")): _*)
    val limit = groupSize * groupCount

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("key", "c")
      .aggregation(Map("key" -> varFor("name")), Map("c" -> collectDistinct(varFor("name"))))
      .limit(limit)
      .input(variables = Seq("name"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, input)

    // then
    runtimeResult should beColumns("key", "c").withRows(for (i <- 0 until groupCount) yield {
      Array[Any](s"bob$i", Array(s"bob$i"))
    })
  }

  test("should collectDistinct on single grouping column under apply") {
    val nodesPerLabel = 100
    val (aNodes, _) = givenGraph { bipartiteGraph(nodesPerLabel, "A", "B", "R") }
    val limit = nodesPerLabel / 2

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("a", "c")
      .apply()
      .|.aggregation(Map("a" -> varFor("a")), Map("c" -> collectDistinct(varFor("a"))))
      .|.limit(limit)
      .|.expandAll("(a)-->(b)")
      .|.argument("a")
      .nodeByLabelScan("a", "A", IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    val expected = aNodes.map(a => Array[Any](a, Array(a)))

    runtimeResult should beColumns("a", "c").withRows(expected)
  }

  test("should collectDistinct on single primitive grouping column") {
    // given
    val (nodes, _) = givenGraph { circleGraph(sizeHint) }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "c")
      .aggregation(Map("x" -> varFor("x")), Map("c" -> collectDistinct(varFor("x"))))
      .expand("(x)--(y)")
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x", "c").withRows(nodes.map { node =>
      Array[Any](node, Array(node))
    })
  }

  test("should collectDistinct on single grouping column with nulls") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i: Int if i % 2 == 0 => Map("num" -> i, "name" -> s"bob${i % 10}")
          case i: Int if i % 2 == 1 => Map("num" -> i)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("name", "c")
      .aggregation(Map("name" -> prop("x", "name")), Map("c" -> collectDistinct(prop("x", "name"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "c").withRows((for (i <- 0 until 10 by 2) yield {
      Array[Any](s"bob$i", Array(s"bob$i"))
    }) :+ Array[Any](null, Array.empty[String]))
  }

  test("should collectDistinct on single primitive grouping column with nulls") {
    // given
    val (unfilteredNodes, _) = givenGraph { circleGraph(sizeHint) }
    val nodes = select(unfilteredNodes, nullProbability = 0.5)
    val input = batchedInputValues(sizeHint / 8, nodes.map(n => Array[Any](n)): _*).stream()

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "c")
      .aggregation(Map("x" -> varFor("x")), Map("c" -> collectDistinct(varFor("x"))))
      .expand("(x)--(y)")
      .input(nodes = Seq("x"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, input)

    // then
    val expected = for (node <- nodes if node != null) yield Array[Any](node, Array(node))
    runtimeResult should beColumns("x", "c").withRows(expected)
  }

  test("should collectDistinct on two grouping columns") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i: Int => Map("num" -> i, "name" -> s"bob${i % 10}", "surname" -> s"bobbins${i / 100}")
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("name", "surname", "c")
      .aggregation(
        Map("name" -> prop("x", "name"), "surname" -> prop("x", "surname")),
        Map("c" -> collectDistinct(prop("x", "num")))
      )
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "surname", "c").withRows(
      for (i <- 0 until 10; j <- 0 until sizeHint / 100)
        yield {
          Array[Any](s"bob$i", s"bobbins$j", (0 until 10).map(k => j * 100 + k * 10 + i).toArray)
        },
      listInAnyOrder = isParallel
    )
  }

  test("should collectDistinct on two primitive grouping columns with nulls") {
    // given
    val (unfilteredNodes, _) = givenGraph { circleGraph(sizeHint) }
    val nodes = select(unfilteredNodes, nullProbability = 0.5)
    val input = batchedInputValues(sizeHint / 8, nodes.map(n => Array[Any](n)): _*).stream()

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "c")
      .aggregation(Map("x" -> varFor("x"), "x2" -> varFor("x2")), Map("c" -> collectDistinct(varFor("x"))))
      .projection("x AS x2")
      .expand("(x)--(y)")
      .input(nodes = Seq("x"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, input)

    // then
    val expected = for (node <- nodes if node != null) yield Array[Any](node, Array(node))
    runtimeResult should beColumns("x", "c").withRows(expected)
  }

  test("should collectDistinct on three grouping columns") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i: Int => Map("num" -> i, "name" -> s"bob${i % 10}", "surname" -> s"bobbins${i / 100}", "dead" -> i % 2)
        },
        "Honey"
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("name", "surname", "dead", "c")
      .aggregation(
        Map("name" -> prop("x", "name"), "surname" -> prop("x", "surname"), "dead" -> prop("x", "dead")),
        Map("c" -> collectDistinct(prop("x", "num")))
      )
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "surname", "dead", "c").withRows(
      for (i <- 0 until 10; j <- 0 until sizeHint / 100)
        yield {
          Array[Any](s"bob$i", s"bobbins$j", i % 2, (0 until 10).map(k => j * 100 + k * 10 + i).toArray)
        },
      listInAnyOrder = isParallel
    )
  }
}

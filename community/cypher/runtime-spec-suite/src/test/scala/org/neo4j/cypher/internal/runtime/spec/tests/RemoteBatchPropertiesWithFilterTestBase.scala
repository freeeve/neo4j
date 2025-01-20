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
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.TrailParameters
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.cypher.internal.util.UpperBound.Limited
import org.neo4j.cypher.internal.util.test_helpers.CypherScalaCheckDrivenPropertyChecks
import org.neo4j.values.storable.PointValue
import org.scalatest.prop.TableDrivenPropertyChecks.forEvery
import org.scalatest.prop.Tables.Table

object RemoteBatchPropertiesWithFilterTestBase

abstract class RemoteBatchPropertiesWithFilterTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  val sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) with CypherScalaCheckDrivenPropertyChecks {

  test("should return nothing - on empty graph - for all predicates") {

    val predicates = Table(
      "predicate",
      "x.prop1 IS NULL",
      "x.prop1 in [1,2,3]",
      "x.prop1 in $listParam",
      "x.prop1 = 10",
      "x.prop1 = 'prop'",
      "x.prop1 = $intParam",
      "x.prop1 = $stringParam",
      "x.prop1 STARTS WITH 'prop'",
      "x.prop1 ENDS WITH 'prop'",
      "x.prop1 CONTAINS 'prop'",
      "x.prop1 IS NOT NULL",
      "x.prop1 > 10",
      "x.prop1 >= 10",
      "x.prop1 < 10",
      "x.prop1 <= 10",
      "point.withinBBox(x.prop1, point({ x:1, y:1 }), point({ x:4, y:4 }))",
      "cache[x.prop1] IS NULL",
      "cache[x.prop1] in [1,2,3]",
      "cache[x.prop1] in $listParam",
      "cache[x.prop1] = 10",
      "cache[x.prop1] = 'prop'",
      "cache[x.prop1] = $intParam",
      "cache[x.prop1] = $stringParam",
      "cache[x.prop1] STARTS WITH 'prop'",
      "cache[x.prop1] ENDS WITH 'prop'",
      "cache[x.prop1] CONTAINS 'prop'",
      "cache[x.prop1] IS NOT NULL",
      "cache[x.prop1] > 10",
      "cache[x.prop1] >= 10",
      "cache[x.prop1] < 10",
      "cache[x.prop1] <= 10"
    )

    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1")
          .projection("cache[x.prop1] as prop1")
          .remoteBatchPropertiesWithFilter("cache[x.prop1]")(predicate)
          .allNodeScan("x")
          .build()

        val result =
          execute(query, runtime, Map("stringParam" -> "a string", "intParam" -> 10, "listParam" -> Array(1, 2, 3)))
        result should beColumns("prop1").withNoRows()
      }
    }
  }

  test("should return nothing on non-existing property - for all predicates") {

    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop2" -> i) })
    }

    val predicates = Table(
      "predicate",
      "x.prop1 = 10",
      "x.prop1 = 'prop'",
      "x.prop1 = $intParam",
      "x.prop1 = $stringParam",
      "x.prop1 STARTS WITH 'prop'",
      "x.prop1 ENDS WITH 'prop'",
      "x.prop1 CONTAINS 'prop'",
      "x.prop1 IS NOT NULL",
      "x.prop1 > 10",
      "x.prop1 >= 10",
      "x.prop1 < 10",
      "x.prop1 <= 10",
      "x.prop1 in [1,2,3]",
      "x.prop1 in $listParam",
      "x.prop2 IS NULL",
      "point.withinBBox(x.prop1, point({ x:1, y:1 }), point({ x:4, y:4 }))",
      "cache[x.prop1] = 10",
      "cache[x.prop1] = 'prop'",
      "cache[x.prop1] = $intParam",
      "cache[x.prop1] = $stringParam",
      "cache[x.prop1] STARTS WITH 'prop'",
      "cache[x.prop1] ENDS WITH 'prop'",
      "cache[x.prop1] CONTAINS 'prop'",
      "cache[x.prop1] IS NOT NULL",
      "cache[x.prop1] > 10",
      "cache[x.prop1] >= 10",
      "cache[x.prop1] < 10",
      "cache[x.prop1] <= 10",
      "cache[x.prop1] in [1,2,3]",
      "cache[x.prop1] in $listParam",
      "cache[x.prop2] IS NULL"
    )

    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1", "prop2")
          .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
          .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
            predicate
          )
          .allNodeScan("x")
          .build()

        val result =
          execute(query, runtime, Map("stringParam" -> "a string", "intParam" -> 10, "listParam" -> Array(1, 2, 3)))
        result should beColumns("prop1", "prop2").withNoRows()
      }
    }
  }

  // ------------------------------------------------
  // return nothing on non-existing property - on tiny graph
  // ------------------------------------------------

  test("should return nothing on non-existing node property - on tiny graph - for all predicates") {

    givenGraph {
      tx.createNode().setProperty("prop2", 10)
      tx.createNode().setProperty("prop2", 20)
      tx.createNode().setProperty("prop2", 30)
    }

    val predicates = Table(
      "predicate",
      "x.prop1 = 10",
      "x.prop1 = 'prop'",
      "x.prop1 = $intParam",
      "x.prop1 = $stringParam",
      "x.prop1 STARTS WITH 'prop'",
      "x.prop1 ENDS WITH 'prop'",
      "x.prop1 CONTAINS 'prop'",
      "x.prop1 IS NOT NULL",
      "x.prop1 > 10",
      "x.prop1 >= 10",
      "x.prop1 < 10",
      "x.prop1 <= 10",
      "x.prop1 in [1,2,3]",
      "x.prop1 in $listParam",
      "x.prop2 IS NULL",
      "point.withinBBox(x.prop1, point({ x:1, y:1 }), point({ x:4, y:4 }))",
      "cache[x.prop1] = 10",
      "cache[x.prop1] = 'prop'",
      "cache[x.prop1] = $intParam",
      "cache[x.prop1] = $stringParam",
      "cache[x.prop1] STARTS WITH 'prop'",
      "cache[x.prop1] ENDS WITH 'prop'",
      "cache[x.prop1] CONTAINS 'prop'",
      "cache[x.prop1] IS NOT NULL",
      "cache[x.prop1] > 10",
      "cache[x.prop1] >= 10",
      "cache[x.prop1] < 10",
      "cache[x.prop1] <= 10",
      "cache[x.prop1] in [1,2,3]",
      "cache[x.prop1] in $listParam",
      "cache[x.prop2] IS NULL"
    )

    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1", "prop2")
          .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
          .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
            predicate
          )
          .allNodeScan("x")
          .build()

        val result =
          execute(query, runtime, Map("stringParam" -> "a string", "intParam" -> 10, "listParam" -> Array(1, 2, 3)))
        result should beColumns("prop1", "prop2").withNoRows()
      }
    }
  }

  test("should return nothing on non-existing relationship property - on tiny graph - for all predicates") {

    givenGraph {
      val (_, rels) = lineGraph(4, "R")
      rels(0).setProperty("prop2", 10)
      rels(1).setProperty("prop2", 20)
      rels(2).setProperty("prop2", 30)
    }

    val predicates = Table(
      "predicate",
      "x.prop1 = 10",
      "x.prop1 = 'prop'",
      "x.prop1 = $intParam",
      "x.prop1 = $stringParam",
      "x.prop1 STARTS WITH 'prop'",
      "x.prop1 ENDS WITH 'prop'",
      "x.prop1 CONTAINS 'prop'",
      "x.prop1 IS NOT NULL",
      "x.prop1 > 10",
      "x.prop1 >= 10",
      "x.prop1 < 10",
      "x.prop1 <= 10",
      "x.prop1 in [1,2,3]",
      "x.prop1 in $listParam",
      "x.prop2 IS NULL",
      "point.withinBBox(x.prop1, point({ x:1, y:1 }), point({ x:4, y:4 }))",
      "cacheR[x.prop1] = 10",
      "cacheR[x.prop1] = 'prop'",
      "cacheR[x.prop1] = $intParam",
      "cacheR[x.prop1] = $stringParam",
      "cacheR[x.prop1] STARTS WITH 'prop'",
      "cacheR[x.prop1] ENDS WITH 'prop'",
      "cacheR[x.prop1] CONTAINS 'prop'",
      "cacheR[x.prop1] IS NOT NULL",
      "cacheR[x.prop1] > 10",
      "cacheR[x.prop1] >= 10",
      "cacheR[x.prop1] < 10",
      "cacheR[x.prop1] <= 10",
      "cacheR[x.prop1] in [1,2,3]",
      "cacheR[x.prop1] in $listParam",
      "cacheR[x.prop2] IS NULL"
    )

    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1", "prop2")
          .projection("cacheR[x.prop1] as prop1", "cacheR[x.prop2] as prop2")
          .remoteBatchPropertiesWithFilter("cacheR[x.prop1]", "cacheR[x.prop2]")(
            predicate
          )
          .expandAll("(n)-[x:R]->()")
          .allNodeScan("n")
          .build()

        val result =
          execute(query, runtime, Map("stringParam" -> "a string", "intParam" -> 10, "listParam" -> Array(1, 2, 3)))
        result should beColumns("prop1", "prop2").withNoRows()
      }
    }
  }

  // -------------------------------------------

  test(
    "should return nothing on mix of existing and non-existing properties- prop1 IS NOT NULL and prop2 Equals - on tiny graph"
  ) {

    givenGraph {
      tx.createNode().setProperty("prop2", 10)
      tx.createNode().setProperty("prop2", 20)
      tx.createNode().setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] IS NOT NULL",
        "cache[x.prop2] = 10"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  // ------------------------------------------------
  // one property column - on tiny graph
  // ------------------------------------------------

  test("should return one node property column - on tiny graph - numeric values") {
    givenGraph {
      tx.createNode().setProperty("prop", 10)
      tx.createNode().setProperty("prop", 20)
      tx.createNode().setProperty("prop", 30)

    }
    val predicates = Table(
      ("predicate", "expected"),
      ("x.prop IS NULL", Seq()),
      ("x.prop IN [10, 30]", Seq(10, 30)),
      ("x.prop IN $listParam", Seq(10, 30)),
      ("x.prop IS NOT NULL", Seq(10, 20, 30)),
      ("x.prop = 20", Seq(20)),
      ("x.prop = $param", Seq(20)),
      ("x.prop = $param + 10", Seq(30)),
      ("x.prop > 20", Seq(30)),
      ("x.prop > $param", Seq(30)),
      ("x.prop >= 20", Seq(20, 30)),
      ("x.prop >= $param", Seq(20, 30)),
      ("x.prop < 20", Seq(10)),
      ("x.prop < $param", Seq(10)),
      ("x.prop <= 20", Seq(10, 20)),
      ("x.prop <= $param", Seq(10, 20)),
      ("x.prop >= 10 + $param", Seq(30)),
      ("cache[x.prop] IS NULL", Seq()),
      ("cache[x.prop] IN [10, 30]", Seq(10, 30)),
      ("cache[x.prop] IN $listParam", Seq(10, 30)),
      ("cache[x.prop] IS NOT NULL", Seq(10, 20, 30)),
      ("cache[x.prop] = 20", Seq(20)),
      ("cache[x.prop] = $param", Seq(20)),
      ("cache[x.prop] > 20", Seq(30)),
      ("cache[x.prop] >= 20", Seq(20, 30)),
      ("cache[x.prop] < 20", Seq(10)),
      ("cache[x.prop] <= 20", Seq(10, 20))
    )

    forEvery(predicates) { (predicate: String, expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cache[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cache[x.prop]")(predicate)
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime, Map("param" -> 20, "listParam" -> Array(10, 30)))
        result should beColumns("prop").withRows(singleColumn(expected))
      }
    }
  }

  test("should return one relationship property column - on tiny graph") {

    givenGraph {
      val (_, rels) = lineGraph(4, "R")
      rels(0).setProperty("prop", 10)
      rels(1).setProperty("prop", 20)
      rels(2).setProperty("prop", 30)
    }

    val predicates = Table(
      ("predicate", "expected"),
      ("x.prop IS NULL", Seq()),
      ("x.prop IN [10, 30]", Seq(10, 30)),
      ("x.prop IN $listParam", Seq(10, 30)),
      ("x.prop IS NOT NULL", Seq(10, 20, 30)),
      ("x.prop = 20", Seq(20)),
      ("x.prop = $param", Seq(20)),
      ("x.prop = $param + 10", Seq(30)),
      ("x.prop > 20", Seq(30)),
      ("x.prop > $param", Seq(30)),
      ("x.prop >= 20", Seq(20, 30)),
      ("x.prop >= $param", Seq(20, 30)),
      ("x.prop < 20", Seq(10)),
      ("x.prop < $param", Seq(10)),
      ("x.prop <= 20", Seq(10, 20)),
      ("x.prop <= $param", Seq(10, 20)),
      ("x.prop >= 10 + $param", Seq(30)),
      ("cacheR[x.prop] IS NULL", Seq()),
      ("cacheR[x.prop] IN [10, 30]", Seq(10, 30)),
      ("cacheR[x.prop] IN $listParam", Seq(10, 30)),
      ("cacheR[x.prop] IS NOT NULL", Seq(10, 20, 30)),
      ("cacheR[x.prop] = 20", Seq(20)),
      ("cacheR[x.prop] = $param", Seq(20)),
      ("cacheR[x.prop] > 20", Seq(30)),
      ("cacheR[x.prop] >= 20", Seq(20, 30)),
      ("cacheR[x.prop] < 20", Seq(10)),
      ("cacheR[x.prop] <= 20", Seq(10, 20))
    )

    forEvery(predicates) { (predicate: String, expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cacheR[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cacheR[x.prop]")(predicate)
          .expandAll("(n)-[x:R]->()")
          .allNodeScan("n")
          .build()

        val result = execute(query, runtime, Map("param" -> 20, "listParam" -> Array(10, 30)))
        result should beColumns("prop").withRows(singleColumn(expected))
      }
    }
  }

  test("should return one node property column - on tiny graph - string values") {
    givenGraph {
      tx.createNode().setProperty("prop", "prop1")
      tx.createNode().setProperty("prop", "prop2")
      tx.createNode().setProperty("prop", "prop3")

      tx.createNode().setProperty("prop", "1prop")
      tx.createNode().setProperty("prop", "2prop")
      tx.createNode().setProperty("prop", "3prop")

      tx.createNode().setProperty("prop", "1prp")
      tx.createNode().setProperty("prop", "prp1")
      tx.createNode().setProperty("prop", "1prp1")

    }

    val predicates = Table(
      ("predicate", "expected"),
      ("x.prop STARTS WITH 'prop'", Seq("prop1", "prop2", "prop3")),
      ("x.prop STARTS WITH $param", Seq("prop1", "prop2", "prop3")),
      ("x.prop ENDS WITH 'prop'", Seq("1prop", "2prop", "3prop")),
      ("x.prop ENDS WITH $param", Seq("1prop", "2prop", "3prop")),
      ("x.prop CONTAINS 'prp'", Seq("1prp", "prp1", "1prp1")),
      ("x.prop CONTAINS $contains", Seq("1prp", "prp1", "1prp1")),
      ("cache[x.prop] STARTS WITH 'prop'", Seq("prop1", "prop2", "prop3")),
      ("cache[x.prop] STARTS WITH $param", Seq("prop1", "prop2", "prop3")),
      ("cache[x.prop] ENDS WITH 'prop'", Seq("1prop", "2prop", "3prop")),
      ("cache[x.prop] ENDS WITH $param", Seq("1prop", "2prop", "3prop")),
      ("cache[x.prop] CONTAINS 'prp'", Seq("1prp", "prp1", "1prp1")),
      ("cache[x.prop] CONTAINS $contains", Seq("1prp", "prp1", "1prp1"))
    )

    forEvery(predicates) { (predicate: String, expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cache[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cache[x.prop]")(predicate)
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime, Map("param" -> "prop", "contains" -> "prp"))
        result should beColumns("prop").withRows(singleColumn(expected))
      }
    }
  }

  test("should return one relationship property column - on tiny graph - string values") {
    givenGraph {
      val (_, rels) = lineGraph(10, "R")
      rels(0).setProperty("prop", "prop1")
      rels(1).setProperty("prop", "prop2")
      rels(2).setProperty("prop", "prop3")

      rels(3).setProperty("prop", "1prop")
      rels(4).setProperty("prop", "2prop")
      rels(5).setProperty("prop", "3prop")

      rels(6).setProperty("prop", "1prp")
      rels(7).setProperty("prop", "prp1")
      rels(8).setProperty("prop", "1prp1")

    }

    val predicates = Table(
      ("predicate", "expected"),
      ("x.prop STARTS WITH 'prop'", Seq("prop1", "prop2", "prop3")),
      ("x.prop STARTS WITH $param", Seq("prop1", "prop2", "prop3")),
      ("x.prop ENDS WITH 'prop'", Seq("1prop", "2prop", "3prop")),
      ("x.prop ENDS WITH $param", Seq("1prop", "2prop", "3prop")),
      ("x.prop CONTAINS 'prp'", Seq("1prp", "prp1", "1prp1")),
      ("x.prop CONTAINS $contains", Seq("1prp", "prp1", "1prp1")),
      ("cacheR[x.prop] STARTS WITH 'prop'", Seq("prop1", "prop2", "prop3")),
      ("cacheR[x.prop] STARTS WITH $param", Seq("prop1", "prop2", "prop3")),
      ("cacheR[x.prop] ENDS WITH 'prop'", Seq("1prop", "2prop", "3prop")),
      ("cacheR[x.prop] ENDS WITH $param", Seq("1prop", "2prop", "3prop")),
      ("cacheR[x.prop] CONTAINS 'prp'", Seq("1prp", "prp1", "1prp1")),
      ("cacheR[x.prop] CONTAINS $contains", Seq("1prp", "prp1", "1prp1"))
    )

    forEvery(predicates) { (predicate: String, expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cacheR[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cacheR[x.prop]")(predicate)
          .expandAll("(n)-[x:R]->()")
          .allNodeScan("n")
          .build()

        val result = execute(query, runtime, Map("param" -> "prop", "contains" -> "prp"))
        result should beColumns("prop").withRows(singleColumn(expected))
      }
    }
  }

  test("should return one node property column - on tiny graph - STARTS WITH AND ENDS WITH") {
    givenGraph {
      tx.createNode().setProperty("prop", "112233")
      tx.createNode().setProperty("prop", "223344")
      tx.createNode().setProperty("prop", "11")
      tx.createNode().setProperty("prop", "33")
      tx.createNode().setProperty("prop", "1133")
      tx.createNode().setProperty("prop", "1122")
      tx.createNode().setProperty("prop", "2233")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")(
        "x.prop STARTS WITH $param1",
        "x.prop ENDS WITH $param2"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime, Map("param1" -> "11", "param2" -> "33"))
    result should beColumns("prop").withRows(singleColumn(Seq("112233", "1133")))
  }

  test("should return one relationship property column - on tiny graph - STARTS WITH AND ENDS WITH") {
    givenGraph {
      val (_, rels) = lineGraph(8, "R")
      rels(0).setProperty("prop", "112233")
      rels(1).setProperty("prop", "223344")
      rels(2).setProperty("prop", "11")
      rels(3).setProperty("prop", "33")
      rels(4).setProperty("prop", "1133")
      rels(5).setProperty("prop", "1122")
      rels(6).setProperty("prop", "2233")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cacheR[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cacheR[x.prop]")("x.prop STARTS WITH $param1", "x.prop ENDS WITH $param2")
      .expandAll("(n)-[x:R]->()")
      .allNodeScan("n")
      .build()

    val result = execute(query, runtime, Map("param1" -> "11", "param2" -> "33"))
    result should beColumns("prop").withRows(singleColumn(Seq("112233", "1133")))
  }

  test("should return one node property column - on tiny graph - point values") {
    givenGraph {
      tx.createNode().setProperty("prop", PointValue.parse("{x:2, y:0}"))
      tx.createNode().setProperty("prop", PointValue.parse("{x:2, y:2}"))
      tx.createNode().setProperty("prop", PointValue.parse("{x:3, y:3}"))
      tx.createNode().setProperty("prop", PointValue.parse("{x:5, y:5}"))
    }

    val predicates = Table(
      "predicate",
      "point.withinBBox(x.prop, point({ x:1, y:1 }), point({ x:4, y:4 }))",
      "point.withinBBox(x.prop, point({x: $x1, y: $y1}), point({x: $x2, y: $y2}))"
    )
    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cache[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cache[x.prop]")(
            predicate
          )
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime, Map("x1" -> 1, "y1" -> 1, "x2" -> 4, "y2" -> 4))
        result should beColumns("prop").withRows(singleColumn(Seq(
          PointValue.parse("{x: 2.0, y: 2.0}"),
          PointValue.parse("{x: 3.0, y: 3.0}")
        )))
      }
    }
  }

  test("should return one relationship property column - on tiny graph - point values") {
    givenGraph {
      val (_, rels) = lineGraph(5, "R")
      rels(0).setProperty("prop", PointValue.parse("{x:2, y:0}"))
      rels(1).setProperty("prop", PointValue.parse("{x:2, y:2}"))
      rels(2).setProperty("prop", PointValue.parse("{x:3, y:3}"))
      rels(3).setProperty("prop", PointValue.parse("{x:5, y:5}"))
    }

    val predicates = Table(
      "predicate",
      "point.withinBBox(x.prop, point({ x:1, y:1 }), point({ x:4, y:4 }))",
      "point.withinBBox(x.prop, point({x: $x1, y: $y1}), point({x: $x2, y: $y2}))"
    )
    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cacheR[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cacheR[x.prop]")(predicate)
          .expandAll("(n)-[x:R]->()")
          .allNodeScan("n")
          .build()

        val result = execute(query, runtime, Map("x1" -> 1, "y1" -> 1, "x2" -> 4, "y2" -> 4))
        result should beColumns("prop").withRows(singleColumn(Seq(
          PointValue.parse("{x: 2.0, y: 2.0}"),
          PointValue.parse("{x: 3.0, y: 3.0}")
        )))
      }
    }
  }

  // -------------------------------------------------------

  test("should return one node property column - on tiny graph - range text value") {
    givenGraph {
      tx.createNode().setProperty("prop", "a")
      tx.createNode().setProperty("prop", "b")
      tx.createNode().setProperty("prop", "c")
      tx.createNode().setProperty("prop", "d")
    }

    val predicates = Table(
      ("predicate", "expected"),
      ("x.prop > $param", Seq("c", "d")),
      ("x.prop >= $param", Seq("b", "c", "d")),
      ("x.prop < $param", Seq("a")),
      ("x.prop <= $param", Seq("a", "b")),
      ("cache[x.prop] > $param", Seq("c", "d")),
      ("cache[x.prop] >= $param", Seq("b", "c", "d")),
      ("cache[x.prop] < $param", Seq("a")),
      ("cache[x.prop] <= $param", Seq("a", "b"))
    )

    forEvery(predicates) { (predicate: String, expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .projection("cache[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cache[x.prop]")(predicate)
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime, Map("param" -> "b"))
        result should beColumns("prop").withRows(singleColumn(expected))
      }
    }
  }

  test("should return one node property column - on tiny graph - range less than AND greater than") {
    givenGraph {
      tx.createNode().setProperty("prop", 10)
      tx.createNode().setProperty("prop", 15)
      tx.createNode().setProperty("prop", 20)
      tx.createNode().setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] > 10", "cache[x.prop] < 20")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(15)))
  }

  test("should return one relationship property column - on tiny graph - range less than AND greater than") {
    givenGraph {
      val (_, rels) = lineGraph(5, "R")
      rels(0).setProperty("prop", 10)
      rels(1).setProperty("prop", 15)
      rels(2).setProperty("prop", 20)
      rels(3).setProperty("prop", 25)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cacheR[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cacheR[x.prop]")("cacheR[x.prop] > 10", "cacheR[x.prop] < 20")
      .expandAll("(n)-[x:R]->()")
      .allNodeScan("n")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(15)))
  }

  // ------------------------------------
  // two node property columns
  // ------------------------------------

  test("should return two node property columns - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode()
      n1.setProperty("prop1", 10)
      n1.setProperty("prop2", 11)
      val n2 = tx.createNode()
      n2.setProperty("prop1", 20)
      // n2.prop2 is null
      val n3 = tx.createNode()
      n3.setProperty("prop1", 20)
      n3.setProperty("prop2", 21)
    }

    val rowN1 = Array(10, 11)
    val rowN2 = Array[Any](20, null)
    val rowN3 = Array(20, 21)
    val predicates = Table(
      ("predicate(s)", "expected"),
      (Seq("x.prop1 = 20"), Seq(rowN2, rowN3)),
      (Seq("x.prop1 = 20", "x.prop2 = 21"), Seq(rowN3)),
      (Seq("x.prop2 = 21"), Seq(rowN3)),
      (Seq("x.prop1 IS NOT NULL"), Seq(rowN1, rowN2, rowN3)),
      (Seq("x.prop2 IS NOT NULL"), Seq(rowN1, rowN3)),
      (Seq("x.prop1 IS NULL"), Seq()),
      (Seq("x.prop2 IS NULL"), Seq(rowN2)),
      (Seq("cache[x.prop1] = 20"), Seq(rowN2, rowN3)),
      (Seq("cache[x.prop1] = 20", "cache[x.prop2] = 21"), Seq(rowN3)),
      (Seq("cache[x.prop2] = 21"), Seq(rowN3)),
      (Seq("cache[x.prop1] IS NOT NULL"), Seq(rowN1, rowN2, rowN3)),
      (Seq("cache[x.prop2] IS NOT NULL"), Seq(rowN1, rowN3)),
      (Seq("cache[x.prop1] IS NULL"), Seq()),
      (Seq("cache[x.prop2] IS NULL"), Seq(rowN2))
    )

    forEvery(predicates) { (predicate: Seq[String], expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1", "prop2")
          .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
          .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(predicate: _*)
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime)
        result should beColumns("prop1", "prop2").withRows(expected)
      }
    }
  }

  test("should return two node properties columns - Equals") {
    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop1" -> i % 2, "prop2" -> i * 2) })
    }

    val predicates = Table(
      "predicate",
      "x.prop1 = 0",
      "cache[x.prop1] = 0",
      "cache[x.prop1] = $param"
    )

    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1", "prop2")
          .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
          .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(predicate)
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime, Map("param" -> 0))
        val expected = (0 until sizeHint).filter(_ % 2 == 0).map(i => Array(0, i * 2))
        result should beColumns("prop1", "prop2").withRows(expected)
      }
    }
  }

  test("should return two node properties columns - prop1 Equals AND prop2 IS NOT NULL") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 3 == 0 => Map("prop1" -> true, "prop2" -> i)
          case i if i % 3 == 1 => Map("prop1" -> false, "prop2" -> i)
          case i if i % 3 == 2 => Map("prop1" -> true)
        }
      )
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] = true",
        "cache[x.prop2] IS NOT NULL"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected: Seq[Array[Any]] = (0 until sizeHint).filter(_ % 3 == 0) map (i => Array(true, i))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  // ------------------------------------

  test("should return two node properties columns when split into two plans with/without filter - Equals") {
    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop1" -> i % 2, "prop2" -> i * 2) })
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchProperties("cache[x.prop2]")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]")("cache[x.prop1] = 0")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected = (0 until sizeHint).filter(_ % 2 == 0).map(i => Array(0, i * 2))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  test("should return three node properties columns with nulls - IS NOT NULL") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 4 == 0 => Map("prop1" -> i, "prop2" -> i * 2, "prop3" -> i * 3)
          case i if i % 4 == 1 => Map("prop1" -> i, "prop3" -> i * 3)
          case i if i % 4 == 2 => Map("prop2" -> i * 2, "prop3" -> i * 3)
          case i if i % 4 == 3 => Map("prop1" -> i, "prop2" -> i * 2)
        }
      )
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2", "prop3")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2", "cache[x.prop3] as prop3")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]", "cache[x.prop3]")(
        "cache[x.prop3] IS NOT NULL"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected: Seq[Array[Any]] = (0 until sizeHint).flatMap {
      case i if i % 4 == 0 => Some(Array(i, i * 2, i * 3))
      case i if i % 4 == 1 => Some(Array(i, null, i * 3))
      case i if i % 4 == 2 => Some(Array(null, i * 2, i * 3))
      case i if i % 4 == 3 => None
    }
    result should beColumns("prop1", "prop2", "prop3").withRows(expected)
  }

  test("should work with deduplicated node names on union - on tiny graph") {

    givenGraph {
      val n1 = tx.createNode()
      n1.setProperty("prop", 10)
      n1.setProperty("maybeProp", true)
      val n2 = tx.createNode()
      n2.setProperty("prop", 20)
      val n3 = tx.createNode()
      n3.setProperty("prop", 30)
      n3.setProperty("maybeProp", true)
    }

    val predicates = Table(
      ("predicate", "expected"),
      ("`  x@10`.prop < 20", Seq(10, 30)),
      ("cache[`  x@10`.prop] < 20", Seq(10, 30)),
      ("`  x@10`.maybeProp IS NOT NULL", Seq(10, 30, 30)),
      ("`  x@10`.maybeProp IS NULL", Seq(20, 30)),
      ("cache[`  x@10`.maybeProp] IS NOT NULL", Seq(10, 30, 30)),
      ("cache[`  x@10`.maybeProp] IS NULL", Seq(20, 30)),
      ("`  x@10`.notProp IS NOT NULL", Seq(30)),
      ("`  x@10`.notProp IS NULL", Seq(10, 20, 30, 30)),
      ("cache[`  x@10`.notProp] IS NOT NULL", Seq(30)),
      ("cache[`  x@10`.notProp] IS NULL", Seq(10, 20, 30, 30))
    )
    forEvery(predicates) { (predicate: String, expected) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop")
          .union() // no distinct on top of union, will produce duplicates from lhs and rhs
          .|.projection("cache[`  x@10`.prop] as prop")
          .|.remoteBatchPropertiesWithFilter("cache[`  x@10`.prop]")(predicate)
          .|.allNodeScan("`  x@10`")
          .projection("cache[x.prop] as prop")
          .remoteBatchPropertiesWithFilter("cache[x.prop]")("x.prop > 20")
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime)
        result should beColumns("prop").withRows(singleColumn(expected))
      }
    }
  }

  test("should return one node property columns when under Apply") {
    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop1" -> i % 2, "prop2" -> i * 2) })
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .apply()
      .|.projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .|.remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")("cache[x.prop1] = 0")
      .|.argument("x")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected = (0 until sizeHint).filter(_ % 2 == 0).map(i => Array(0, i * 2))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  test("should return one relationship property columns when under Apply") {
    givenGraph {
      val (_, rels) = lineGraph(sizeHint, "R")
      rels.zipWithIndex.foreach {
        case (r, i) =>
          r.setProperty("prop1", i % 2)
          r.setProperty("prop2", i * 2)
      }
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .apply()
      .|.projection("cacheR[x.prop1] as prop1", "cacheR[x.prop2] as prop2")
      .|.remoteBatchPropertiesWithFilter("cacheR[x.prop1]", "cacheR[x.prop2]")("cacheR[x.prop1] = 0")
      .|.expandAll("(n)-[x:R]->()")
      .|.argument("n")
      .allNodeScan("n")
      .build()

    val result = execute(query, runtime)
    val expected = (0 until sizeHint).filter(_ % 2 == 0).map(i => Array(0, i * 2))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  test("should return one node property columns when split into two plans under Apply with Sort") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 3 == 0 => Map("prop1" -> i, "prop2" -> i * 2)
          case i if i % 3 == 1 => Map("prop2" -> i * 2)
          case i if i % 4 == 2 => Map("prop1" -> i)
        }
      )
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .apply()
      .|.sort("prop1 ASC", "prop2 ASC")
      .|.projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .|.remoteBatchPropertiesWithFilter("cache[x.prop2]")("cache[x.prop2] IS NOT NULL")
      .|.remoteBatchPropertiesWithFilter("cache[x.prop1]")("cache[x.prop1] IS NOT NULL")
      .|.argument("x")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected = (0 until sizeHint).filter(i => i % 3 == 0).map(i => Array(i, i * 2))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  test("should join on a remote batched property") {
    // given
    val nodes = givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i => Map("prop" -> i)
        }
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("a", "b")
      .valueHashJoin("cache[a.prop]=cache[b.prop]")
      .|.remoteBatchPropertiesWithFilter("cache[b.prop]")("cache[b.prop] >= 10")
      .|.allNodeScan("b")
      .remoteBatchPropertiesWithFilter("cache[a.prop]")("cache[a.prop] < 20")
      .allNodeScan("a")
      .build()
    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.map(n => Array(n, n)).slice(10, 20)
    runtimeResult should beColumns("a", "b").withRows(expected)
  }

  test("should work with trail(repeat) single hop") {
    givenGraph {
      val (nodes, _) = circleGraph(nNodes = sizeHint, relType = "R", outDegree = 1)
      var i = 0
      for (node <- nodes) {
        node.setProperty("foo", 42 + i % 4)
        i += 1
      }
    }

    val `(start) [(a)-[r]->(b)]{1,1} (end)` = TrailParameters(
      min = 1,
      max = Limited(1),
      start = "start",
      end = "end",
      innerStart = "a_inner",
      innerEnd = "b_inner",
      groupNodes = Set(("a_inner", "a"), ("b_inner", "b")),
      groupRelationships = Set(("r_inner", "r")),
      innerRelationships = Set("r_inner"),
      previouslyBoundRelationships = Set.empty,
      previouslyBoundRelationshipGroups = Set.empty,
      reverseGroupVariableProjections = false,
      emitPredicate = None
    )

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("count(*) AS c"))
      .repeatTrail(`(start) [(a)-[r]->(b)]{1,1} (end)`)
      .|.remoteBatchPropertiesWithFilter("cache[b_inner.foo]")("cache[b_inner.foo] = 42")
      .|.filterExpression(isRepeatTrailUnique("r_inner"))
      .|.expandAll("(a_inner)-[r_inner]->(b_inner)")
      .|.argument("start", "a_inner")
      .allNodeScan("start")
      .build()

    // when
    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withSingleRow(sizeHint / 4)
  }

  test("should work with trail(repeat) including zero repetition") {
    givenGraph {
      val (nodes, _) = circleGraph(nNodes = sizeHint, relType = "R", outDegree = 1)
      var i = 0
      for (node <- nodes) {
        node.setProperty("foo", 42 + (i % 4))
        i += 1
      }
    }

    val `(start) [(a)-[r]->(b)]{0,1} (end)` = TrailParameters(
      min = 0,
      max = Limited(1),
      start = "start",
      end = "end",
      innerStart = "a_inner",
      innerEnd = "b_inner",
      groupNodes = Set(("a_inner", "a"), ("b_inner", "b")),
      groupRelationships = Set(("r_inner", "r")),
      innerRelationships = Set("r_inner"),
      previouslyBoundRelationships = Set.empty,
      previouslyBoundRelationshipGroups = Set.empty,
      reverseGroupVariableProjections = false,
      emitPredicate = None
    )

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("count(*) AS c"))
      .repeatTrail(`(start) [(a)-[r]->(b)]{0,1} (end)`)
      .|.remoteBatchPropertiesWithFilter("cache[b_inner.foo]")("cache[b_inner.foo] = 42")
      .|.filterExpression(isRepeatTrailUnique("r_inner"))
      .|.expandAll("(a_inner)-[r_inner]->(b_inner)")
      .|.argument("start", "a_inner")
      .allNodeScan("start")
      .build()

    // when
    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withSingleRow(sizeHint + sizeHint / 4)
  }

  test("should work with trail(repeat) single hop, also on top") {
    givenGraph {
      val (nodes, _) = circleGraph(nNodes = sizeHint, relType = "R", outDegree = 1)
      var i = 0
      for (node <- nodes) {
        node.setProperty("foo", 42 + i % 4)
        i += 1
      }
    }

    val `(start) [(a)-[r]->(b)]{1,1} (end)` = TrailParameters(
      min = 1,
      max = Limited(1),
      start = "start",
      end = "end",
      innerStart = "a_inner",
      innerEnd = "b_inner",
      groupNodes = Set(("a_inner", "a"), ("b_inner", "b")),
      groupRelationships = Set(("r_inner", "r")),
      innerRelationships = Set("r_inner"),
      previouslyBoundRelationships = Set.empty,
      previouslyBoundRelationshipGroups = Set.empty,
      reverseGroupVariableProjections = false,
      emitPredicate = None
    )

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("c")
      .aggregation(Seq.empty, Seq("count(*) AS c"))
      .remoteBatchPropertiesWithFilter("cache[end.foo]")("cache[end.foo] = 42")
      .repeatTrail(`(start) [(a)-[r]->(b)]{1,1} (end)`)
      .|.remoteBatchPropertiesWithFilter("cache[b_inner.foo]")("cache[b_inner.foo] = 42")
      .|.filterExpression(isRepeatTrailUnique("r_inner"))
      .|.expandAll("(a_inner)-[r_inner]->(b_inner)")
      .|.argument("start", "a_inner")
      .allNodeScan("start")
      .build()

    // when
    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withSingleRow(sizeHint / 4)
  }

}

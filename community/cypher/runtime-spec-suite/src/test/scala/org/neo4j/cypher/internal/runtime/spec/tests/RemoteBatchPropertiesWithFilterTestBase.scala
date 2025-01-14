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
import org.neo4j.graphdb.Label
import org.neo4j.values.storable.PointValue
import org.scalatest.prop.TableDrivenPropertyChecks.forEvery
import org.scalatest.prop.Tables.Table

object RemoteBatchPropertiesWithFilterTestBase

abstract class RemoteBatchPropertiesWithFilterTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  val sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) with CypherScalaCheckDrivenPropertyChecks {

  test("should return nothing on empty graph - for all predicates") {

    val predicates = Table(
      "predicate",
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
      "point.withinBBox(x.prop1, point({ x:1, y:1 }), point({ x:4, y:4 }))"
    )

    forEvery(predicates) { (predicate: String) =>
      withClue(s"predicate: $predicate") {
        val query = new LogicalQueryBuilder(this)
          .produceResults("prop1")
          .projection("cache[x.prop1] as prop1")
          .remoteBatchPropertiesWithFilter("cache[x.prop1]")(predicate)
          .allNodeScan("x")
          .build()

        val result = execute(query, runtime, Map("stringParam" -> "a string", "intParam" -> 10))
        result should beColumns("prop1").withNoRows()
      }
    }
  }

  test("should return nothing on non-existing property - IS NOT NULL") {

    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop2" -> i * 2) })
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] IS NOT NULL"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test("should return nothing on non-existing property - IS NOT NULL - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] IS NOT NULL"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test(
    "should return nothing on mix of existing and non-existing properties- prop1 IS NOT NULL and prop2 Equals - on tiny graph"
  ) {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
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

  test("should return nothing on non-existing property - Equals - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] = 10"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test("should return nothing on non existing properties - Equals query parameter - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L"))
      tx.createNode(Label.label("L"))
      tx.createNode(Label.label("L"))
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("x.prop = $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> 20))
    result should beColumns("prop").withNoRows()
  }

  test("should return nothing on non-existing property - STARTS WITH - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] STARTS WITH 'prop'"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test("should return nothing on non-existing property - ENDS WITH - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] ENDS WITH 'prop'"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test("should return nothing on non-existing property - CONTAINS - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] CONTAINS 'prop'"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test("should return one node property column - Equals - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] = 20")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(20)))
  }

  test("should return one node property column - Equals query parameter - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] = $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> 20))
    result should beColumns("prop").withRows(singleColumn(Seq(20)))
  }

  test("should return one node property column - IS NOT NULL - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("notProp", 5)
      tx.createNode(Label.label("L")).setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("notProp", 25)
      tx.createNode(Label.label("L")).setProperty("prop", 30)
      tx.createNode(Label.label("L")).setProperty("notProp", 35)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] IS NOT NULL")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(10, 20, 30)))
  }

  test("should return one node property column - STARTS WITH - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "notProp1")
      tx.createNode(Label.label("L")).setProperty("prop", "prop1")
      tx.createNode(Label.label("L")).setProperty("prop", "prop2")
      tx.createNode(Label.label("L")).setProperty("prop", "notProp3")
      tx.createNode(Label.label("L")).setProperty("prop", "prop4")
      tx.createNode(Label.label("L")).setProperty("notProp", "notProp5")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] STARTS WITH 'prop'")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq("prop1", "prop2", "prop4")))
  }

  test("should return one node property column - STARTS WITH query parameter - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "notProp1")
      tx.createNode(Label.label("L")).setProperty("prop", "prop1")
      tx.createNode(Label.label("L")).setProperty("prop", "prop2")
      tx.createNode(Label.label("L")).setProperty("prop", "notProp3")
      tx.createNode(Label.label("L")).setProperty("prop", "prop4")
      tx.createNode(Label.label("L")).setProperty("notProp", "notProp5")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] STARTS WITH $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> "prop"))
    result should beColumns("prop").withRows(singleColumn(Seq("prop1", "prop2", "prop4")))
  }

  test("should return one node property column - ENDS WITH - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "1propNot")
      tx.createNode(Label.label("L")).setProperty("prop", "1prop")
      tx.createNode(Label.label("L")).setProperty("prop", "2prop")
      tx.createNode(Label.label("L")).setProperty("prop", "3propNot")
      tx.createNode(Label.label("L")).setProperty("prop", "4prop")
      tx.createNode(Label.label("L")).setProperty("notProp", "5propNot")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] ENDS WITH 'prop'")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq("1prop", "2prop", "4prop")))
  }

  test("should return one node property column - ENDS WITH query parameter - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "1propNot")
      tx.createNode(Label.label("L")).setProperty("prop", "1prop")
      tx.createNode(Label.label("L")).setProperty("prop", "2prop")
      tx.createNode(Label.label("L")).setProperty("prop", "3propNot")
      tx.createNode(Label.label("L")).setProperty("prop", "4prop")
      tx.createNode(Label.label("L")).setProperty("notProp", "5propNot")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] ENDS WITH $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> "prop"))
    result should beColumns("prop").withRows(singleColumn(Seq("1prop", "2prop", "4prop")))
  }

  test("should return one node property column - CONTAINS - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "1prp")
      tx.createNode(Label.label("L")).setProperty("prop", "1prop1")
      tx.createNode(Label.label("L")).setProperty("prop", "2prop2")
      tx.createNode(Label.label("L")).setProperty("prop", "3prpNot")
      tx.createNode(Label.label("L")).setProperty("prop", "4prop4")
      tx.createNode(Label.label("L")).setProperty("notProp", "5not5")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] CONTAINS 'prop'")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq("1prop1", "2prop2", "4prop4")))
  }

  test("should return one node property column - CONTAINS query parameter - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "1prp")
      tx.createNode(Label.label("L")).setProperty("prop", "1prop1")
      tx.createNode(Label.label("L")).setProperty("prop", "2prop2")
      tx.createNode(Label.label("L")).setProperty("prop", "3prpNot")
      tx.createNode(Label.label("L")).setProperty("prop", "4prop4")
      tx.createNode(Label.label("L")).setProperty("notProp", "5not5")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] CONTAINS $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> "prop"))
    result should beColumns("prop").withRows(singleColumn(Seq("1prop1", "2prop2", "4prop4")))
  }

  test("should return one node property column - STARTS WITH AND ENDS WITH - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "112233")
      tx.createNode(Label.label("L")).setProperty("prop", "11xx33")
      tx.createNode(Label.label("L")).setProperty("prop", "223344")
      tx.createNode(Label.label("L")).setProperty("prop", "11")
      tx.createNode(Label.label("L")).setProperty("prop", "33")
      tx.createNode(Label.label("L")).setProperty("prop", "1133")
      tx.createNode(Label.label("L")).setProperty("prop", "1223")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")(
        "cache[x.prop] STARTS WITH '11'",
        "cache[x.prop] ENDS WITH '33'"
      )
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq("112233", "11xx33", "1133")))
  }

  test("should return one node property column - STARTS WITH AND ENDS WITH query parameters - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "112233")
      tx.createNode(Label.label("L")).setProperty("prop", "11xx33")
      tx.createNode(Label.label("L")).setProperty("prop", "223344")
      tx.createNode(Label.label("L")).setProperty("prop", "11")
      tx.createNode(Label.label("L")).setProperty("prop", "33")
      tx.createNode(Label.label("L")).setProperty("prop", "1133")
      tx.createNode(Label.label("L")).setProperty("prop", "1223")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")(
        "cache[x.prop] STARTS WITH $param1",
        "cache[x.prop] ENDS WITH $param2"
      )
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param1" -> "11", "param2" -> "33"))
    result should beColumns("prop").withRows(singleColumn(Seq("112233", "11xx33", "1133")))
  }

  test("should return one node property column - prop point bounding box- on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:2, y:0}"))
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:2, y:2}"))
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:3, y:3}"))
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:5, y:5}"))

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")(
        "point.withinBBox(x.prop, point({ x:1, y:1 }), point({ x:4, y:4 }))"
      )
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(
      PointValue.parse("{x: 2.0, y: 2.0}"),
      PointValue.parse("{x: 3.0, y: 3.0}")
    )))
  }

  test("should return one node property column - prop point bounding box query params - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:2, y:0}"))
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:2, y:2}"))
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:3, y:3}"))
      tx.createNode(Label.label("L")).setProperty("prop", PointValue.parse("{x:5, y:5}"))
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")(
        "point.withinBBox(x.prop, point({x: $x1, y: $y1}), point({x: $x2, y: $y2}))"
      )
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("x1" -> 1, "y1" -> 1, "x2" -> 4, "y2" -> 4))
    result should beColumns("prop").withRows(singleColumn(Seq(
      PointValue.parse("{x: 2.0, y: 2.0}"),
      PointValue.parse("{x: 3.0, y: 3.0}")
    )))
  }

  test("should return two node properties columns - Equals") {
    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop1" -> i % 2, "prop2" -> i * 2) })
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")("cache[x.prop1] = 0")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected = (0 until sizeHint).filter(_ % 2 == 0).map(i => Array(0, i * 2))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  test("should return two node properties columns - Equals query parameter") {
    givenGraph {
      nodePropertyGraph(sizeHint, { case i => Map("prop1" -> i % 2, "prop2" -> i * 2) })
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")("cache[x.prop1] = $param")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime, Map("param" -> 0))
    val expected = (0 until sizeHint).filter(_ % 2 == 0).map(i => Array(0, i * 2))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

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

  test("should return two node property columns with one null value - Equals - on tiny graph") {
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

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")("cache[x.prop1] = 20")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withRows(Seq(Array[Any](20, null), Array[Any](20, 21)))
  }

  test("should return two node property columns with one null value - Equals on null property - on tiny graph") {
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

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")("cache[x.prop2] = 21")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withRows(Seq(Array[Any](20, 21)))
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

  test("should return two node properties columns - prop1 IS NOT NULL AND prop2 Equals") {
    givenGraph {
      nodePropertyGraph(
        sizeHint,
        {
          case i if i % 3 == 0 => Map("prop1" -> i, "prop2" -> true)
          case i if i % 3 == 1 => Map("prop1" -> i, "prop2" -> false)
          case i if i % 3 == 2 => Map("prop2" -> true)
        }
      )
    }
    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] IS NOT NULL",
        "cache[x.prop2] = true"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    val expected: Seq[Array[Any]] = (0 until sizeHint).filter(_ % 3 == 0) map (i => Array(i, true))
    result should beColumns("prop1", "prop2").withRows(expected)
  }

  test("should return two node properties columns - Equals - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode()
      n1.setProperty("prop1", 20)
      n1.setProperty("prop2", 21)
      val n2 = tx.createNode()
      n2.setProperty("prop1", 20)
      n2.setProperty("prop2", 22)
      val n3 = tx.createNode()
      n3.setProperty("prop1", 20)
      n3.setProperty("prop2", 23)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")("cache[x.prop1] = 20", "cache[x.prop2] = 22")
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withRows(Seq(Array(20, 22)))
  }

  test("should return nothing on non existing properties - IS NOT NULL - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L"))
      tx.createNode(Label.label("L"))
      tx.createNode(Label.label("L"))
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] IS NOT NULL"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
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
          case i => Map("prop" -> f"prop$i%03d") // leading zeros, 000 -> 999
        }
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("a", "b")
      .valueHashJoin("cache[a.prop]=cache[b.prop]")
      .|.remoteBatchPropertiesWithFilter("cache[b.prop]")("cache[b.prop] STARTS WITH 'prop00'") // 000 -> 009
      .|.allNodeScan("b")
      .remoteBatchPropertiesWithFilter("cache[a.prop]")("cache[a.prop] STARTS WITH 'prop0'") // 000 -> 099
      .allNodeScan("a")
      .build()
    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = nodes.map(n => Array(n, n)).take(10)
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
      reverseGroupVariableProjections = false
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
      reverseGroupVariableProjections = false
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
      reverseGroupVariableProjections = false
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

  test("should return one node property column - Range greater than - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 15)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] > 15")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(20, 25)))
  }

  test("should return one node property column - Range greater than composite expression - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 15)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] > 10 + $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> 5))
    result should beColumns("prop").withRows(singleColumn(Seq(20, 25)))
  }

  test("should return one node property column - Range greater than text value - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "a")
      tx.createNode(Label.label("L")).setProperty("prop", "b")
      tx.createNode(Label.label("L")).setProperty("prop", "c")
      tx.createNode(Label.label("L")).setProperty("prop", "d")

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] > 'b'")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq("c", "d")))
  }

  test("should return one node property column - Range greater than text parameter - on tiny graph") {
    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop", "a")
      tx.createNode(Label.label("L")).setProperty("prop", "b")
      tx.createNode(Label.label("L")).setProperty("prop", "c")
      tx.createNode(Label.label("L")).setProperty("prop", "d")
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] >= $param")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime, Map("param" -> "b"))
    result should beColumns("prop").withRows(singleColumn(Seq("b", "c", "d")))
  }

  test("should return nothing on non-existing property - Range greater than - on tiny graph") {

    givenGraph {
      tx.createNode(Label.label("L")).setProperty("prop2", 10)
      tx.createNode(Label.label("L")).setProperty("prop2", 20)
      tx.createNode(Label.label("L")).setProperty("prop2", 30)
    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop1", "prop2")
      .projection("cache[x.prop1] as prop1", "cache[x.prop2] as prop2")
      .remoteBatchPropertiesWithFilter("cache[x.prop1]", "cache[x.prop2]")(
        "cache[x.prop1] > 10"
      )
      .allNodeScan("x")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop1", "prop2").withNoRows()
  }

  test("should return one node property column - Range greater than or equal - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 15)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] >= 15")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(15, 20, 25)))
  }

  test("should return one node property column - Range less than - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 15)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] < 20")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(10, 15)))
  }

  test("should return one node property column - Range less than or equal - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 15)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] <= 20")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(10, 15, 20)))
  }

  test("should return one node property column - Range less than AND greater than - on tiny graph") {
    givenGraph {
      val n1 = tx.createNode(Label.label("L"))
      n1.setProperty("p1", 5)
      n1.setProperty("prop", 10)
      tx.createNode(Label.label("L")).setProperty("prop", 15)
      tx.createNode(Label.label("L")).setProperty("prop", 20)
      tx.createNode(Label.label("L")).setProperty("prop", 25)

    }

    val query = new LogicalQueryBuilder(this)
      .produceResults("prop")
      .projection("cache[x.prop] as prop")
      .remoteBatchPropertiesWithFilter("cache[x.prop]")("cache[x.prop] > 10", "cache[x.prop] < 20")
      .nodeByLabelScan("x", "L")
      .build()

    val result = execute(query, runtime)
    result should beColumns("prop").withRows(singleColumn(Seq(15)))
  }
}

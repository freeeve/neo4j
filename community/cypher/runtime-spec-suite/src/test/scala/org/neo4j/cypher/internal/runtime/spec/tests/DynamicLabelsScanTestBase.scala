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
import org.neo4j.cypher.internal.expressions.NullCheckAssert
import org.neo4j.cypher.internal.expressions.NullCheckAssert.NullCheckAssertException
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNodeFull
import org.neo4j.cypher.internal.logical.plans.DynamicElement.All
import org.neo4j.cypher.internal.logical.plans.DynamicElement.Any
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.logical.plans.IndexOrderDescending
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite

object DynamicLabelsScanTestBase

abstract class DynamicLabelsScanTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should scan all nodes of a dynamic label") {
    // given
    val honeys = givenGraph {
      nodeGraph(sizeHint, "Butter")
      nodeGraph(sizeHint, "Almond")

      nodeGraph(sizeHint, "Honey")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "'Honey'", All, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(honeys))
  }

  test("should scan all nodes of a label in ascending order") {
    // parallel does not maintain order
    assume(!isParallel)

    // given
    val honeys = givenGraph {
      nodeGraph(sizeHint, "Butter")
      nodeGraph(sizeHint, "Almond")

      nodeGraph(sizeHint, "Honey")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "'Honey'", All, IndexOrderAscending)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumnInOrder(honeys.sortBy(_.getId)))
  }

  test("should scan all nodes of a label in descending order") {
    // parallel does not maintain order
    assume(!isParallel)
    // given
    val honeys = givenGraph {
      nodeGraph(sizeHint, "Butter")
      nodeGraph(sizeHint, "Almond")

      nodeGraph(sizeHint, "Honey")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "'Honey'", All, IndexOrderDescending)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumnInOrder(honeys.sortBy(_.getId * -1)))
  }

  test("should scan empty graph") {
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "'Honey'", All, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle multiple scans") {
    // given
    val nodes = givenGraph { nodeGraph(10, "Honey") }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("y", "z", "x")
      .apply()
      .|.filter("true")
      .|.dynamicNodeByLabelsScan("x", "'Honey'", All, IndexOrderNone)
      .apply()
      .|.filter("true")
      .|.dynamicNodeByLabelsScan("y", "'Honey'", All, IndexOrderNone)
      .dynamicNodeByLabelsScan("z", "'Honey'", All, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for { x <- nodes; y <- nodes; z <- nodes } yield Array(y, z, x)
    runtimeResult should beColumns("y", "z", "x").withRows(expected)
  }

  test("should union any labels of all nodes") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint, "Butter") ++
        nodeGraph(sizeHint, "Almond") ++
        nodeGraph(sizeHint, "Honey")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['Honey', 'Almond', 'Butter']", Any, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(nodes))
  }

  test("should scan all nodes of a label and not produce duplicates if nodes have multiple labels") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint, "Butter", "Almond") ++
        nodeGraph(sizeHint, "Almond") ++
        nodeGraph(sizeHint, "Butter")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['Almond', 'Butter']", Any, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(nodes))
  }

  test("should handle non-existing labels") {
    // given
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['Honey', 'Almond', 'Butter']", Any, IndexOrderNone)
      .build()

    // empty db
    val executablePlan = buildPlan(logicalQuery, runtime)
    execute(executablePlan) should beColumns("x").withNoRows()

    // CREATE Almond
    givenGraph(nodeGraph(sizeHint, "Almond"))
    execute(executablePlan) should beColumns("x").withRows(rowCount(sizeHint))

    // CREATE Honey
    givenGraph(nodeGraph(sizeHint, "Honey"))
    execute(executablePlan) should beColumns("x").withRows(rowCount(2 * sizeHint))

    // CREATE Butter
    givenGraph(nodeGraph(sizeHint, "Butter"))
    execute(executablePlan) should beColumns("x").withRows(rowCount(3 * sizeHint))
  }

  test("should scan all labels of all nodes") {
    // given
    val allThree = givenGraph {
      nodeGraph(sizeHint, "Butter")
      nodeGraph(sizeHint, "Almond")
      nodeGraph(sizeHint, "Honey")

      nodeGraph(sizeHint, "Butter", "Almond", "Honey")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['Honey', 'Almond', 'Butter']", All, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(allThree))
  }

  test("intersection scan on the RHS of union") {
    // given
    val cdNodes = givenGraph {
      nodeGraph(sizeHint, "C")
      nodeGraph(sizeHint, "D")

      nodeGraph(sizeHint, "C", "D")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .union()
      .|.filter("true")
      .|.dynamicNodeByLabelsScan("x", "['C', 'D']", All, IndexOrderNone, "x")
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(List(1, 2) ++ cdNodes))
  }

  test("intersection scan on the RHS of cartesian product") {
    // given
    val cdNodes = givenGraph {
      nodeGraph(sizeHint, "C")
      nodeGraph(sizeHint, "D")

      nodeGraph(sizeHint, "C", "D")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "y")
      .cartesianProduct()
      .|.filter("true")
      .|.dynamicNodeByLabelsScan("y", "['C', 'D']", All, IndexOrderNone)
      .unwind("[1, 2] as x")
      .argument()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for { ab <- List(1, 2); cd <- cdNodes } yield Array[Any](ab, cd)
    runtimeResult should beColumns("x", "y").withRows(expected)
  }

  test("should scan all nodes of a dynamic label from argument") {
    // given
    val nodes = givenGraph {
      nodeGraph(sizeHint, "Butter") ++ nodeGraph(sizeHint, "Almond") ++ nodeGraph(sizeHint, "Honey")
    }

    val inputStream = inputValues(Array("Honey"), Array(Array("Almond", "Butter")))

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .apply()
      .|.dynamicNodeByLabelsScan("x", "lbl", Any, IndexOrderNone, "lbl")
      .input(variables = Seq("lbl"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, inputStream)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(nodes))
  }

  test("should handle empty array with Any") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "[]", Any, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle empty array with All") {
    val nodes = givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "[]", All, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(nodes))
  }

  test("should handle nonexistent label with Any") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "'NONEXISTENT_LABEL'", Any)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle nonexistent label with All") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "'NONEXISTENT_LABEL'", All)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should work with merge and a nonexistent label") {
    assume(!isParallel)
    assume(!isPipelined || canFuse)

    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults()
      .emptyResult()
      .merge(Seq(createNodeFull("n", dynamicLabels = Seq("'Foo'"))))
      .dynamicNodeByLabelsScan("n", "'Foo'", All)
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns().withStatistics(nodesCreated = 1, labelsAdded = 1)
  }

  test("should handle nonexistent label array with Any") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['NONEXISTENT_LABEL_1', 'NONEXISTENT_LABEL_2']", Any)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle nonexistent label array with All") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['NONEXISTENT_LABEL_1', 'NONEXISTENT_LABEL_2']", All)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle single nonexistent label array with Any") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['NONEXISTENT_LABEL_1']", Any)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle single nonexistent label array with All") {
    givenGraph {
      nodeGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['NONEXISTENT_LABEL_1']", All)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should handle partially nonexistent label array with Any") {
    val foos = givenGraph {
      nodeGraph(sizeHint, "Foo")
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['NONEXISTENT_LABEL_1', 'Foo']", Any, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withRows(singleColumn(foos))
  }

  test("should handle partially nonexistent label array with All") {
    givenGraph {
      nodeGraph(sizeHint, "Foo")
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", "['NONEXISTENT_LABEL_1', 'Foo']", All, IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("x").withNoRows()
  }

  test("should check for null input in dynamic label expression") {
    givenGraph {
      tx.createNode()
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicNodeByLabelsScan("x", NullCheckAssert(), Any, IndexOrderNone)
      .build()

    the[Exception] thrownBy consume(execute(logicalQuery, runtime)) should not be a[NullCheckAssertException]
  }

}

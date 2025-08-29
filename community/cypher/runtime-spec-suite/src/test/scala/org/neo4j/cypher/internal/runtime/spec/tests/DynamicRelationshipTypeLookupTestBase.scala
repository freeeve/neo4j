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

import org.neo4j.common.EntityType
import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.expressions.NullCheckAssert
import org.neo4j.cypher.internal.expressions.NullCheckAssert.NullCheckAssertException
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNodeFull
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationshipWithDynamicType
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.logical.plans.IndexOrderDescending
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RecordingRuntimeResult
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.cypher.internal.util.RuntimeUnsatisfiableRelationshipTypeExpression
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.graphdb.RelationshipType
import org.neo4j.internal.kernel.api.exceptions.schema.IllegalTokenNameException
import org.neo4j.internal.schema.IndexType
import org.neo4j.kernel.impl.coreapi.schema.IndexDefinitionImpl

import scala.util.Using

object DynamicRelationshipTypeLookupTestBase

abstract class DynamicRelationshipTypeLookupTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should support directed relationship scan") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(relationships.map(r =>
      Array(r, r.getStartNode, r.getEndNode)
    ))
  }

  test("should support directed INCOMING relationship scan") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)<-[r]-(y)", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(relationships.map(r =>
      Array(r, r.getEndNode, r.getStartNode)
    ))
  }

  test("should handle directed relationship scan for non-existing type") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$('X')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withNoRows()
  }

  test("should combine directed type scan and filter") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .filter("x:NOT_THERE")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withNoRows()
  }

  test("should handle multiple directed scans") {
    // given
    val (_, relationships) = givenGraph { circleGraph(10, "L") }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r1", "r2", "r3")
      .apply()
      .|.dynamicRelationshipTypeLookup("()-[r3]->()", "$('R')")
      .apply()
      .|.dynamicRelationshipTypeLookup("()-[r2]->()", "$('R')")
      .dynamicRelationshipTypeLookup("()-[r1]->()", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for { r1 <- relationships; r2 <- relationships; r3 <- relationships } yield Array(r1, r2, r3)
    runtimeResult should beColumns("r1", "r2", "r3").withRows(expected)
  }

  test("should handle an argument in a directed scan") {
    // given
    val (_, relationships) = givenGraph { circleGraph(10, "L") }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("b", "r")
      .apply()
      .|.projection("a AS b")
      .|.dynamicRelationshipTypeLookup("()-[r]->()", "$('R')", argumentIds = Set("a"))
      .input(variables = Seq("a"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, inputValues(Array(1), Array(2), Array(3)))
    val expected = for (i <- 1 to 3; r <- relationships) yield Array[Any](i, r)
    runtimeResult should beColumns("b", "r").withRows(expected)
  }

  test("should support undirected relationship scan") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(relationships.flatMap(r =>
      Seq(Array(r, r.getStartNode, r.getEndNode), Array(r, r.getEndNode, r.getStartNode))
    ))
  }

  test("should handle undirected relationship scan for non-existent type") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$('X')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withNoRows()
  }

  test("should combine undirected type scan and filter") {
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .filter("x:NOT_THERE")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withNoRows()
  }

  test("should handle multiple undirected scans") {
    // given
    val (_, relationships) = givenGraph { circleGraph(10, "L") }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r1", "r2", "r3")
      .apply()
      .|.dynamicRelationshipTypeLookup("()-[r3]-()", "$('R')")
      .apply()
      .|.dynamicRelationshipTypeLookup("()-[r2]-()", "$('R')")
      .dynamicRelationshipTypeLookup("()-[r1]-()", "$('R')")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for {
      r1 <- relationships; r2 <- relationships; r3 <- relationships
    } yield Seq.fill(8)(Array(r1, r2, r3))
    runtimeResult should beColumns("r1", "r2", "r3").withRows(expected.flatten)
  }

  test("should handle an argument in an undirected scan") {
    // given
    val (_, relationships) = givenGraph { circleGraph(10, "L") }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("b", "r")
      .apply()
      .|.projection("a AS b")
      .|.dynamicRelationshipTypeLookup("()-[r]-()", "$('R')", argumentIds = Set("a"))
      .input(variables = Seq("a"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, inputValues(Array(1), Array(2), Array(3)))
    val expected = (for (i <- 1 to 3; r <- relationships) yield Seq(Array[Any](i, r), Array[Any](i, r))).flatten
    runtimeResult should beColumns("b", "r").withRows(expected)
  }

  test("directed relationship scan should use ascending index order when provided") {
    assume(relationshipTypeIndexIsOrdered && !isParallel)
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$('R')", IndexOrderAscending)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(
      inOrder(
        relationships
          .map(r => Array(r, r.getStartNode, r.getEndNode))
          .sortBy(_.head.getId)
      )
    )
  }

  test("directed relationship scan should use descending index order when provided") {
    // parallel does not maintain order
    assume(relationshipTypeIndexIsOrdered && !isParallel)
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$('R')", IndexOrderDescending)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(
      inOrder(
        relationships
          .map(r => Array(r, r.getStartNode, r.getEndNode))
          .sortBy(_.head.getId * -1)
      )
    )
  }

  test("undirected relationship scan should use ascending index order when provided") {
    assume(relationshipTypeIndexIsOrdered && !isParallel)
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$('R')", IndexOrderAscending)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(
      inOrder(
        relationships
          .flatMap(r => Seq(Array(r, r.getStartNode, r.getEndNode), Array(r, r.getEndNode, r.getStartNode)))
          .sortBy(_.head.getId)
      )
    )
  }

  test("undirected relationship scan should use descending index order when provided") {
    assume(relationshipTypeIndexIsOrdered && !isParallel)
    // given
    val nNodes = Math.sqrt(sizeHint).ceil.toInt
    val (_, _, relationships, _) = givenGraph {
      bidirectionalBipartiteGraph(nNodes, "A", "B", "R", "S")
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r", "x", "y")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$('R')", IndexOrderDescending)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r", "x", "y").withRows(
      inOrder(
        relationships
          .flatMap(r => Seq(Array(r, r.getStartNode, r.getEndNode), Array(r, r.getEndNode, r.getStartNode)))
          .sortBy(_.head.getId * -1)
      )
    )
  }

  test("should handle undirected and continuation") {
    val size = 100
    val (_, rels) = givenGraph {
      circleGraph(size)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .nonFuseable()
      .unwind(s"range(1, 10) AS r2")
      .dynamicRelationshipTypeLookup("(n)-[r]-(m)", "$('R')")
      .build()

    // then
    val runtimeResult: RecordingRuntimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("r")
      .withRows(singleColumn(rels.flatMap(r => Seq.fill(2 * 10)(r))))
  }

  test("undirected scans only find loop once") {
    val rel = givenGraph {
      val a = tx.createNode()
      a.createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(n)-[r]-(m)", "$('R')")
      .build()

    execute(logicalQuery, runtime) should beColumns("r").withSingleRow(rel)
  }

  test("should do dynamic directed scan of all relationships") {
    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      val (_, _) = circleGraph(sizeHint / 3, "D", 1)
      val (_, _) = circleGraph(sizeHint / 3, "E", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['A','B','C'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumn(rels))
  }

  test("should do dynamic directed scan of all relationships using input parameters") {
    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      val (_, _) = circleGraph(sizeHint / 3, "D", 1)
      val (_, _) = circleGraph(sizeHint / 3, "E", 1)
      aRels ++ bRels ++ cRels
    }

    val inputStream = inputValues(Array("A"), Array(Array("B", "C")))

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(relType)", argumentIds = Set("relType"))
      .input(variables = Seq("relType"))
      .build()

    val runtimeResult = execute(logicalQuery, runtime, inputStream)

    // then
    runtimeResult should beColumns("r").withRows(singleColumn(rels))
  }

  test("should fail with a helpful error message when the input is not a single string (all)") {
    // given
    givenGraph {
      val a = tx.createNode()
      tx.createNode().createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$(relType)", argumentIds = Set("relType"))
      .input(variables = Seq("relType"))
      .build()

    def theResultFor(v: Any) = execute(logicalQuery, runtime, inputValues(Array(v)))
    def theDynamicType(v: Any): Unit = consume(theResultFor(v))

    // then
    theResultFor(Array[String]("C", "C")) should beColumns("r").withNoNotifications()
    theResultFor(Array[String]("C", "D")) should beColumns("r").withNotifications(
      RuntimeUnsatisfiableRelationshipTypeExpression(List("C", "D"))
    )
    the[CypherTypeException] thrownBy theDynamicType(
      1
    ) should have message "Expected relationship type to be a string or list of strings."
    the[CypherTypeException] thrownBy theDynamicType(
      Array(1)
    ) should have message "Expected relationship type to be a string or list of strings."
    the[CypherTypeException] thrownBy theDynamicType(
      null
    ) should have message "Expected relationship type to be a string or list of strings."
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(""))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType("\u0000"))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("\u0000")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("\u0000", "\u0000")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("", "\u0000")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("C", "")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("X", "")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("", "C")))
  }

  test("when conflicting types are encountered the next row should be processed") {
    // given
    val rel = givenGraph {
      val a = tx.createNode()
      tx.createNode().createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$all(relType)", argumentIds = Set("relType"))
      .input(variables = Seq("relType"))
      .build()

    val res = execute(
      logicalQuery,
      runtime,
      inputValues(
        Array(Array[String]("A", "B")),
        Array("R")
      )
    )

    res should beColumns("r")
      .withRows(singleColumn(Seq(rel)))
      .withNotifications(RuntimeUnsatisfiableRelationshipTypeExpression(List("A", "B")))
  }

  test("should fail with a helpful error message when the input is not a string or list of strings (any)") {
    // given
    givenGraph {
      val a = tx.createNode()
      tx.createNode().createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(relType)", IndexOrderNone, argumentIds = Set("relType"))
      .input(variables = Seq("relType"))
      .build()

    def theResultFor(v: Any) = execute(logicalQuery, runtime, inputValues(Array(v)))
    def theDynamicType(v: Any): Unit = consume(theResultFor(v))

    // then
    theResultFor(Array[String]("C", "C")) should beColumns("r").withNoNotifications()
    theResultFor(Array[String]("C", "D")) should beColumns("r").withNoNotifications()
    the[CypherTypeException] thrownBy theDynamicType(
      1
    ) should have message "Expected relationship type to be a string or list of strings."
    the[CypherTypeException] thrownBy theDynamicType(
      Array(1)
    ) should have message "Expected relationship type to be a string or list of strings."
    the[CypherTypeException] thrownBy theDynamicType(
      null
    ) should have message "Expected relationship type to be a string or list of strings."
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(""))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType("\u0000"))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("\u0000")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("\u0000", "\u0000")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("", "\u0000")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("C", "")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("X", "")))
    an[IllegalTokenNameException] shouldBe thrownBy(theDynamicType(Array("", "C")))
  }

  test("should do undirected scan of all relationships with types") {
    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$any(['A','B','C'])", IndexOrderNone)
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumn(rels.flatMap(r => Seq(r, r))))
  }

  test("should do directed scan of all relationships of a label in ascending order") {

    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['A','B','C'])", IndexOrderAscending).withLeveragedOrder()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumnInOrder(rels.sortBy(_.getId)))
  }

  test("should do undirected scan of all relationships of a label in ascending order") {

    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$any(['A','B','C'])", IndexOrderAscending).withLeveragedOrder()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumnInOrder(rels.sortBy(_.getId).flatMap(r => Seq(r, r))))
  }

  test("should do directed scan of all relationships of a label in descending order") {
    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['A','B','C'])", IndexOrderDescending).withLeveragedOrder()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumnInOrder(rels.sortBy(_.getId * -1)))
  }

  test("should do undirected scan of all relationships of a label in descending order") {

    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$any(['A','B','C'])", IndexOrderDescending).withLeveragedOrder()
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumnInOrder(rels.sortBy(_.getId * -1).flatMap(r => Seq(r, r))))
  }

  test("should handle multiple directed ANY (union) scans") {
    // given
    val (_, rels) = givenGraph { circleGraph(10, "A", 1) }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r1", "r2", "r3")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x3)-[r3]->(y3)", "$any(['A','B','C'])")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x2)-[r2]->(y2)", "$any(['A','B','C'])")
      .dynamicRelationshipTypeLookup("(x1)-[r1]->(y1)", "$any(['A','B','C'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for { r1 <- rels; r2 <- rels; r3 <- rels } yield Array(r1, r2, r3)
    runtimeResult should beColumns("r1", "r2", "r3").withRows(expected)
  }

  test("should handle multiple undirected ANY (union) scans") {
    // given
    val (_, rels) = givenGraph { circleGraph(10, "A", 1) }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r1", "r2", "r3")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x3)-[r3]-(y3)", "$any(['A','B','C'])")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x2)-[r2]-(y2)", "$any(['A','B','C'])")
      .dynamicRelationshipTypeLookup("(x1)-[r1]-(y1)", "$any(['A','B','C'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for {
      r1 <- rels.flatMap(r => Seq(r, r)); r2 <- rels.flatMap(r => Seq(r, r)); r3 <- rels.flatMap(r => Seq(r, r))
    } yield Array(r1, r2, r3)
    runtimeResult should beColumns("r1", "r2", "r3").withRows(expected)
  }

  test("should handle non-existing types, directed") {
    // given
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['A','B','C'])")
      .build()

    // empty db
    val executablePlan = buildPlan(logicalQuery, runtime)
    execute(executablePlan) should beColumns("x").withNoRows()

    // CREATE A
    givenGraph(circleGraph(sizeHint, "A", 1))
    execute(executablePlan) should beColumns("x").withRows(rowCount(sizeHint))

    // CREATE B
    givenGraph(circleGraph(sizeHint, "B", 1))
    execute(executablePlan) should beColumns("x").withRows(rowCount(2 * sizeHint))

    // CREATE C
    givenGraph(circleGraph(sizeHint, "C", 1))
    execute(executablePlan) should beColumns("x").withRows(rowCount(3 * sizeHint))
  }

  test("should handle non-existing types, undirected") {
    // given
    val batchSize = sizeHint / 10
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x")
      .dynamicRelationshipTypeLookup("(x)-[r]-(y)", "$any(['A','B','C'])")
      .build()

    // empty db
    val executablePlan = buildPlan(logicalQuery, runtime)
    execute(executablePlan) should beColumns("x").withNoRows()

    // CREATE A
    givenGraph(circleGraph(batchSize, "A", 1))
    execute(executablePlan) should beColumns("x").withRows(rowCount(2 * batchSize))

    // CREATE B
    givenGraph(circleGraph(batchSize, "B", 1))
    execute(executablePlan) should beColumns("x").withRows(rowCount(4 * batchSize))

    // CREATE C
    givenGraph(circleGraph(batchSize, "C", 1))
    execute(executablePlan) should beColumns("x").withRows(rowCount(6 * batchSize))
  }

  test("directed scan on the RHS of apply") {
    // given
    val (aRels, bRels, cRels, dRels) = givenGraph {
      val (_, aRels) = circleGraph(10, "A", 1)
      val (_, bRels) = circleGraph(10, "B", 1)
      val (_, cRels) = circleGraph(10, "C", 1)
      val (_, dRels) = circleGraph(10, "C", 1)

      (aRels, bRels, cRels, dRels)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r1", "r2")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x2)-[r2]->(y2)", "$any(['C','D'])", argumentIds = Set("x1", "r1", "y1"))
      .dynamicRelationshipTypeLookup("(x1)-[r1]->(y1)", "$any(['A','B'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for { r1 <- aRels ++ bRels; r2 <- cRels ++ dRels } yield Array(r1, r2)
    runtimeResult should beColumns("r1", "r2").withRows(expected)
  }

  test("undirected scan on the RHS of apply") {
    // given
    val (aRels, bRels, cRels, dRels) = givenGraph {
      val (_, aRels) = circleGraph(10, "A", 1)
      val (_, bRels) = circleGraph(10, "B", 1)
      val (_, cRels) = circleGraph(10, "C", 1)
      val (_, dRels) = circleGraph(10, "C", 1)

      (aRels, bRels, cRels, dRels)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r1", "r2")
      .apply()
      .|.dynamicRelationshipTypeLookup("(x2)-[r2]-(y2)", "$any(['C','D'])", argumentIds = Set("x1", "r1", "y1"))
      .dynamicRelationshipTypeLookup("(x1)-[r1]-(y1)", "$any(['A','B'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = for {
      r1 <- aRels.flatMap(r => Seq(r, r)) ++ bRels.flatMap(r => Seq(r, r))
      r2 <- cRels.flatMap(r => Seq(r, r)) ++ dRels.flatMap(r => Seq(r, r))
    } yield Array(r1, r2)
    runtimeResult should beColumns("r1", "r2").withRows(expected)
  }

  test("scan should get source, target and type") {
    // given
    val rels = givenGraph {
      val (_, aRels) = circleGraph(sizeHint / 3, "A", 1)
      val (_, bRels) = circleGraph(sizeHint / 3, "B", 1)
      val (_, cRels) = circleGraph(sizeHint / 3, "C", 1)
      aRels ++ bRels ++ cRels
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "y", "t")
      .projection("x AS x", "y AS y", "type(r) AS t")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['A','B','C'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    val expected = rels.map(r => Array[Any](r.getStartNode, r.getEndNode, r.getType.name()))
    runtimeResult should beColumns("x", "y", "t").withRows(expected)
  }

  test("undirected Any (union) scans only find loop once") {
    val rel = givenGraph {
      val a = tx.createNode()
      a.createRelationshipTo(a, RelationshipType.withName("R"))
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(n)-[r]-(m)", "$any(['R','S','T'])")
      .build()

    execute(logicalQuery, runtime) should beColumns("r").withSingleRow(rel)
  }

  test("should handle empty array with Any") {
    givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any([])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withNoRows()
  }

  test("should handle empty array with All") {
    val (_, rels) = givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$all([])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumn(rels))
  }

  test("should work with merge and a nonexistent rel type") {
    assume(!isParallel)
    assume(!isPipelined || canFuse)

    givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults()
      .emptyResult()
      .merge(
        Seq(createNodeFull("a"), createNodeFull("b")),
        Seq(createRelationshipWithDynamicType("r", "a", "'Foo'", "b", OUTGOING))
      )
      .dynamicRelationshipTypeLookup("(a)-[r]->(b)", "$all('Foo')", IndexOrderNone)
      .build(readOnly = false)

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns().withStatistics(nodesCreated = 2, relationshipsCreated = 1)
  }

  test("should handle nonexistent type array with Any") {
    givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['NONEXISTENT_LABEL_1', 'NONEXISTENT_LABEL_2'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withNoRows()
  }

  test("should handle nonexistent type array with All") {
    givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$all(['NONEXISTENT_LABEL_1', 'NONEXISTENT_LABEL_2'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withNoRows()
  }

  test("should handle partially nonexistent type array with Any") {
    val (_, rels) = givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$any(['NONEXISTENT_LABEL_1', 'R'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withRows(singleColumn(rels))
  }

  test("should handle partially nonexistent type array with All") {
    givenGraph {
      circleGraph(sizeHint)
    }

    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .dynamicRelationshipTypeLookup("(x)-[r]->(y)", "$all(['NONEXISTENT_LABEL_1', 'R'])")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("r").withNoRows()
  }

  test("should check for null input in dynamic type expression") {
    givenGraph {
      val n = tx.createNode()
      n.createRelationshipTo(tx.createNode(), RelationshipType.withName("R"))
    }

    Seq(SemanticDirection.BOTH, SemanticDirection.OUTGOING).foreach { dir =>
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("r")
        .dynamicRelationshipTypeLookup(
          Some("x"),
          Some("r"),
          NullCheckAssert(),
          Some("y"),
          dir,
          DynamicElement.Any,
          IndexOrderNone,
          Map.empty,
          Set.empty
        )
        .build()

      the[Exception] thrownBy consume(execute(logicalQuery, runtime)) should not be a[NullCheckAssertException]
    }
  }

  private def relationshipTypeIndexIsOrdered: Boolean = {
    Using(graphDb.beginTx) { tx =>
      {
        tx.schema.getIndexes.forEach({ id =>
          {
            val index = id.asInstanceOf[IndexDefinitionImpl].getIndexReference
            if (
              index.schema.isAnyTokenSchemaDescriptor && (index.schema.entityType eq EntityType.RELATIONSHIP) && (index.getIndexType eq IndexType.LOOKUP)
            ) {
              return index.getCapability.supportsOrdering()
            }
          }
        })
      }
    }
    fail("Didn't find the relationship type token index")
  }
}

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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNodeFull
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationshipWithDynamicType
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.logical.plans.IndexOrderDescending
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class DynamicRelationshipTypeScanPlanningIntegrationTest
    extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport {

  final private val planner =
    plannerBuilder()
      .withSetting(GraphDatabaseInternalSettings.cypher_enable_dynamic_label_scan, java.lang.Boolean.TRUE)
      .setAllNodesCardinality(120)
      .setLabelCardinality("A", 1)
      .setAllRelationshipsCardinality(10)
      .setRelationshipCardinality("(:A)-[]->()", 10)
      .build()

  test("should plan dynamic relationship type scan with single type") {
    val query =
      """MATCH (a)-[r:$('R')]->(b)
        |RETURN a, r, b""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "r", "b")
        .dynamicRelationshipTypeScan(
          leftNode = Some("a"),
          relName = Some("r"),
          relTypeExpr = literalString("R"),
          rightNode = Some("b"),
          direction = SemanticDirection.OUTGOING,
          operator = DynamicElement.All,
          indexOrder = IndexOrderNone
        )
        .build()
  }

  test("should plan dynamic relationship type scan for the disjunction of multiple types") {
    val query =
      """MATCH (a)-[r:$any(['R', 'S', 'T'])]-(b)
        |RETURN a, r, b""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "r", "b")
        .dynamicRelationshipTypeScan(
          leftNode = Some("a"),
          relName = Some("r"),
          relTypeExpr = listOfString("R", "S", "T"),
          rightNode = Some("b"),
          direction = SemanticDirection.BOTH,
          operator = DynamicElement.Any,
          indexOrder = IndexOrderNone
        )
        .build()
  }

  test("should plan dynamic relationship type scan for types passed as parameters") {
    val query =
      """WITH ['R', 'S', 'T'] AS types
        |MATCH (a)<-[r:$any(types)]-(b)
        |RETURN a, r, b""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "r", "b")
        .apply()
        .|.dynamicRelationshipTypeScan(
          leftNode = Some("a"),
          relName = Some("r"),
          relTypeExpr = varFor("types"),
          rightNode = Some("b"),
          direction = SemanticDirection.INCOMING,
          operator = DynamicElement.Any,
          indexOrder = IndexOrderNone,
          args = "types"
        )
        .projection("['R', 'S', 'T'] AS types")
        .argument()
        .build()
  }

  test("should plan dynamic relationship type scan with complex expression in predicate") {
    val query =
      """MATCH (a:A)
        |WITH labels(a) AS labels
        |MATCH (x)-[r:$any(labels)]-(y)
        |RETURN x, r, y""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("x", "r", "y")
        .apply()
        .|.dynamicRelationshipTypeScan(
          leftNode = Some("x"),
          relName = Some("r"),
          relTypeExpr = varFor("labels"),
          rightNode = Some("y"),
          direction = SemanticDirection.BOTH,
          operator = DynamicElement.Any,
          indexOrder = IndexOrderNone,
          args = "labels"
        )
        .projection("labels(a) AS labels")
        .nodeByLabelScan("a", "A", IndexOrderNone)
        .build()
  }

  test("should plan dynamic relationship type scan with dynamic types in MERGE") {
    val query =
      """MERGE ()-[r:$('R')]->()
        |RETURN r""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("r")
        .merge(
          nodes = Seq(createNodeFull("anon_0"), createNodeFull("anon_1")),
          relationships = Seq(
            createRelationshipWithDynamicType("r", "anon_0", "'R'", "anon_1", SemanticDirection.OUTGOING)
          )
        )
        .dynamicRelationshipTypeScan(
          leftNode = Some("anon_0"),
          relName = Some("r"),
          relTypeExpr = literalString("R"),
          rightNode = Some("anon_1"),
          direction = SemanticDirection.OUTGOING,
          operator = DynamicElement.All,
          indexOrder = IndexOrderNone
        )
        .build()
  }

  test("should not plan dynamic relationship type scan if cypher_enable_dynamic_label_scan is false") {
    val query =
      """MATCH (a)-[r:$('R')]->(b)
        |RETURN a, r, b""".stripMargin

    val plan = plannerBuilder()
      .withSetting(GraphDatabaseInternalSettings.cypher_enable_dynamic_label_scan, java.lang.Boolean.FALSE)
      .setAllNodesCardinality(100)
      .build()
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "r", "b")
        .filterExpression(hasDynamicType(varFor("r"), literalString("R")))
        .allRelationshipsScan("(a)-[r]->(b)")
        .build()
  }

  test("should not plan dynamic relationship type scan if relationship lookup index is not available") {
    val query =
      """MATCH (a)-[r:$('R')]->(b)
        |RETURN a, r, b""".stripMargin

    val plan = plannerBuilder()
      .setAllNodesCardinality(100)
      .removeRelationshipLookupIndex()
      .build()
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "r", "b")
        .filterExpression(hasDynamicType(varFor("r"), literalString("R")))
        .allRelationshipsScan("(a)-[r]->(b)")
        .build()
  }

  test("should plan dynamic relationship type scan ordered by the variable name") {
    val query =
      """MATCH (a)-[r:$('R')]->(b)
        |RETURN a, r, b ORDER BY r""".stripMargin

    val plan = planner
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "r", "b")
        .dynamicRelationshipTypeScan(
          leftNode = Some("a"),
          relName = Some("r"),
          relTypeExpr = literalString("R"),
          rightNode = Some("b"),
          direction = SemanticDirection.OUTGOING,
          operator = DynamicElement.All,
          indexOrder = IndexOrderAscending
        )
        .build()
  }

  test("should plan dynamic relationship type scan ordered by the variable name renamed") {
    val query =
      """MATCH (a)-[r:$('R')]-(b)
        |RETURN a, r AS s, b ORDER BY s DESC""".stripMargin

    val plan = planner
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a", "s", "b")
        .projection("r AS s")
        .dynamicRelationshipTypeScan(
          leftNode = Some("a"),
          relName = Some("r"),
          relTypeExpr = literalString("R"),
          rightNode = Some("b"),
          direction = SemanticDirection.BOTH,
          operator = DynamicElement.All,
          indexOrder = IndexOrderDescending
        )
        .build()
  }
}

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
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningAttributesTestSupport
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

import java.lang.Boolean.TRUE

class GraphSchemaOptimizationsPlanningIntegrationTest extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport with LogicalPlanningAttributesTestSupport {

  override protected def plannerBuilder(): StatisticsBackedLogicalPlanningConfigurationBuilder =
    super.plannerBuilder().withSetting(GraphDatabaseInternalSettings.planning_graph_schema_optimizations_enabled, TRUE)

  test("should not plan filter for a directly implied label") {
    val planner = plannerBuilder()
      .setLabelCardinality("Person", 1000)
      .setLabelCardinality("Actor", 500)
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Person")
      .setAllNodesCardinality(3000)
      .build()

    val query = "MATCH (n:Actor:Person) RETURN n"
    val plan = planner.plan(query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .nodeByLabelScan("n", "Actor")
      .build()
  }

  test("should not plan filter, one constrained label, multiple implied labels") {
    val planner = plannerBuilder()
      .setLabelCardinality("Entity", 1500)
      .setLabelCardinality("Person", 1000)
      .setLabelCardinality("Actor", 500)
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Person")
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Entity")
      .setAllNodesCardinality(3000)
      .build()

    val query = "MATCH (n:Actor:Person:Entity) RETURN n"
    val plan = planner.plan(query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .nodeByLabelScan("n", "Actor")
      .build()
  }

  test("should plan intersection scan if filter cannot be removed") {
    val planner = plannerBuilder()
      .setLabelCardinality("Entity", 1500)
      .setLabelCardinality("Person", 1000)
      .setLabelCardinality("Actor", 500)
      .setLabelCardinality("Other", 800)
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Person")
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Entity")
      .setAllNodesCardinality(3000)
      .build()

    val query = "MATCH (n:Actor:Person:Entity:Other) RETURN n"
    val plan = planner.plan(query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .intersectionNodeByLabelsScan("n", Seq("Actor", "Person", "Entity", "Other"))
      .build()
  }

  test("should plan filter + index scan on implied label") {
    val planner = plannerBuilder()
      .setLabelCardinality("Person", 1000)
      .setLabelCardinality("Actor", 500)
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Person")
      .addNodeIndex("Person", List("pid"), 0.1, 0.2)
      .setAllNodesCardinality(3000)
      .build()

    val query =
      """MATCH (n:Actor:Person)
        |WHERE n.pid IS NOT NULL
        |RETURN n
        |""".stripMargin

    val plan = planner.plan(query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .filter("n:Actor")
      .nodeIndexOperator("n:Person(pid)", _ => GetValue)
      .build()
  }

  // Cardinality estimation tests

  test("should not apply the selectivity of an implied label to estimate cardinality of a label scan") {
    val actors = 500

    val planner = plannerBuilder()
      .setLabelCardinality("Person", 1000)
      .setLabelCardinality("Actor", actors)
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Person")
      .setAllNodesCardinality(3000)
      .build()

    val query = "MATCH (n:Actor:Person) RETURN n"

    val actual = planner.planState(query)
    val expected = planner.planBuilder()
      .produceResults("n").withCardinality(actors)
      .nodeByLabelScan("n", "Actor").withCardinality(actors)

    actual should haveSamePlanAndCardinalitiesAsBuilder(expected)
  }

  test("should not apply the selectivity of implied labels to estimate cardinality of an intersection scan") {
    val actors = 500
    val others = 1500
    val total = 3000

    val planner = plannerBuilder()
      .setLabelCardinality("Entity", 1500)
      .setLabelCardinality("Person", 1000)
      .setLabelCardinality("Actor", actors)
      .setLabelCardinality("Other", others)
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Person")
      .addNodeLabelConstraint(constrainedLabel = "Actor", impliedLabel = "Entity")
      .setAllNodesCardinality(total)
      .build()

    val query = "MATCH (n:Actor:Person:Entity:Other) RETURN n"

    val cardinality = actors * (others.toDouble / total)

    val actual = planner.planState(query)
    val expected = planner.planBuilder()
      .produceResults("n").withCardinality(cardinality)
      .intersectionNodeByLabelsScan("n", Seq("Actor", "Person", "Entity", "Other")).withCardinality(cardinality)

    actual should haveSamePlanAndCardinalitiesAsBuilder(expected)
  }
}

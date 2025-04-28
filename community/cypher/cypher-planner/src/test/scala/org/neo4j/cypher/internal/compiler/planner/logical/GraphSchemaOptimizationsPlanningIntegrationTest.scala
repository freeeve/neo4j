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

  test("Should not plan a label filter if that label is implied by the relationship type and end-node constraint") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 10)
      .setLabelCardinality("B", 50)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 200)
      .setRelationshipCardinality("()-[:R]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R]->()", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 100)
      .addRelationshipEndpointLabelConstraint("()-[:R]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R]->(b:B)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .expandAll("(a)-[:R]->()")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test("Should plan selections when it cannot be implied from its source plan") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 50)
      .setLabelCardinality("B", 70)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 100)
      .setRelationshipCardinality("()-[:R]->(:B)", 100)
      .setRelationshipCardinality("(:A)-[:R]->()", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 100)
      .setRelationshipCardinality("()-[:S]->()", 100)
      .setRelationshipCardinality("(:B)-[:S]->()", 100)
      .addRelationshipEndpointLabelConstraint("(:B)-[:S]->()")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R]->(b:B)-[s:S]->()
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .expandAll("(b)-[:S]->()")
      .filter("b:B")
      .expandAll("(a)-[:R]->(b)")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test("Should not plan selections when it can be implied from its source plan") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 50)
      .setLabelCardinality("B", 70)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 100)
      .setRelationshipCardinality("()-[:R]->(:B)", 100)
      .setRelationshipCardinality("(:A)-[:R]->()", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 100)
      .setRelationshipCardinality("()-[:S]->()", 100)
      .setRelationshipCardinality("(:B)-[:S]->()", 100)
      .addRelationshipEndpointLabelConstraint("()-[:R]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R]->(b:B)-[s:S]->()
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .expandAll("(b)-[:S]->()")
      .expandAll("(a)-[:R]->(b)")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test("Should not plan selection :B but should plan selection :C when :C cannon be implied from its source plan") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(1000)
      .setLabelCardinality("A", 5)
      .setLabelCardinality("B", 700)
      .setLabelCardinality("C", 700)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 100)
      .setRelationshipCardinality("()-[:R]->(:A)", 100)
      .setRelationshipCardinality("(:B)-[:R]->()", 100)
      .setRelationshipCardinality("(:C)-[:R]->()", 100)
      .setRelationshipCardinality("(:B)-[:R]->(:A)", 100)
      .setRelationshipCardinality("(:C)-[:R]->(:A)", 100)
      .setRelationshipCardinality("()-[:S]->()", 100)
      .setRelationshipCardinality("()-[:S]->(:B)", 100)
      .setRelationshipCardinality("()-[:S]->(:C)", 100)
      .addRelationshipEndpointLabelConstraint("(:B)-[:R]->()")
      .addRelationshipEndpointLabelConstraint("()-[:S]->(:C)")
      .build()

    val query =
      """
        |MATCH (a:A)<-[r:R]-(bc:B&C)<-[s:S]-()
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .expandAll("(bc)<-[:S]-()")
      .filter("bc:C")
      .expandAll("(a)<-[:R]-(bc)")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test(
    "Should plan a relationshipScan when labels on both sides can be implied by the relationship types and end-node constraints and label scans are more expansive"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(2000)
      .setLabelCardinality("A", 700)
      .setLabelCardinality("B", 1200)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 200)
      .setRelationshipCardinality("()-[:R]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R]->()", 200)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 200)
      .addRelationshipEndpointLabelConstraint("(:A)-[:R]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R]->(b:B)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .relationshipTypeScan("(a)-[:R]->()")
      .build()
  }

  test("Should plan a label filter when it cannot be implied by the relationship type and end-node constraint") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 10)
      .setLabelCardinality("B", 20)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 200)
      .setRelationshipCardinality("()-[:R]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R]->()", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 100)
      .addRelationshipEndpointLabelConstraint("()-[:R]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r1:R]->(m)-[r2]->(b:B)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .filter("b:B", "NOT r2 = r1")
      .expandAll("(m)-[r2]->(b)")
      .expandAll("(a)-[r1:R]->(m)")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test(
    "Should still plan to start from label B when that is cheaper, even though B is implied by R using an end-node constraint"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(1000)
      .setLabelCardinality("A", 900)
      .setLabelCardinality("B", 10)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 200)
      .setRelationshipCardinality("()-[:R]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R]->()", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 100)
      .addRelationshipEndpointLabelConstraint("()-[:R]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R]->(b:B)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .filter("a:A")
      .expandAll("(b)<-[:R]-(a)")
      .nodeByLabelScan("b", "B")
      .build()
  }

  test(
    "Should not plan a label filter for B if that label is implied by the relationship type and end-node constraint, but should still plan a label filter for C that is not implied by a constraint"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 10)
      .setLabelCardinality("B", 90)
      .setLabelCardinality("C", 90)
      .setAllRelationshipsCardinality(200)
      .setRelationshipCardinality("()-[:R]->()", 200)
      .setRelationshipCardinality("()-[:R]->(:B)", 200)
      .setRelationshipCardinality("()-[:R]->(:C)", 150)
      .setRelationshipCardinality("(:A)-[:R]->()", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:B)", 100)
      .setRelationshipCardinality("(:A)-[:R]->(:C)", 90)
      .addRelationshipEndpointLabelConstraint("()-[:R]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R]->(bc:B&C)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .filter("bc:C")
      .expandAll("(a)-[:R]->(bc)")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test(
    "Should plan a label filter when not all relationship types in a disjunction imply the label"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 10)
      .setLabelCardinality("B", 90)
      .setLabelCardinality("C", 90)
      .setAllRelationshipsCardinality(2000)
      .setRelationshipCardinality("()-[:R1]->()", 200)
      .setRelationshipCardinality("()-[:R1]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R1]->()", 100)
      .setRelationshipCardinality("(:A)-[:R1]->(:B)", 100)
      .setRelationshipCardinality("()-[:R2]->()", 200)
      .setRelationshipCardinality("()-[:R2]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R2]->()", 100)
      .setRelationshipCardinality("(:A)-[:R2]->(:B)", 100)
      .addRelationshipEndpointLabelConstraint("()-[:R1]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R1|R2]->(b:B)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .filter("b:B")
      .expandAll("(a)-[:R1|R2]->(b)")
      .nodeByLabelScan("a", "A")
      .build()
  }

  test(
    "Should not plan a label filter when all relationship types in a disjunction imply the label"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 10)
      .setLabelCardinality("B", 90)
      .setLabelCardinality("C", 90)
      .setAllRelationshipsCardinality(2000)
      .setRelationshipCardinality("()-[:R1]->()", 200)
      .setRelationshipCardinality("()-[:R1]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R1]->()", 100)
      .setRelationshipCardinality("(:A)-[:R1]->(:B)", 100)
      .setRelationshipCardinality("()-[:R2]->()", 200)
      .setRelationshipCardinality("()-[:R2]->(:B)", 200)
      .setRelationshipCardinality("(:A)-[:R2]->()", 100)
      .setRelationshipCardinality("(:A)-[:R2]->(:B)", 100)
      .addRelationshipEndpointLabelConstraint("()-[:R1]->(:B)")
      .addRelationshipEndpointLabelConstraint("()-[:R2]->(:B)")
      .build()

    val query =
      """
        |MATCH (a:A)-[r:R1|R2]->(b:B)
        |RETURN a
        |""".stripMargin

    val res = planner.plan(query).stripProduceResults
    res shouldEqual planner.subPlanBuilder()
      .expandAll("(a)-[:R1|R2]->()")
      .nodeByLabelScan("a", "A")
      .build()
  }
}

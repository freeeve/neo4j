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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.CypherVersionTestSupport
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.PathModes
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.expressions.IsRepeatAcyclic
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.AcyclicParameters
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.TrailParameters
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandInto
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.util.UpperBound.Limited
import org.neo4j.cypher.internal.util.UpperBound.Unlimited
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class AcyclicPlanningIntegrationTest extends CypherFunSuite with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport with CypherVersionTestSupport {

  private val pb = plannerBuilder()
    .setAllNodesCardinality(1000)
    .setAllRelationshipsCardinality(300)
    .setLabelCardinality("S", 3)
    .setLabelCardinality("T", 100)
    .setRelationshipCardinality("()-[:R]->()", 250)
    .setRelationshipCardinality("(:S)-[:R]->()", 3)
    .setRelationshipCardinality("()-[:R]->(:S)", 3)
    .setRelationshipCardinality("(:T)-[:R]->()", 250)
    .setRelationshipCardinality("()-[:R]->(:T)", 250)
    .setRelationshipCardinality("(:T)-[:R]->(:T)", 250)
    .addSemanticFeature(PathModes)

  test("Single ACYCLIC path pattern case 0 - Unsatisfiable query") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (n1:S)-[r1]->(n1)
        |RETURN n1
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1")
      .projection("NULL AS n1", "NULL AS r1")
      .limit(0)
      .argument()
      .build()
  }

  test("Single ACYCLIC path pattern case 1 - Fixed-length relationships") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (n1:S)-[r1]->(n2)<-[r2]-(n3)
        |RETURN n1, n3
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n3")
      .filter("NOT n1 = n3", "NOT n2 = n3")
      .expandAll("(n2)<-[]-(n3)")
      .filter("NOT n1 = n2")
      .expandAll("(n1)-[]->(n2)")
      .nodeByLabelScan("n1", "S")
      .build()
  }

  test("Single ACYCLIC path pattern case 2 - Fixed-length relationship and QPP - process left-to-right") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)-[r1]->(n2)((n3)-[r2]->(n4)){0,3}(n5)
        |RETURN n1, n5
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n5")
      .filter("NOT n1 = n5") // Should already be solved by a combination of 'NOT n1 = n2' (in case of 0-iterations of the QPP) and 'IsRepeatAcyclic(n4) with n1 in alreadyBoundNodes' (in case of at least one iteration of the QPP)
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n2",
        end = "n5",
        innerStart = "n3",
        innerEnd = "n4",
        groupNodes = Set(),
        innerNodes = Set("n3", "n4"),
        previouslyBoundNodes = Set("n1", "n2"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r2"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n4")(pos), "NOT n3 = n4")
      .|.expandAll("(n3)-[r2]->(n4)")
      .|.argument("n3")
      .filter("NOT n1 = n2")
      .expandAll("(n1)-[]->(n2)")
      .nodeByLabelScan("n1", "S")
      .build()
  }

  test("Single ACYCLIC path pattern case 2 - Fixed-length relationship and QPP - process right-to-left") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1)-[r1]->(n2)((n3)-[r2]->(n4)){0,3}(n5:S)
        |RETURN n1, n5
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n5")
      .filter(
        "NOT n1 IN n4 + n3",
        "NOT n1 = n2",
        "NOT n1 = n5"
      ) // 'NOT n1 = n5' should already be solved by a combination of 'NOT n1 = n2' (in case of 0-iterations of the QPP) and 'NOT n1 IN n4 + n3' (in case of at least one iteration of the QPP)
      .expandAll("(n2)<-[]-(n1)")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n5",
        end = "n2",
        innerStart = "n4",
        innerEnd = "n3",
        groupNodes = Set(("n3", "n3"), ("n4", "n4")),
        innerNodes = Set("n3", "n4"),
        previouslyBoundNodes = Set("n5"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r2"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = true,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n3")(pos), "NOT n3 = n4")
      .|.expandAll("(n4)<-[r2]-(n3)")
      .|.argument("n4")
      .nodeByLabelScan("n5", "S")
      .build()
  }

  test("Single ACYCLIC path pattern case 3 - QPP with more than one relationship - process left-to-right") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)
        |(
        | (n2)<-[r1]-(n3)
        |  -[r2]->(n4)
        |  -[r3]->(n5)
        |){0,3}
        |(n6)
        |RETURN n1, n6
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n6")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n1",
        end = "n6",
        innerStart = "n2",
        innerEnd = "n5",
        groupNodes = Set(),
        innerNodes = Set("n2", "n3", "n4", "n5"),
        previouslyBoundNodes = Set("n1"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r1", "r2", "r3"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n5")(pos), "NOT n2 = n5", "NOT n4 = n5", "NOT n3 = n5")
      .|.expandAll("(n4)-[r3]->(n5)")
      .|.filter(IsRepeatAcyclic(v"n4")(pos), "NOT n2 = n4", "NOT n3 = n4")
      .|.expandAll("(n3)-[r2]->(n4)")
      .|.filter(IsRepeatAcyclic(v"n3")(pos), "NOT n2 = n3")
      .|.expandAll("(n2)<-[r1]-(n3)")
      .|.argument("n2")
      .nodeByLabelScan("n1", "S", IndexOrderNone)
      .build()
  }

  test("Single ACYCLIC path pattern case 3 - QPP with more than one relationship - process right-to-left") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1)
        |(
        | (n2)<-[r1]-(n3)
        |  -[r2]->(n4)
        |  -[r3]->(n5)
        |){0,3}
        |(n6:S)
        |RETURN n1, n6
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n6")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n6",
        end = "n1",
        innerStart = "n5",
        innerEnd = "n2",
        groupNodes = Set(),
        innerNodes = Set("n2", "n3", "n4", "n5"),
        previouslyBoundNodes = Set("n6"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r1", "r2", "r3"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = true,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n2")(pos), "NOT n2 = n3", "NOT n2 = n4", "NOT n2 = n5")
      .|.expandAll("(n3)-[r1]->(n2)")
      .|.filter(IsRepeatAcyclic(v"n3")(pos), "NOT n3 = n4", "NOT n3 = n5")
      .|.expandAll("(n4)<-[r2]-(n3)")
      .|.filter(IsRepeatAcyclic(v"n4")(pos), "NOT n4 = n5")
      .|.expandAll("(n5)<-[r3]-(n4)")
      .|.argument("n5")
      .nodeByLabelScan("n6", "S", IndexOrderNone)
      .build()
  }

  test("Single ACYCLIC path pattern case 4 - Directly connected QPPs") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)
        |((n2)-[r1]->(n3)){0,2}
        |(n4)
        |((n5)-[r2]->(n6)){0,3}
        |(n7)
        |RETURN n1, n7
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n7")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n4",
        end = "n7",
        innerStart = "n5",
        innerEnd = "n6",
        groupNodes = Set(),
        innerNodes = Set("n5", "n6"),
        previouslyBoundNodes = Set("n4"),
        // n1 was also previously bound. It is not strictly needed,
        // since it's the same as n4 in the case of zero-iterations of the first QPP
        // and the same as the last n2 in the case of at least one iteration of the first QPP
        previouslyBoundNodeGroups = Set("n3", "n2"),
        groupRelationships = Set(),
        innerRelationships = Set("r2"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n6")(pos), "NOT n5 = n6")
      .|.expandAll("(n5)-[r2]->(n6)")
      .|.argument("n5")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(2),
        start = "n1",
        end = "n4",
        innerStart = "n2",
        innerEnd = "n3",
        groupNodes = Set(("n2", "n2"), ("n3", "n3")),
        innerNodes = Set("n2", "n3"),
        previouslyBoundNodes = Set("n1"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r1"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n3")(pos), "NOT n2 = n3")
      .|.expandAll("(n2)-[r1]->(n3)")
      .|.argument("n2")
      .nodeByLabelScan("n1", "S")
      .build()
  }

  test("Single ACYCLIC path pattern case 5 - QPPs with a relationship in between - processing QPP1, rel, QPP2") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)
        |((n2)-[r1:R]->(n3)){0,2}
        |(n4)-[r2]->(n5)
        |((n6)-[r3]->(n7)){0,3}
        |(n8)
        |RETURN n1, n8
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n8")
      .filter("NOT n8 IN n3 + n2", "NOT n1 = n8", "NOT n4 = n8") // Not strictly needed
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n5",
        end = "n8",
        innerStart = "n6",
        innerEnd = "n7",
        groupNodes = Set(),
        innerNodes = Set("n6", "n7"),
        previouslyBoundNodes = Set("n1", "n4", "n5"),
        previouslyBoundNodeGroups = Set("n3", "n2"),
        groupRelationships = Set(),
        innerRelationships = Set("r3"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n7")(pos), "NOT n6 = n7")
      .|.expandAll("(n6)-[r3]->(n7)")
      .|.argument("n6")
      .filter("NOT n5 IN n3 + n2", "NOT n1 = n5", "NOT n4 = n5")
      .expandAll("(n4)-[]->(n5)")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(2),
        start = "n1",
        end = "n4",
        innerStart = "n2",
        innerEnd = "n3",
        groupNodes = Set(("n2", "n2"), ("n3", "n3")),
        innerNodes = Set("n2", "n3"),
        previouslyBoundNodes = Set("n1"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r1"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n3")(pos), "NOT n2 = n3")
      .|.expandAll("(n2)-[r1:R]->(n3)")
      .|.argument("n2")
      .nodeByLabelScan("n1", "S", IndexOrderNone)
      .build()
  }

  test("Single ACYCLIC path pattern case 5 - QPPs with a relationship in between - processing rel, QPP1, QPP2") {
    val planner = pb
      .setRelationshipCardinality("(:S)-[:R]->(:S)", 3)
      .build()
    val query =
      """
        |MATCH ACYCLIC (n1)
        |((n2)-[r1]->(n3)){0,2}
        |(n4)-[r2:R]->(n5:S)
        |((n6)-[r3]->(n7)){0,3}
        |(n8)
        |RETURN n1, n8
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n8")
      .filter("NOT n8 IN n3 + n2", "NOT n1 = n8", "NOT n4 = n8")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "n5",
        end = "n8",
        innerStart = "n6",
        innerEnd = "n7",
        groupNodes = Set(),
        innerNodes = Set("n6", "n7"),
        previouslyBoundNodes = Set("n1", "n4", "n5"),
        previouslyBoundNodeGroups = Set("n2", "n3"),
        groupRelationships = Set(),
        innerRelationships = Set("r3"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n7")(pos), "NOT n6 = n7")
      .|.expandAll("(n6)-[r3]->(n7)")
      .|.argument("n6")
      .filter("NOT n1 = n5")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(2),
        start = "n4",
        end = "n1",
        innerStart = "n3",
        innerEnd = "n2",
        groupNodes = Set(("n2", "n2"), ("n3", "n3")),
        innerNodes = Set("n2", "n3"),
        previouslyBoundNodes = Set("n5", "n4"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r1"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = true,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n2")(pos), "NOT n2 = n3")
      .|.expandAll("(n3)<-[r1]-(n2)")
      .|.argument("n3")
      .filter("NOT n4 = n5")
      .expandAll("(n5)<-[:R]-(n4)")
      .nodeByLabelScan("n5", "S", IndexOrderNone)
      .build()
  }

  test("Single ACYCLIC path pattern case 6 - Relationship before and after QPP") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (a:S)-[q:R]->(b) ((c)-[r]->(d)-[s]->(e)){1,10} (f)-[t]->(g)
        |RETURN a, g
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("a", "g")
      .filter("NOT g IN (e + d) + c", "NOT a = g", "NOT b = g", "NOT f = g")
      // The following should have been enough: "NOT g IN (e + d)", "NOT a = g", "NOT b = g"
      .expandAll("(f)-[]->(g)")
      .filter("NOT a = f") // Not strictly needed
      .repeatAcyclic(AcyclicParameters(
        min = 1,
        max = Limited(10),
        start = "b",
        end = "f",
        innerStart = "c",
        innerEnd = "e",
        groupNodes = Set(("c", "c"), ("d", "d"), ("e", "e")),
        innerNodes = Set("c", "d", "e"),
        previouslyBoundNodes = Set("a", "b"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r", "s"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"e")(pos), "NOT d = e", "NOT c = e")
      .|.expandAll("(d)-[s]->(e)")
      .|.filter(IsRepeatAcyclic(v"d")(pos), "NOT c = d")
      .|.expandAll("(c)-[r]->(d)")
      .|.argument("c")
      .filter("NOT a = b")
      .expandAll("(a)-[:R]->(b)")
      .nodeByLabelScan("a", "S", IndexOrderNone)
      .build()
  }

  test("Multiple ACYCLIC path pattern case 1 - Two path patterns with single fixed-length relationships") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)-[r1:R]->(n2), ACYCLIC (n2)-[r2]->(n3)
        |RETURN n1, n3
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n3")
      .filter("NOT r1 = r2", "NOT n2 = n3")
      .expandAll("(n2)-[r2]->(n3)")
      .filter("NOT n1 = n2")
      .expandAll("(n1)-[r1:R]->(n2)")
      .nodeByLabelScan("n1", "S", IndexOrderNone)
      .build()
  }

  test(
    "Multiple ACYCLIC path pattern case 1 - Two path patterns with single fixed-length relationships - anonymous variables"
  ) {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC ()-[]-(), ACYCLIC ()-[]-()
        |RETURN 1
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("1")
      .projection("1 AS 1")
      .filter("NOT anon_1 = anon_4")
      .cartesianProduct()
      .|.filter("NOT anon_3 = anon_5")
      .|.allRelationshipsScan("(anon_3)-[anon_4]-(anon_5)")
      .filter("NOT anon_0 = anon_2")
      .allRelationshipsScan("(anon_0)-[anon_1]-(anon_2)")
      .build()
  }

  test("Multiple ACYCLIC path pattern case 2 - Two path patterns with multiple fixed-length relationships") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)-[r1]->(n2)
        | <-[r2]-(n3), ACYCLIC (n3)-[r3]->(n4)
        | -[r4]->(n5)
        |RETURN n1, n5
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n5")
      .filter("NOT r2 = r4", "NOT r1 = r4", "NOT n3 = n5", "NOT n4 = n5")
      .expandAll("(n4)-[r4]->(n5)")
      .filter("NOT r2 = r3", "NOT r1 = r3", "NOT n3 = n4")
      .expandAll("(n3)-[r3]->(n4)")
      .filter("NOT n1 = n3", "NOT n2 = n3")
      .expandAll("(n2)<-[r2]-(n3)")
      .filter("NOT n1 = n2")
      .expandAll("(n1)-[r1]->(n2)")
      .nodeByLabelScan("n1", "S", IndexOrderNone)
      .build()
  }

  test("Multiple ACYCLIC path pattern case 3 - Two path patterns both with a fixed-length relationship and a QPP") {
    val planner = pb.build()
    val query =
      """
        |MATCH ACYCLIC (n1:S)-[r1]->(n2)
        |((n3)<-[r2]-(n4)){0,2}
        |(n5),
        |ACYCLIC (n5)-[r3]->(n6)
        |((n7)-[r4]->(n8)){0,1}
        |(n9)
        |RETURN n1, n9
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("n1", "n9")
      .filter("NOT n5 = n9")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(1),
        start = "n6",
        end = "n9",
        innerStart = "n7",
        innerEnd = "n8",
        groupNodes = Set(),
        innerNodes = Set("n7", "n8"),
        previouslyBoundNodes = Set("n5", "n6"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r4"),
        previouslyBoundRelationships = Set("r1"),
        previouslyBoundRelationshipGroups = Set("r2"),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n8")(pos), "NOT n7 = n8", isRepeatTrailUnique("r4"))
      .|.expandAll("(n7)-[r4]->(n8)")
      .|.argument("n7")
      .filter("NOT r3 IN r2", "NOT n5 = n6", "NOT r1 = r3")
      .expandAll("(n5)-[r3]->(n6)")
      .filter("NOT n1 = n5") // Not strictly needed
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(2),
        start = "n2",
        end = "n5",
        innerStart = "n3",
        innerEnd = "n4",
        groupNodes = Set(),
        innerNodes = Set("n3", "n4"),
        previouslyBoundNodes = Set("n1", "n2"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(("r2", "r2")),
        innerRelationships = Set("r2"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"n4")(pos), "NOT n3 = n4", isRepeatTrailUnique("r2"))
      .|.expandAll("(n3)<-[r2]-(n4)")
      .|.argument("n3")
      .filter("NOT n1 = n2")
      .expandAll("(n1)-[r1]->(n2)")
      .nodeByLabelScan("n1", "S", IndexOrderNone)
      .build()
  }

  test("ACYCLIC base test") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (a:S)-[r1:R]->(c),
        |  ACYCLIC (c)-[r2:R]->(d)<-[r3:R]-(e)
        |  ((e_inner)-[r4:R]->(f)<-[r5:R]-(g_inner1)){0,3}
        |  (g)
        |  ((g_inner2)-[r6:R]->(h_inner)){0,2} (h)
        |RETURN a, h
        |
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("a", "h")
      .filter("NOT c = h", "NOT d = h") // DifferentNodes(c,h), DifferentNodes(d,h)
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(2),
        start = "g",
        end = "h",
        innerStart = "g_inner2",
        innerEnd = "h_inner",
        groupNodes = Set(),
        innerNodes = Set("g_inner2", "h_inner"),
        previouslyBoundNodes = Set("c", "d", "g"), // e is not strictly necessary
        previouslyBoundNodeGroups = Set("g_inner1", "f", "e_inner"),
        groupRelationships = Set(),
        innerRelationships = Set("r6"),
        previouslyBoundRelationships = Set("r1"),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      // IsRepeatAcyclic(h_inner) with previouslyBoundNodes = Set("c", "d", "g")
      // and previouslyBoundNodeGroups = Set("g_inner1", "f", "e_inner") solves:
      // - UniqueNodes(g_inner2+h_inner)
      // - DistinctNodes(e_inner+f+g_inner1, g_inner2+h_inner)
      // - NoneOfNodes(c, g_inner2+h_inner)
      // - NoneOfNodes(d, g_inner2+h_inner)
      // isRepeatTrailUnique(r6) with previouslyBoundRelationships = Set("r1") solves:
      // - NoneOfRelationships(r1, r6)
      .|.filter(IsRepeatAcyclic(v"h_inner")(pos), "NOT g_inner2 = h_inner", isRepeatTrailUnique("r6"))
      .|.expandAll("(g_inner2)-[r6:R]->(h_inner)")
      .|.argument("g_inner2")
      .filter("NOT c = g", "NOT d = g") // DifferentNodes(c,g), DifferentNodes(d,g)
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Limited(3),
        start = "e",
        end = "g",
        innerStart = "e_inner",
        innerEnd = "g_inner1",
        groupNodes = Set(("e_inner", "e_inner"), ("f", "f"), ("g_inner1", "g_inner1")),
        innerNodes = Set("e_inner", "f", "g_inner1"),
        previouslyBoundNodes = Set("c", "d", "e"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("r4", "r5"),
        previouslyBoundRelationships = Set("r1"),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      // IsRepeatAcyclic(v"f"), IsRepeatAcyclic(v"g_inner1") with previouslyBoundNodes = Set("c", "d", "e") solves:
      // - UniqueNodes(e_inner+f+g_inner1)
      // - NoneOfNodes(c, e_inner+f+g_inner1)
      // - NoneOfNodes(d, e_inner+f+g_inner1)
      // IsRepeatTrailUnique(r4), IsRepeatTrailUnique(r5) with previouslyBoundRelationships = Set("r1") solves:
      // - NoneOfRelationships(r1, r4+r5)
      .|.filter(
        IsRepeatAcyclic(v"g_inner1")(pos),
        "NOT f = g_inner1",
        "NOT e_inner = g_inner1",
        isRepeatTrailUnique("r5")
      )
      .|.expandAll("(f)<-[r5:R]-(g_inner1)")
      .|.filter(IsRepeatAcyclic(v"f")(pos), "NOT e_inner = f", isRepeatTrailUnique("r4"))
      .|.expandAll("(e_inner)-[r4:R]->(f)")
      .|.argument("e_inner")
      .filter(
        "NOT r1 = r3", // DifferentRelationships(r1,r3)
        "NOT c = e", // DifferentNodes(c,e)
        "NOT d = e" // DifferentNodes(d,e)
      )
      .expandAll("(d)<-[r3:R]-(e)")
      .filter("NOT r1 = r2", "NOT c = d") // DifferentRelationships(r1,r2), DifferentNodes(c,d)
      .expandAll("(c)-[r2:R]->(d)")
      .filter("NOT a = c") // DifferentNodes(a,c)
      .expandAll("(a)-[r1:R]->(c)")
      .nodeByLabelScan("a", "S")
      .build()
  }

  test("Single ACYCLIC path pattern - with subquery expression") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (a)-[r]-(b)((c)-[s]-(d)
        |  WHERE EXISTS { MATCH ACYCLIC (d)((x1)-[x2]->(x3))+(c) RETURN * } )+ (p)
        |RETURN *
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("a", "b", "c", "d", "p", "r", "s")
      .filter("NOT a = p")
      .repeatAcyclic(AcyclicParameters(
        min = 1,
        max = Unlimited,
        start = "b",
        end = "p",
        innerStart = "c",
        innerEnd = "d",
        groupNodes = Set(("c", "c"), ("d", "d")),
        innerNodes = Set("c", "d"),
        previouslyBoundNodes = Set("a", "b"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(("s", "s")),
        innerRelationships = Set("s"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.semiApply()
      .|.|.repeatAcyclic(AcyclicParameters(
        min = 1,
        max = Unlimited,
        start = "d",
        end = "c",
        innerStart = "x1",
        innerEnd = "x3",
        groupNodes = Set(),
        innerNodes = Set("x1", "x3"),
        previouslyBoundNodes = Set("d"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("x2"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandInto,
        accumulators = Set()
      ))
      .|.|.|.filter("NOT x1 = x3", IsRepeatAcyclic(v"x3")(pos))
      .|.|.|.expandAll("(x1)-[x2]->(x3)")
      .|.|.|.argument("x1")
      .|.|.argument("d", "c")
      .|.filter(IsRepeatAcyclic(v"d")(pos), "NOT c = d")
      .|.expandAll("(c)-[s]-(d)")
      .|.argument("c")
      .filter("NOT a = b")
      .allRelationshipsScan("(a)-[r]-(b)")
      .build()
  }

  test("Single ACYCLIC path pattern - with subquery expression with TRAIL path mode") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (a)-[r]-(b)((c)-[s]-(d)
        |  WHERE EXISTS { MATCH (d:T)-[x2 {prop: 1}]->+(c) RETURN * } )+ (p)
        |RETURN *
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("a", "b", "c", "d", "p", "r", "s")
      .filter("NOT a = p")
      .repeatAcyclic(AcyclicParameters(
        min = 1,
        max = Unlimited,
        start = "b",
        end = "p",
        innerStart = "c",
        innerEnd = "d",
        groupNodes = Set(("c", "c"), ("d", "d")),
        innerNodes = Set("c", "d"),
        previouslyBoundNodes = Set("a", "b"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(("s", "s")),
        innerRelationships = Set("s"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandAll,
        accumulators = Set()
      ))
      .|.semiApply()
      .|.|.repeatTrail(TrailParameters(
        min = 1,
        max = Unlimited,
        start = "d",
        end = "c",
        innerStart = "anon_0",
        innerEnd = "anon_1",
        groupNodes = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("x2"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandInto,
        accumulators = Set()
      ))
      .|.|.|.filter("NOT anon_0 = anon_1", "x2.prop = 1", isRepeatTrailUnique("x2"))
      .|.|.|.expandAll("(anon_0)-[x2]->(anon_1)")
      .|.|.|.argument("anon_0")
      .|.|.filter("d:T")
      .|.|.argument("d", "c")
      .|.filter(IsRepeatAcyclic(v"d")(pos), "NOT c = d")
      .|.expandAll("(c)-[s]-(d)")
      .|.argument("c")
      .filter("NOT a = b")
      .allRelationshipsScan("(a)-[r]-(b)")
      .build()
  }

  test("Single ACYCLIC path pattern - expandInto due to previous graph pattern") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (a)--(e)
        |WITH * SKIP 0
        |MATCH ACYCLIC (a)((b)--(c)--(d))*(e)
        |RETURN *
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("a", "b", "c", "d", "e")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Unlimited,
        start = "a",
        end = "e",
        innerStart = "b",
        innerEnd = "d",
        groupNodes = Set(("b", "b"), ("c", "c"), ("d", "d")),
        innerNodes = Set("b", "c", "d"),
        previouslyBoundNodes = Set("a"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("anon_0", "anon_1"),
        previouslyBoundRelationships = Set(),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandInto,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"d")(pos), "NOT b = d", "NOT c = d")
      .|.expandAll("(c)-[anon_1]-(d)")
      .|.filter(IsRepeatAcyclic(v"c")(pos), "NOT b = c")
      .|.expandAll("(b)-[anon_0]-(c)")
      .|.argument("b")
      .skip(0)
      .filter("NOT a = e")
      .allRelationshipsScan("(a)-[]-(e)")
      .build()
  }

  test("Single ACYCLIC path pattern - expandInto in same GRAPH pattern") {
    val planner = pb.build()

    val query =
      """
        |MATCH ACYCLIC (a)--(e), ACYCLIC (a)((b)--(c)--(d))*(e)
        |RETURN *
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)
    plan shouldEqual planner.subPlanBuilder()
      .produceResults("a", "b", "c", "d", "e")
      .repeatAcyclic(AcyclicParameters(
        min = 0,
        max = Unlimited,
        start = "a",
        end = "e",
        innerStart = "b",
        innerEnd = "d",
        groupNodes = Set(("b", "b"), ("c", "c"), ("d", "d")),
        innerNodes = Set("b", "c", "d"),
        previouslyBoundNodes = Set("a"),
        previouslyBoundNodeGroups = Set(),
        groupRelationships = Set(),
        innerRelationships = Set("anon_1", "anon_2"),
        previouslyBoundRelationships = Set("anon_0"),
        previouslyBoundRelationshipGroups = Set(),
        reverseGroupVariableProjections = false,
        expansionMode = ExpandInto,
        accumulators = Set()
      ))
      .|.filter(IsRepeatAcyclic(v"d")(pos), "NOT b = d", "NOT c = d", isRepeatTrailUnique("anon_2"))
      .|.expandAll("(c)-[anon_2]-(d)")
      .|.filter(IsRepeatAcyclic(v"c")(pos), "NOT b = c", isRepeatTrailUnique("anon_1"))
      .|.expandAll("(b)-[anon_1]-(c)")
      .|.argument("b")
      .filter("NOT a = e")
      .allRelationshipsScan("(a)-[anon_0]-(e)")
      .build()
  }
}

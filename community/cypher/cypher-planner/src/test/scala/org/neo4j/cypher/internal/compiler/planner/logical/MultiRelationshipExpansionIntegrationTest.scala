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
import org.neo4j.configuration.GraphDatabaseInternalSettings.StatefulShortestPlanningMode.ALL_IF_POSSIBLE
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.compiler.ExecutionModel.Volcano
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.ir.SelectivePathPattern.CountInteger
import org.neo4j.cypher.internal.logical.builder.TestNFABuilder
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.logical.plans.NestedPlanExistsExpression
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

import java.lang.Boolean.TRUE

class MultiRelationshipExpansionIntegrationTest extends CypherFunSuite with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport {

  private val plannerBase = plannerBuilder()
    .setAllNodesCardinality(100)
    .setAllRelationshipsCardinality(40)
    .setLabelCardinality("User", 4)
    .setLabelCardinality("N", 6)
    .setLabelCardinality("NN", 5)
    .setLabelCardinality("NN", 5)
    .setLabelCardinality("B", 8)
    .addNodeIndex("User", Seq("prop"), 1.0, 0.25)
    .setRelationshipCardinality("()-[:R]->()", 10)
    .setRelationshipCardinality("(:User)-[:R]->()", 10)
    .setRelationshipCardinality("(:User)-[:R]->(:B)", 10)
    .setRelationshipCardinality("(:B)-[:R]->(:B)", 10)
    .setRelationshipCardinality("(:B)-[:R]->(:User)", 10)
    .setRelationshipCardinality("(:B)-[:R]->()", 10)
    .setRelationshipCardinality("(:B)-[]->()", 10)
    .setRelationshipCardinality("(:User)-[]->(:B)", 10)
    .setRelationshipCardinality("()-[]->(:B)", 10)
    .setRelationshipCardinality("()-[:R]->(:B)", 10)
    .setRelationshipCardinality("()-[]->(:N)", 10)
    .setRelationshipCardinality("()-[]->(:NN)", 10)
    .setRelationshipCardinality("()-[]->(:User)", 10)
    .setRelationshipCardinality("(:User)-[]->(:User)", 10)
    .setRelationshipCardinality("(:User)-[]->()", 10)
    .setRelationshipCardinality("(:User)-[]->(:NN)", 10)
    .setRelationshipCardinality("(:B)-[]->(:N)", 10)
    .setRelationshipCardinality("()-[:T]->()", 10)
    .setRelationshipCardinality("(:N)-[]->()", 10)
    // This makes it deterministic which plans ends up on what side of a CartesianProduct.
    .setExecutionModel(Volcano)
    .withSetting(GraphDatabaseInternalSettings.multi_relationship_expansion_enabled, TRUE)

  private val planner = plannerBase.build()

  private val allIfPossiblePlanner = plannerBase
    .withSetting(GraphDatabaseInternalSettings.stateful_shortest_planning_mode, ALL_IF_POSSIBLE)
    .build()

  private val nonDeduplicatingPlanner =
    plannerBase
      .enableDeduplicateNames(false)
      .build()

  test("should plan non-inlineable pattern expression predicates combined with normal predicate inside QPP") {
    val query =
      """MATCH ANY SHORTEST ((u:User)(
        |  (n)-[r]->(m)
        |    WHERE CASE
        |      WHEN (m)-[]->(:N) THEN n.prop > m.prop
        |      ELSE false
        |    END
        |  )+(v))
        |RETURN *""".stripMargin
    val planner = nonDeduplicatingPlanner

    val plan = planner.plan(query)

    val nestedPlan = planner.subPlanBuilder()
      .filter("`  UNNAMED0`:N")
      .expand("(`  m@2`)-[]->(`  UNNAMED0`)")
      .argument("  m@2")
      .build()

    val solvedNestedExpressionAsString =
      """EXISTS {
        |  MATCH (`  m@2`)-[`  UNNAMED0`]->(`  UNNAMED0`)
        |    WHERE `  UNNAMED0`:N
        |}""".stripMargin
    val nestedPlanExpression = NestedPlanExistsExpression(
      plan = nestedPlan,
      solvedExpressionAsString =
        solvedNestedExpressionAsString
    )(pos)

    val innerCompoundPredicate =
      Some(caseExpression(
        None,
        Some(literalBoolean(false)),
        (
          nestedPlanExpression,
          greaterThan(
            propExpression(v"  n@0", "prop"),
            propExpression(v"  m@2", "prop")
          )
        )
      ))

    val expectedNfa = new TestNFABuilder(0, "u")
      .addTransition(0, 1, "(u) (`  n@0`)")
      .addMultiRelationshipTransitionWithExpression(
        1,
        2,
        "(`  n@0`)-[`  r@1`]->(`  m@2`)",
        compoundPred = innerCompoundPredicate
      )
      .addTransition(2, 1, "(`  m@2`) (`  n@0`)")
      .addTransition(2, 3, "(`  m@2`) (`  v@6`)")
      .setFinalState(3)
      .build()

    plan should equal(
      planner.planBuilder()
        .produceResults("`  m@5`", "`  n@3`", "`  r@4`", "u", "v")
        .statefulShortestPathExpr(
          "u",
          "v",
          "SHORTEST 1 (u) ((`  n@0`)-[`  r@1`]->(`  m@2`)){1, } (v)",
          None,
          Set(("  n@0", "  n@3"), ("  m@2", "  m@5")),
          Set(("  r@1", "  r@4")),
          Set(("  v@6", "v")),
          Set(),
          StatefulShortestPath.Selector.Shortest(CountInteger(1)),
          expectedNfa,
          ExpandAll,
          false,
          1,
          None
        )
        .nodeByLabelScan("u", "User")
        .build()
    )
  }

  test(
    "Should support a shortest path pattern with a predicate on several entities in different pattern parts inside a QPP"
  ) {
    val query = "MATCH ANY SHORTEST (u:User)(((n)-[r]->(c:B)-->(m)) WHERE n.prop <= m.prop)+ (v) RETURN *"

    val nfa = new TestNFABuilder(0, "u")
      .addTransition(0, 1, "(u) (n)")
      .addTransition(1, 2, "(n)-[r]->(c:B)-[anon_0]->(m)", compoundPredicate = "n.prop <= m.prop")
      .addTransition(2, 1, "(m) (n)")
      .addTransition(2, 3, "(m) (v)")
      .setFinalState(3)
      .build()

    val plan = planner.plan(query).stripProduceResults
    val expected = planner.subPlanBuilder()
      .statefulShortestPath(
        "u",
        "v",
        "SHORTEST 1 (u) ((`n`)-[`r`]->(`c`)-[`anon_0`]->(`m`)){1, } (v)",
        None,
        Set(("n", "n"), ("c", "c"), ("m", "m")),
        Set(("r", "r")),
        Set(("v", "v")),
        Set(),
        StatefulShortestPath.Selector.Shortest(CountInteger(1)),
        nfa,
        ExpandAll,
        false,
        2,
        None
      )
      .nodeByLabelScan("u", "User")
      .build()
    plan should equal(expected)
  }

  test(
    "should plan SHORTEST with predicate depending on no path variables as a filter inside the QPP"
  ) {
    val query = "MATCH ANY SHORTEST ((u:User)((n)-[r]->(m) WHERE $param)+(v)) RETURN *"

    val nfa = new TestNFABuilder(0, "u")
      .addTransition(0, 1, "(u) (n)")
      .addTransition(1, 2, "(n)-[r]->(m)", compoundPredicate = "$param")
      .addTransition(2, 1, "(m) (n)")
      .addTransition(2, 3, "(m) (v)")
      .setFinalState(3)
      .build()

    val plan = allIfPossiblePlanner.plan(query).stripProduceResults
    plan should equal(
      allIfPossiblePlanner.subPlanBuilder()
        .statefulShortestPath(
          "u",
          "v",
          "SHORTEST 1 (u) ((`n`)-[`r`]->(`m`)){1, } (v)",
          None,
          Set(("n", "n"), ("m", "m")),
          Set(("r", "r")),
          Set(("v", "v")),
          Set(),
          StatefulShortestPath.Selector.Shortest(CountInteger(1)),
          nfa,
          ExpandAll,
          false,
          1,
          None
        )
        .nodeByLabelScan("u", "User")
        .build()
    )
  }

  test("long pattern with lots of anonymous variables") {
    val query =
      s"""
         |MATCH SHORTEST 1
         |  (u:User {prop: 5})<-[:R]-
         |  (:B)-[:R]->
         |  (:B)-[:R]->+()
         |  (
         |    (sx1)<-[:R]-
         |    (:B)-[:R]->
         |    (x:B)<-[:R]-
         |    (:B)-[:R]->
         |    (sx2:B)
         |    WHERE  sx1.prop + sx2.prop <= 60
         |  ){0,1}
         |  ()-[:R]->*
         |  (:B)<-[:R]-
         |  (:B)-[:R]->
         |  (end:B)
         |RETURN *
         |""".stripMargin

    val plan = planner.plan(query).stripProduceResults

    val nfa = new TestNFABuilder(0, "u")
      .addTransition(0, 1, "(u)<-[anon_22:R]-(anon_23 WHERE anon_23:B)")
      .addTransition(1, 2, "(anon_23)-[anon_24:R]->(anon_25 WHERE anon_25:B)")
      .addTransition(2, 3, "(anon_25) (anon_10)")
      .addTransition(3, 4, "(anon_10)-[anon_11:R]->(anon_12)")
      .addTransition(4, 3, "(anon_12) (anon_10)")
      .addTransition(4, 5, "(anon_12) (anon_26)")
      .addTransition(5, 6, "(anon_26) (sx1)")
      .addTransition(5, 8, "(anon_26) (anon_27)")
      .addTransition(
        6,
        7,
        "(sx1)<-[anon_13:R]-(anon_14:B)-[anon_15:R]->(x:B)<-[anon_16:R]-(anon_17:B)-[anon_18:R]->(sx2:B)",
        compoundPredicate = "sx1.prop + sx2.prop <= 60"
      )
      .addTransition(7, 8, "(sx2) (anon_27)")
      .addTransition(8, 9, "(anon_27) (anon_19)")
      .addTransition(8, 11, "(anon_27) (anon_28 WHERE anon_28:B)")
      .addTransition(9, 10, "(anon_19)-[anon_20:R]->(anon_21)")
      .addTransition(10, 9, "(anon_21) (anon_19)")
      .addTransition(10, 11, "(anon_21) (anon_28 WHERE anon_28:B)")
      .addTransition(11, 12, "(anon_28)<-[anon_29:R]-(anon_30 WHERE anon_30:B)")
      .addTransition(12, 13, "(anon_30)-[anon_31:R]->(end WHERE end:B)")
      .setFinalState(13)
      .build()

    plan should equal(planner.subPlanBuilder()
      .statefulShortestPath(
        "u",
        "end",
        "SHORTEST 1 (u)<-[`anon_0`]-(`anon_1`)-[`anon_2`]->(`anon_3`) ((`anon_10`)-[`anon_11`]->(`anon_12`)){1, } (`anon_4`) ((`sx1`)<-[`anon_13`]-(`anon_14`)-[`anon_15`]->(`x`)<-[`anon_16`]-(`anon_17`)-[`anon_18`]->(`sx2`)){0, 1} (`anon_5`) ((`anon_19`)-[`anon_20`]->(`anon_21`)){0, } (`anon_6`)<-[`anon_7`]-(`anon_8`)-[`anon_9`]->(end)",
        None,
        Set(("sx1", "sx1"), ("x", "x"), ("sx2", "sx2")),
        Set(),
        Set(
          ("anon_28", "anon_6"),
          ("anon_30", "anon_8"),
          ("anon_25", "anon_3"),
          ("anon_23", "anon_1"),
          ("anon_26", "anon_4"),
          ("anon_27", "anon_5"),
          ("end", "end")
        ),
        Set(("anon_22", "anon_0"), ("anon_24", "anon_2"), ("anon_29", "anon_7"), ("anon_31", "anon_9")),
        StatefulShortestPath.Selector.Shortest(CountInteger(1)),
        nfa,
        ExpandAll,
        false,
        5,
        None
      )
      .nodeIndexOperator("u:User(prop = 5)", _ => GetValue)
      .build())
  }

  test("long pattern and everything is named") {
    val query =
      s"""
         |MATCH SHORTEST 1
         |  (u:User {prop: 5})<-[r1:R]-
         |  (b1:B)-[r2:R]->
         |  (b2:B) ((b3)-[r3:R]->(b4))+ (b5)
         |  (
         |    (sx1)<-[r4:R]-
         |    (b6:B)-[r5:R]->
         |    (x:B)<-[r6:R]-
         |    (b7:B)-[r7:R]->
         |    (sx2:B)
         |    WHERE  sx1.prop + sx2.prop <= 60
         |  ){0,1}
         |  (b8) ((b9)-[r8:R]->(b10))*
         |  (b11:B)<-[r9:R]-
         |  (b12:B)-[r10:R]->
         |  (end:B)
         |RETURN *
         |""".stripMargin

    val plan = planner.plan(query).stripProduceResults

    val nfa = new TestNFABuilder(0, "u")
      .addTransition(0, 1, "(u)<-[r1:R]-(b1 WHERE b1:B)")
      .addTransition(1, 2, "(b1)-[r2:R]->(b2 WHERE b2:B)")
      .addTransition(2, 3, "(b2) (b3)")
      .addTransition(3, 4, "(b3)-[r3:R]->(b4)")
      .addTransition(4, 3, "(b4) (b3)")
      .addTransition(4, 5, "(b4) (b5)")
      .addTransition(5, 6, "(b5) (sx1)")
      .addTransition(5, 8, "(b5) (b8)")
      .addTransition(
        6,
        7,
        "(sx1)<-[r4:R]-(b6:B)-[r5:R]->(x:B)<-[r6:R]-(b7:B)-[r7:R]->(sx2:B)",
        compoundPredicate = "sx1.prop + sx2.prop <= 60"
      )
      .addTransition(7, 8, "(sx2) (b8)")
      .addTransition(8, 9, "(b8) (b9)")
      .addTransition(8, 11, "(b8) (b11 WHERE b11:B)")
      .addTransition(9, 10, "(b9)-[r8:R]->(b10)")
      .addTransition(10, 9, "(b10) (b9)")
      .addTransition(10, 11, "(b10) (b11 WHERE b11:B)")
      .addTransition(11, 12, "(b11)<-[r9:R]-(b12 WHERE b12:B)")
      .addTransition(12, 13, "(b12)-[r10:R]->(end WHERE end:B)")
      .setFinalState(13)
      .build()

    plan should equal(planner.subPlanBuilder()
      .statefulShortestPath(
        "u",
        "end",
        "SHORTEST 1 (u)<-[r1]-(b1)-[r2]->(b2) ((`b3`)-[`r3`]->(`b4`)){1, } (b5) ((`sx1`)<-[`r4`]-(`b6`)-[`r5`]->(`x`)<-[`r6`]-(`b7`)-[`r7`]->(`sx2`)){0, 1} (b8) ((`b9`)-[`r8`]->(`b10`)){0, } (b11)<-[r9]-(b12)-[r10]->(end)",
        None,
        Set(
          ("b10", "b10"),
          ("b7", "b7"),
          ("b3", "b3"),
          ("sx1", "sx1"),
          ("b6", "b6"),
          ("sx2", "sx2"),
          ("x", "x"),
          ("b4", "b4"),
          ("b9", "b9")
        ),
        Set(("r4", "r4"), ("r6", "r6"), ("r7", "r7"), ("r5", "r5"), ("r8", "r8"), ("r3", "r3")),
        Set(("b1", "b1"), ("b11", "b11"), ("b2", "b2"), ("b8", "b8"), ("b12", "b12"), ("b5", "b5"), ("end", "end")),
        Set(("r1", "r1"), ("r2", "r2"), ("r9", "r9"), ("r10", "r10")),
        StatefulShortestPath.Selector.Shortest(CountInteger(1)),
        nfa,
        ExpandAll,
        false,
        5,
        None
      )
      .nodeIndexOperator("u:User(prop = 5)", _ => GetValue)
      .build())
  }

  test("nested plan expression in non-inlined predicate") {

    val query = """
      MATCH p = ANY SHORTEST (s) ((a)-[r1]->(b)-[r2]->(c) WHERE EXISTS { (c)-->(a) })+ (t)
      RETURN s.id as s, t.id as t
    """

    val planner = plannerBase
      .enableDeduplicateNames(false)
      .build()

    val nestedPlan = planner.subPlanBuilder()
      .expandInto("(`  c@5`)-[]->(`  a@1`)")
      .argument("  a@1", "  c@5")
      .build()

    val solvedNestedExpressionAsString =
      """EXISTS { MATCH (`  c@5`)-[`  UNNAMED0`]->(`  a@1`) }""".stripMargin
    val nestedPlanExpression = Some(NestedPlanExistsExpression(
      plan = nestedPlan,
      solvedExpressionAsString =
        solvedNestedExpressionAsString
    )(pos))

    planner.plan(query) should equal(
      planner.planBuilder()
        .produceResults("`  s@7`", "`  t@8`")
        .projection("`  s@0`.id AS `  s@7`", "`  t@6`.id AS `  t@8`")
        .statefulShortestPath(
          "  s@0",
          "  t@6",
          "SHORTEST 1 (`  s@0`) ((`  a@1`)-[`  r1@2`]->(`  b@3`)-[`  r2@4`]->(`  c@5`)){1, } (`  t@6`)",
          None,
          Set(),
          Set(),
          Set(("  t@9", "  t@6")),
          Set(),
          StatefulShortestPath.Selector.Shortest(CountInteger(1)),
          new TestNFABuilder(0, "  s@0")
            .addTransition(0, 1, "(`  s@0`) (`  a@1`)")
            .addMultiRelationshipTransitionWithExpression(
              1,
              2,
              "(`  a@1`)-[`  r1@2`]->(`  b@3`)-[`  r2@4`]->(`  c@5`)",
              nestedPlanExpression
            )
            .addTransition(2, 1, "(`  c@5`) (`  a@1`)")
            .addTransition(2, 3, "(`  c@5`) (`  t@9`)")
            .setFinalState(3)
            .build(),
          ExpandAll,
          false,
          2,
          None
        )
        .allNodeScan("`  s@0`")
        .build()
    )
  }

  test(
    "Should rewrite equality property predicate which involves different property keys - transitivity does not hold"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setRelationshipCardinality("()-[:R]->()", 500)
      .withSetting(GraphDatabaseInternalSettings.multi_relationship_expansion_enabled, TRUE)
      .build()

    val query =
      """
        |MATCH ANY SHORTEST (leftOuter {id:1}) ((leftInner)-[r1:R]->(m)-[r2:R]->(rightInner) WHERE leftInner.prop = rightInner.p){2,4} (rightOuter)
        |RETURN leftOuter, rightOuter
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner.subPlanBuilder()
      .produceResults("leftOuter", "rightOuter")
      .statefulShortestPath(
        "leftOuter",
        "rightOuter",
        "SHORTEST 1 (leftOuter) ((`leftInner`)-[`r1`]->(`m`)-[`r2`]->(`rightInner`)){2, 4} (rightOuter)",
        None,
        Set(),
        Set(),
        Set(("rightOuter", "rightOuter")),
        Set(),
        StatefulShortestPath.Selector.Shortest(CountInteger(1)),
        new TestNFABuilder(0, "leftOuter")
          .addTransition(0, 1, "(leftOuter) (leftInner)")
          .addTransition(
            1,
            2,
            "(leftInner)-[r1:R]->(m)-[r2:R]->(rightInner)",
            compoundPredicate = "cacheNFromStore[leftInner.prop] = cacheNFromStore[rightInner.p]"
          )
          .addTransition(2, 3, "(rightInner) (leftInner)")
          .addTransition(
            3,
            4,
            "(leftInner)-[r1:R]->(m)-[r2:R]->(rightInner)",
            compoundPredicate = "cacheNFromStore[leftInner.prop] = cacheNFromStore[rightInner.p]"
          )
          .addTransition(4, 5, "(rightInner) (leftInner)")
          .addTransition(4, 9, "(rightInner) (rightOuter)")
          .addTransition(
            5,
            6,
            "(leftInner)-[r1:R]->(m)-[r2:R]->(rightInner)",
            compoundPredicate = "cacheNFromStore[leftInner.prop] = cacheNFromStore[rightInner.p]"
          )
          .addTransition(6, 7, "(rightInner) (leftInner)")
          .addTransition(6, 9, "(rightInner) (rightOuter)")
          .addTransition(
            7,
            8,
            "(leftInner)-[r1:R]->(m)-[r2:R]->(rightInner)",
            compoundPredicate = "cacheNFromStore[leftInner.prop] = cacheNFromStore[rightInner.p]"
          )
          .addTransition(8, 9, "(rightInner) (rightOuter)")
          .setFinalState(9)
          .build(),
        ExpandAll,
        false,
        4,
        Some(8)
      )
      .filter("leftOuter.id = 1")
      .allNodeScan("leftOuter")
      .build()
  }
}

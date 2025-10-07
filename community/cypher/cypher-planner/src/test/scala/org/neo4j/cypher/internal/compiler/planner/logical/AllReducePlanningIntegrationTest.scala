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
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfiguration
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.ir.SelectivePathPattern.CountInteger
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.TrailParameters
import org.neo4j.cypher.internal.logical.builder.TestNFABuilder
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandInto
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode.Trail
import org.neo4j.cypher.internal.util.UpperBound.Limited
import org.neo4j.cypher.internal.util.UpperBound.Unlimited
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class AllReducePlanningIntegrationTest extends CypherFunSuite with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport with CypherVersionTestSupport {

  protected val baseConfig: StatisticsBackedLogicalPlanningConfigurationBuilder = plannerBuilder()
    .setAllNodesCardinality(100)
    .setAllRelationshipsCardinality(50)
    .setRelationshipCardinality("(:N)-[]->()", 35)
    .setRelationshipCardinality("(:N)-[]->(:NN)", 35)
    .setRelationshipCardinality("()-[]->(:NN)", 35)
    .setRelationshipCardinality("(:NN)-[]->()", 15)
    .setRelationshipCardinality("(:NN)-[]->(:NN)", 15)
    .setLabelCardinality("N", 6)
    .setLabelCardinality("NN", 5)
    .addNodeIndex("NN", Seq("prop"), 0.1, 0.01)
    .addSemanticFeature(SemanticFeature.ExperimentalCypherVersions)

  protected val planner: StatisticsBackedLogicalPlanningConfiguration = baseConfig.build()

  protected val nonDedupeNamePlanner: StatisticsBackedLogicalPlanningConfiguration = baseConfig
    .enableDeduplicateNames(false)
    .build()

  test("allReduce in return clause should fallback to post-filter reduce() - node group variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      "MATCH (os)((is)-[r]->(ie)){1,3}(oe) RETURN allReduce(acc = 0, node IN ie | acc + node.x, acc < 12) AS result"
    ).stripProduceResults

    val trailParameters = TrailParameters(
      min = 1,
      max = Limited(3),
      start = "os",
      end = "oe",
      innerStart = "is",
      innerEnd = "ie",
      groupNodes = Set(("ie", "ie")),
      groupRelationships = Set.empty,
      innerRelationships = Set("r"),
      previouslyBoundRelationships = Set.empty,
      previouslyBoundRelationshipGroups = Set.empty,
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set.empty
    )

    plan shouldEqual planner.subPlanBuilder()
      // reduce(acc = {accumulator: 0, result: true}, ie IN ie | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator + ie.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result AS `allReduce(acc = 0, acc + ie.x, acc < 12)`
      .projection(Map("result" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(0),
        stepVariable = v"node",
        groupVariable = v"ie",
        allReduceStepExpression = add(v"acc", prop(v"node", "x")),
        allReducePredicate = lessThan(v"acc", literalInt(12)),
        nextAnonymousVariable = v"anon_0"
      )))
      .repeatTrail(trailParameters)
      .|.filterExpression(isRepeatTrailUnique("r"))
      .|.expandAll("(is)-[r]->(ie)")
      .|.argument("is")
      .allNodeScan("os")
      .build()
  }

  test("allReduce in return clause should fallback to post-filter reduce() - relationship group variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      "MATCH (os)((is)-[r]->(ie)){1,3}(oe) RETURN allReduce(acc = 1, rel IN r | acc * rel.x, acc < 12)"
    ).stripProduceResults

    plan shouldEqual planner.subPlanBuilder()
      // reduce(acc = {accumulator: 1, result: true}, r IN r | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator * r.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result AS `allReduce(acc = 1, acc * r.x, acc < 12)`
      .projection(Map("allReduce(acc = 1, rel IN r | acc * rel.x, acc < 12)" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(1),
        stepVariable = v"rel",
        groupVariable = v"r",
        allReduceStepExpression = multiply(v"acc", prop(v"rel", "x")),
        allReducePredicate = lessThan(v"acc", literalInt(12)),
        nextAnonymousVariable = v"anon_0"
      )))
      .expand("(os)-[r*1..3]->()")
      .allNodeScan("os")
      .build()
  }

  test("allReduce in WITH clause should fallback to post-filter reduce()") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      "MATCH (os)((is)-[r]->(ie)){1,3}(oe) WITH allReduce(acc = 1, rel IN r | acc * rel.x, acc < 12) AS x RETURN 1"
    ).stripProduceResults

    plan shouldEqual planner.subPlanBuilder()
      // reduce(acc = {accumulator: 1, result: true}, r IN r | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator * r.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result AS x
      .projection("1 AS 1")
      .projection(Map("x" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(1),
        stepVariable = v"rel",
        groupVariable = v"r",
        allReduceStepExpression = multiply(v"acc", prop(v"rel", "x")),
        allReducePredicate = lessThan(v"acc", literalInt(12)),
        nextAnonymousVariable = v"anon_0"
      )))
      .expand("(os)-[r*1..3]->()")
      .allNodeScan("os")
      .build()
  }

  test("allReduce in SSP clause should fallback to post-filter reduce()") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      "MATCH ANY (os)((is)-[r]->(ie)){1,3}(oe) WHERE allReduce(acc = 1, rel IN r | acc * rel.x, acc < 12) RETURN 1"
    ).stripProduceResults

    plan shouldEqual planner.subPlanBuilder()
      // reduce(acc = {accumulator: 1, result: true}, r IN r | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator * r.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result
      .projection("1 AS 1")
      .filterExpression(allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(1),
        stepVariable = v"rel",
        groupVariable = v"r",
        allReduceStepExpression = multiply(v"acc", prop(v"rel", "x")),
        allReducePredicate = lessThan(v"acc", literalInt(12)),
        nextAnonymousVariable = v"anon_0"
      ))
      .statefulShortestPath(
        sourceNode = "os",
        targetNode = "oe",
        solvedExpressionString = "ANY 1 (os) ((`is`)-[`r`]->(`ie`)){1, 3} (oe)",
        nonInlinedPreFilters = None,
        groupNodes = Set(),
        groupRelationships = Set(("r", "r")),
        singletonNodeVariables = Set(("oe", "oe")),
        singletonRelationshipVariables = Set(),
        selector = StatefulShortestPath.Selector.Shortest(CountInteger(1)),
        nfa = new TestNFABuilder(0, "os")
          .addTransition(0, 1, "(os) (is)")
          .addTransition(1, 2, "(is)-[r]->(ie)")
          .addTransition(2, 3, "(ie) (is)")
          .addTransition(2, 7, "(ie) (oe)")
          .addTransition(3, 4, "(is)-[r]->(ie)")
          .addTransition(4, 5, "(ie) (is)")
          .addTransition(4, 7, "(ie) (oe)")
          .addTransition(5, 6, "(is)-[r]->(ie)")
          .addTransition(6, 7, "(ie) (oe)")
          .setFinalState(7)
          .build(),
        mode = ExpandAll,
        minLength = 1,
        maxLength = Some(3),
        pathMode = Trail
      )
      .allNodeScan("os")
      .build()
  }

  test("nested allReduce in RETURN clause should fallback to post-filter reduce()") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """
        |MATCH (a)-[r]->+(b)
        |RETURN allReduce(
        |  acc = allReduce(acc = [], rel IN r | acc + rel, size(acc) < 5),
        |  rel IN r | toInteger(acc) + rel.prop,
        |  toInteger(acc) <= 5
        |) AS result
        |""".stripMargin
    ).stripProduceResults

    plan shouldEqual planner.subPlanBuilder()
      // reduce(
      //   acc = {
      //     accumulator:
      //       reduce(acc = {accumulator: [], result: true}, r IN r | CASE
      //         WHEN acc.result = false THEN acc
      //         ELSE [anon_0 IN [acc.accumulator + r] | {accumulator: anon_0, result: acc.result AND size(anon_0) < 5}][0]
      //       END).result,
      //     result: true
      //   },
      //   r IN r | CASE
      //     WHEN acc.result = false THEN acc
      //     ELSE [anon_1 IN [toInteger(acc.accumulator) + r.prop] | {accumulator: anon_1, result: acc.result AND toInteger(anon_1) <= 5}][0]
      //   END).result AS `allReduce(acc = allReduce(acc = [], acc + r, size(acc) < 5), toInteger(acc) + r.prop, toInteger(acc) <= 5)`
      .projection(Map(
        "result" ->
          allReduceFallBack(
            accumulator = v"acc",
            init = allReduceFallBack(
              accumulator = v"acc",
              init = ListLiteral(Seq.empty)(pos),
              stepVariable = v"rel",
              groupVariable = v"r",
              allReduceStepExpression = add(v"acc", v"rel"),
              allReducePredicate = lessThan(size(v"acc"), literalInt(5)),
              nextAnonymousVariable = v"anon_0"
            ),
            stepVariable = v"rel",
            groupVariable = v"r",
            allReduceStepExpression = add(function("toInteger", v"acc"), prop(v"rel", "prop")),
            allReducePredicate = lessThanOrEqual(function("toInteger", v"acc"), literalInt(5)),
            nextAnonymousVariable = v"anon_1"
          )
      ))
      .expand("(a)-[r*1..]->()")
      .allNodeScan("a")
      .build()
  }

  test("Should handle, but not inline, nested allReduces - in init") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)-[r]->+(b)
        |WHERE allReduce(acc = allReduce(acc = [], rel IN r | acc + rel, size(acc) < 5),
        |                rel IN r | toInteger(acc) + rel.prop,
        |                toInteger(acc) <= 5)
        |RETURN a, b""".stripMargin
    ).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filterExpression(
          allReduceFallBack(
            accumulator = v"acc",
            init = allReduceFallBack(
              accumulator = v"acc",
              init = ListLiteral(Seq.empty)(pos),
              stepVariable = v"rel",
              groupVariable = v"r",
              allReduceStepExpression = add(v"acc", v"rel"),
              allReducePredicate = lessThan(size(v"acc"), literalInt(5)),
              nextAnonymousVariable = v"anon_0"
            ),
            stepVariable = v"rel",
            groupVariable = v"r",
            allReduceStepExpression = add(function("toInteger", v"acc"), prop(v"rel", "prop")),
            allReducePredicate = lessThanOrEqual(function("toInteger", v"acc"), literalInt(5)),
            nextAnonymousVariable = v"anon_1"
          )
        )
        .expand("(a)-[r*1..]->(b)")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline allReduce with no dependencies") {
    val plan = nonDedupeNamePlanner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b:NN)
        |  WHERE allReduce(
        |    sum = 0,
        |    step IN rel | sum + step.prop,
        |    sum < 99
        |  )
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "  left@0",
      innerEnd = "  right@2",
      groupNodes = Set(),
      groupRelationships = Set(("  rel@1", "  rel@3")),
      innerRelationships = Set("  rel@1"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "sum", "  sum@4"))
    )

    plan should equal(
      nonDedupeNamePlanner.planBuilder()
        .produceResults("`  rel@3`")
        .filter("b:NN")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString(isRepeatTrailUnique("  rel@1"), "`  sum@4` < 99")
        .|.projection("sum + `  rel@1`.prop AS `  sum@4`")
        .|.expandAll("(`  left@0`)-[`  rel@1`]->(`  right@2`)")
        .|.argument("  left@0", "sum")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should force left-to-right plan of QPP with inlined allReduce") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b:NN)
        |  WHERE
        |    b.prop = 42 AND
        |    allReduce(
        |      sum = 0,
        |      step IN rel | sum + step.prop,
        |      sum < 99
        |    )
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandInto,
      accumulators = Set(("0", "sum", "sum"))
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString("sum < 99", isRepeatTrailUnique("rel"))
        .|.projection("sum + rel.prop AS sum")
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left", "sum")
        .cartesianProduct()
        .|.nodeIndexOperator("b:NN(prop = 42)")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should NOT inline allReduce, if it is not the top-level predicate") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b)
        |  WHERE
        |    allReduce(
        |      sum = 0,
        |      step IN rel | sum + step.prop,
        |      sum < 99
        |    ) IS NULL
        |RETURN rel""".stripMargin
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .filterExpression(
          isNull(
            allReduceFallBack(
              accumulator = v"sum",
              init = literalInt(0),
              stepVariable = v"step",
              groupVariable = v"rel",
              allReduceStepExpression = add(v"sum", prop("step", "prop")),
              allReducePredicate = lessThan(v"sum", literalInt(99)),
              nextAnonymousVariable = v"anon_0"
            )
          )
        )
        .expand("(a)-[rel*1..]->()")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline allReduce as early as possible") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(middle)-[rel2]->(right))+
        |      (b)
        |  WHERE
        |    allReduce(
        |      sum = 0,
        |      step IN rel | sum + step.prop,
        |      sum < 99
        |    )
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel", "rel2"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set((
        "0",
        "sum",
        "sum"
      ))
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString("NOT rel2 = rel", isRepeatTrailUnique("rel2"))
        .|.expandAll("(middle)-[rel2]->(right)")
        .|.filterExpressionOrString("sum < 99", isRepeatTrailUnique("rel"))
        .|.projection("sum + rel.prop AS sum")
        .|.expandAll("(left)-[rel]->(middle)")
        .|.argument("left", "sum")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline two allReduces with the same group variable from the same QPP") {
    val plan = nonDedupeNamePlanner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b:NN)
        |WHERE
        |  allReduce(sum = 0, step IN rel | sum + step.prop, sum < 99) AND
        |  allReduce(
        |    span = {},
        |    spanStep IN rel | { previous: span.current, current: spanStep.q },
        |    coalesce(span.previous < span.current, true)
        |  )
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH sum = 0 AND span = {}` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "  left@0",
      innerEnd = "  right@2",
      groupNodes = Set(),
      groupRelationships = Set(("  rel@1", "  rel@3")),
      innerRelationships = Set("  rel@1"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(
        ("0", "sum", "  sum@4"),
        ("{}", "span", "  span@5")
      )
    )

    plan should equal(
      planner.subPlanBuilder()
        .filter("b:NN")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0 AND span = {}`)
        .|.filterExpressionOrString(
          "`  sum@4` < 99",
          "coalesce(`  span@5`.previous < `  span@5`.current, true)",
          isRepeatTrailUnique("  rel@1")
        )
        .|.projection(
          "sum + cacheR[`  rel@1`.prop] AS `  sum@4`",
          "{ previous: span.current, current: cacheR[`  rel@1`.q] } AS `  span@5`"
        )
        .|.cacheProperties("cacheRFromStore[`  rel@1`.prop]", "cacheRFromStore[`  rel@1`.q]")
        .|.expandAll("(`  left@0`)-[`  rel@1`]->(`  right@2`)")
        .|.argument("  left@0", "sum", "span")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline two allReduces with different group variables from the same QPP") {
    val plan = nonDedupeNamePlanner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b:NN)
        |WHERE
        |  allReduce(sum = 0, step IN rel | sum + step.prop, sum < 99) AND
        |  allReduce(people = [], rightStep IN right | people + CASE WHEN rightStep:Person THEN [rightStep] ELSE [] END, size(people) < 10)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH sum = 0 AND people = []` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "  left@0",
      innerEnd = "  right@2",
      groupNodes = Set(),
      groupRelationships = Set(("  rel@1", "  rel@3")),
      innerRelationships = Set("  rel@1"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(
        ("0", "sum", "  sum@4"),
        ("[]", "people", "  people@5")
      )
    )

    plan should equal(
      planner.subPlanBuilder()
        .filter("b:NN")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0 AND people = []`)
        .|.filterExpressionOrString(isRepeatTrailUnique("  rel@1"), "`  sum@4` < 99", "size(`  people@5`) < 10")
        .|.projection(
          "sum + `  rel@1`.prop AS `  sum@4`",
          "people + CASE WHEN `  right@2`:Person THEN [`  right@2`] ELSE [] END AS `  people@5`"
        )
        .|.expandAll("(`  left@0`)-[`  rel@1`]->(`  right@2`)")
        .|.argument("  left@0", "sum", "people")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should handle two allReduce accumulators with the same name by renaming both") {
    val plan = nonDedupeNamePlanner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b:NN)
        |WHERE
        |  allReduce(acc = 0, step IN rel | acc + step.prop, acc < 99) AND
        |  allReduce(acc = 1, step IN rel | acc * step.prop, 0 < acc < 1)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "  left@0",
      innerEnd = "  right@2",
      groupNodes = Set(),
      groupRelationships = Set(("  rel@1", "  rel@3")),
      innerRelationships = Set("  rel@1"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(
        ("0", "  acc@4", "  acc@6"),
        ("1", "  acc@5", "  acc@7")
      )
    )

    plan should equal(
      planner.subPlanBuilder()
        .filter("b:NN")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString(
          "0 < `  acc@7` < 1",
          "`  acc@6` < 99",
          isRepeatTrailUnique("  rel@1")
        )
        .|.projection(
          "`  acc@4` + cacheRFromStore[`  rel@1`.prop] AS `  acc@6`",
          "`  acc@5` * cacheRFromStore[`  rel@1`.prop] AS `  acc@7`"
        )
        .|.expandAll("(`  left@0`)-[`  rel@1`]->(`  right@2`)")
        .|.argument("  left@0", "  acc@4", "  acc@5")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline two allReduce for two different QPPs") {
    val plan = nonDedupeNamePlanner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((l1)-[rel1]->(r1)){,10}
        |      (x:NN)
        |      ((l2)-[rel2]->(r2)){0,1}
        |      (b:NN)
        |WHERE
        |  allReduce(sum = 0, step1 IN rel1 | sum + step1.prop, sum < 99) AND
        |  allReduce(product = 1, step2 IN rel2 | product * step2.prop, 0 < product < 1)
        |RETURN rel1, rel2""".stripMargin
    ).stripProduceResults

    val `((l1)-[rel1]->(r1)){,10} WITH sum = 0` = TrailParameters(
      min = 0,
      max = Limited(10),
      start = "a",
      end = "x",
      innerStart = "  l1@0",
      innerEnd = "  r1@2",
      groupNodes = Set(),
      groupRelationships = Set(("  rel1@1", "  rel1@3")),
      innerRelationships = Set("  rel1@1"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "sum", "  sum@9"))
    )

    val `((l2)-[rel2]->(r2))+ WITH product = 1` = TrailParameters(
      min = 0,
      max = Limited(1),
      start = "x",
      end = "b",
      innerStart = "  l2@4",
      innerEnd = "  r2@6",
      groupNodes = Set(),
      groupRelationships = Set(("  rel2@5", "  rel2@7")),
      innerRelationships = Set("  rel2@5"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set("  rel1@3"),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("1", "product", "  product@8"))
    )

    plan should equal(
      planner.subPlanBuilder()
        .filter("b:NN")
        .repeatTrail(`((l2)-[rel2]->(r2))+ WITH product = 1`)
        .|.filterExpressionOrString("0 < `  product@8` < 1", isRepeatTrailUnique("  rel2@5"))
        .|.projection("product * `  rel2@5`.prop AS `  product@8`")
        .|.expandAll("(`  l2@4`)-[`  rel2@5`]->(`  r2@6`)")
        .|.argument("  l2@4", "product")
        .filter("x:NN")
        .repeatTrail(`((l1)-[rel1]->(r1)){,10} WITH sum = 0`)
        .|.filterExpressionOrString("`  sum@9` < 99", isRepeatTrailUnique("  rel1@1"))
        .|.projection("sum + `  rel1@1`.prop AS `  sum@9`")
        .|.expandAll("(`  l1@0`)-[`  rel1@1`]->(`  r1@2`)")
        .|.argument("  l1@0", "sum")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should NOT inline nested allReduces - in reduction step") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)-[r]->+(b)
        |WHERE
        |  allReduce(
        |    acc = true,
        |    rel IN r |
        |      acc AND rel.prop AND
        |        allReduce(
        |          acc = [],
        |          rel IN r | acc + rel,
        |          size(acc) < 5
        |        ),
        |    acc
        |  )
        |RETURN a, b""".stripMargin
    ).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filterExpression(
          allReduceFallBack(
            accumulator = v"acc",
            init = literalBoolean(true),
            groupVariable = v"r",
            allReduceStepExpression = ands(
              v"acc",
              prop("rel", "prop"),
              allReduceFallBack(
                accumulator = v"acc",
                init = ListLiteral(Seq.empty)(pos),
                stepVariable = v"rel",
                groupVariable = v"r",
                allReduceStepExpression = add(v"acc", v"rel"),
                allReducePredicate = lessThan(size(v"acc"), literalInt(5)),
                nextAnonymousVariable = v"anon_0"
              )
            ),
            stepVariable = v"rel",
            allReducePredicate = v"acc",
            nextAnonymousVariable = v"anon_1"
          )
        )
        .expand("(a)-[r*1..]->(b)")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should handle nested allReduces - in predicate") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)-[r]->+(b)
        |WHERE allReduce(acc = 0,
        |                step IN r | acc + step.prop,
        |                acc > toInteger(allReduce(acc = [], step IN r | acc + step, size(acc) < 5)))
        |RETURN a, b""".stripMargin
    ).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filterExpression(
          allReduceFallBack(
            accumulator = v"acc",
            init = literalInt(0),
            stepVariable = v"step",
            groupVariable = v"r",
            allReduceStepExpression = add(v"acc", prop("step", "prop")),
            allReducePredicate = greaterThan(
              v"acc",
              function(
                "toInteger",
                allReduceFallBack(
                  accumulator = v"acc",
                  init = ListLiteral(Seq.empty)(pos),
                  groupVariable = v"r",
                  allReduceStepExpression = add(v"acc", v"step"),
                  allReducePredicate = lessThan(size(v"acc"), literalInt(5)),
                  nextAnonymousVariable = v"anon_0",
                  stepVariable = v"step"
                )
              )
            ),
            nextAnonymousVariable = v"anon_1"
          )
        )
        .expand("(a)-[r*1..]->(b)")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should NOT inline allReduce if predicate has dependency on unavailable non-local variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b)
        |  WHERE
        |    allReduce(
        |      sum = 0,
        |      step IN rel | sum + step.prop,
        |      sum < b.prop
        |    )
        |RETURN rel""".stripMargin
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(0),
            stepVariable = v"step",
            groupVariable = v"rel",
            allReduceStepExpression = add(v"sum", prop("step", "prop")),
            allReducePredicate = greaterThan(prop("b", "prop"), v"sum"),
            nextAnonymousVariable = v"anon_0"
          )
        )
        .expand("(a)-[rel*1..]->(b)")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline allReduce if predicate has dependency on available non-local variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)
        |      ((left)-[rel]->(right))+
        |      (b)
        |  WHERE
        |    allReduce(
        |      sum = 0,
        |      step IN rel | sum + step.prop,
        |      sum < a.prop
        |    )
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "sum", "sum"))
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString("a.prop > sum", isRepeatTrailUnique("rel"))
        .|.projection("sum + rel.prop AS sum")
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left", "sum", "a")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline allReduce when init depends on available non-local variable") {
    val plan = nonDedupeNamePlanner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N) ((left)-[rel]->(right))+ (b)
        |  WHERE allReduce(sum = a.prop, step IN rel | sum + step.prop, sum < 99)
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = a.prop` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "  left@0",
      innerEnd = "  right@2",
      groupNodes = Set(),
      groupRelationships = Set(("  rel@1", "  rel@3")),
      innerRelationships = Set("  rel@1"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("a.prop", "sum", "  sum@4"))
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("`  rel@3`")
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = a.prop`)
        .|.filterExpressionOrString("`  sum@4` < 99", isRepeatTrailUnique("  rel@1"))
        .|.projection("sum + `  rel@1`.prop AS `  sum@4`")
        .|.expandAll("(`  left@0`)-[`  rel@1`]->(`  right@2`)")
        .|.argument("  left@0", "sum")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should NOT inline allReduce when init depends on unavailable non-local variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N) ((left)-[rel]->(right))+ (b)
        |  WHERE allReduce(sum = b.prop, step IN rel | sum + step.prop, sum < 99)
        |RETURN rel""".stripMargin
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = prop("b", "prop"),
            stepVariable = v"step",
            groupVariable = v"rel",
            allReduceStepExpression = add(v"sum", prop("step", "prop")),
            allReducePredicate = lessThan(v"sum", literalInt(99)),
            nextAnonymousVariable = v"anon_0"
          )
        )
        .expand("(a)-[rel*1..]->(b)")
        .nodeByLabelScan("a", "N", IndexOrderNone)
        .build()
    )
  }

  test("Should NOT inline allReduce if init has dependency on another variable from same QPP") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N) ((left)-[rel]->(right))+ (b)
        |  WHERE allReduce(sum = size(left), step IN rel | sum + step.prop, sum < 99)
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = a.prop` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(("left", "left")),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set()
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = size(v"left"),
            groupVariable = v"rel",
            allReduceStepExpression = add(v"sum", prop("step", "prop")),
            allReducePredicate = lessThan(v"sum", literalInt(99)),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"step"
          )
        )
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = a.prop`)
        .|.filterExpressionOrString(isRepeatTrailUnique("rel"))
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should NOT inline allReduce if predicate has dependency on another variable from same QPP") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N) ((left)-[rel]->(right))+ (b)
        |  WHERE allReduce(sum = 99, step IN rel | sum + step.prop, sum < size(left))
        |RETURN rel""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = a.prop` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(("left", "left")),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set()
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("rel")
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(99),
            groupVariable = v"rel",
            allReduceStepExpression = add(v"sum", prop("step", "prop")),
            allReducePredicate = lessThan(v"sum", size(v"left")),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"step"
          )
        )
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = a.prop`)
        .|.filterExpressionOrString(isRepeatTrailUnique("rel"))
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should NOT inline allReduce if reductionStep has dependency on another variable from same QPP") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N) ((left)-[r]->(right))+ (b)
        |  WHERE allReduce(sum = 99, rel IN r | sum + rel.prop + size(left), sum > 0)
        |RETURN r""".stripMargin
    )

    val `((left)-[rel]->(right))+ WITH sum = a.prop` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(("left", "left")),
      groupRelationships = Set(("r", "r")),
      innerRelationships = Set("r"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set()
    )

    plan should equal(
      planner.planBuilder()
        .produceResults("r")
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(99),
            groupVariable = v"r",
            allReduceStepExpression = add(add(v"sum", prop("rel", "prop")), size(v"left")),
            allReducePredicate = greaterThan(v"sum", literalInt(0)),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"rel"
          )
        )
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = a.prop`)
        .|.filterExpressionOrString(isRepeatTrailUnique("r"))
        .|.expandAll("(left)-[r]->(right)")
        .|.argument("left")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should inline allReduce when reduction function depends on available non-local variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)-[avail]->+(x) ((left)-[rel]->(right))+ (b)
        |  WHERE allReduce(sum = 0, r IN rel | sum + r.prop + size(avail), sum < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "x",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set("avail"),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "sum", "sum"))
    )

    plan should equal(
      planner.subPlanBuilder()
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString("sum < 99", isRepeatTrailUnique("rel"))
        .|.projection("sum + rel.prop + size(avail) AS sum")
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left", "sum", "avail")
        .expand("(a)-[avail*1..]->(x)")
        .nodeByLabelScan("a", "N")
        .build()
    )
  }

  test("Should plan allReduce before QPP if it does not depend on the QPP") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """WITH [1, 2, 3] AS list
        |MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(sum = 0, step IN list | sum + step, sum < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .expand("(a)-[rel*1..]->()")
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(0),
            groupVariable = v"list",
            allReduceStepExpression = add(v"sum", v"step"),
            allReducePredicate = lessThan(v"sum", literalInt(99)),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"step"
          )
        )
        .apply()
        .|.nodeByLabelScan("a", "N", IndexOrderNone, "list")
        .projection("[1, 2, 3] AS list")
        .argument()
        .build()
    )
  }

  test("Should not inline allReduce when the reduction iterates over a list from outside the QPP") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """WITH [1, 2, 3] AS list
        |MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(sum = 0, step IN list | sum + step + size(rel), sum < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(0),
            groupVariable = v"list",
            allReduceStepExpression = add(add(v"sum", v"step"), size(v"rel")),
            allReducePredicate = lessThan(v"sum", literalInt(99)),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"step"
          )
        )
        .expand("(a)-[rel*1..]->()")
        .apply()
        .|.nodeByLabelScan("a", "N", IndexOrderNone, "list")
        .projection("[1, 2, 3] AS list")
        .argument()
        .build()
    )
  }

  test("Should not inline allReduce when the reduction expression uses the group variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """WITH [1, 2, 3] AS list
        |MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(sum = 0, step IN rel | sum + size(rel), sum < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    plan should equal(
      planner.subPlanBuilder()
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(0),
            groupVariable = v"rel",
            allReduceStepExpression = add(v"sum", size(v"rel")),
            allReducePredicate = lessThan(v"sum", literalInt(99)),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"step"
          )
        )
        .expand("(a)-[rel*1..]->()")
        .apply()
        .|.nodeByLabelScan("a", "N", IndexOrderNone, "list")
        .projection("[1, 2, 3] AS list")
        .argument()
        .build()
    )
  }

  test("Should support inlined allReduces when the reduction variable has the same name as group variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(sum = 0, rel IN rel | sum + rel.prop, sum < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH sum = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "sum", "sum"))
    )

    plan should equal(
      planner.subPlanBuilder()
        .repeatTrail(`((left)-[rel]->(right))+ WITH sum = 0`)
        .|.filterExpressionOrString("sum < 99", isRepeatTrailUnique("rel"))
        .|.projection("sum + rel.prop AS sum")
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left", "sum")
        .nodeByLabelScan("a", "N", IndexOrderNone)
        .build()
    )
  }

  test("Should support non-inlined allReduces when the reduction variable has the same name as group variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(sum = 0, rel IN rel | sum + rel.prop + size(right), sum < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(("right", "right")),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set.empty,
      previouslyBoundRelationshipGroups = Set.empty,
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set.empty
    )

    plan should equal(
      planner.subPlanBuilder()
        .filterExpression(
          allReduceFallBack(
            accumulator = v"sum",
            init = literalInt(0),
            groupVariable = v"rel",
            allReduceStepExpression = add(add(v"sum", prop(v"rel", "prop")), size(v"right")),
            allReducePredicate = lessThan(v"sum", literalInt(99)),
            nextAnonymousVariable = v"anon_0",
            stepVariable = v"rel"
          )
        )
        .repeatTrail(`((left)-[rel]->(right))+`)
        .|.filterExpressionOrString(isRepeatTrailUnique("rel"))
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left")
        .nodeByLabelScan("a", "N", IndexOrderNone)
        .build()
    )
  }

  test("Should inline allReduce when the accumulator shadows a group variable") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(left = 0, rel IN rel | left + rel.prop, left < 99)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH left = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "left", "left"))
    )

    plan should equal(
      planner.subPlanBuilder()
        .repeatTrail(`((left)-[rel]->(right))+ WITH left = 0`)
        .|.filterExpressionOrString("left < 99", isRepeatTrailUnique("rel"))
        .|.projection("left + rel.prop AS left")
        .|.expandAll("(left)-[rel]->(right)")
        .|.argument("left")
        .nodeByLabelScan("a", "N", IndexOrderNone)
        .build()
    )
  }

  test("should inline allReduce when step variable is used in predicate") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)((left)-[rel]->(right))+(b)
        |  WHERE allReduce(acc = 0, rel IN rel | acc + rel.prop, acc < rel.otherProp)
        |RETURN rel""".stripMargin
    ).stripProduceResults

    val `((left)-[rel]->(right))+ WITH acc = 0` = TrailParameters(
      min = 1,
      max = Unlimited,
      start = "a",
      end = "b",
      innerStart = "left",
      innerEnd = "right",
      groupNodes = Set(),
      groupRelationships = Set(("rel", "rel")),
      innerRelationships = Set("rel"),
      previouslyBoundRelationships = Set(),
      previouslyBoundRelationshipGroups = Set(),
      reverseGroupVariableProjections = false,
      expansionMode = ExpandAll,
      accumulators = Set(("0", "acc", "acc"))
    )

    plan shouldEqual planner.subPlanBuilder()
      .repeatTrail(`((left)-[rel]->(right))+ WITH acc = 0`)
      .|.filterExpressionOrString("rel.otherProp > acc", isRepeatTrailUnique("rel"))
      .|.projection("acc + rel.prop AS acc")
      .|.expandAll("(left)-[rel]->(right)")
      .|.argument("left", "acc")
      .nodeByLabelScan("a", "N", IndexOrderNone)
      .build()
  }

  test("should support non-inlined allReduce when step variable is used in predicate") {
    val plan = planner.plan(
      CypherVersion.Cypher25,
      """MATCH (a:N)((left)-[rel]->(right))+(b)
        |RETURN allReduce(acc = 0, r IN rel | acc + r.prop, acc < r.otherProp) AS res""".stripMargin
    ).stripProduceResults

    plan shouldEqual planner.subPlanBuilder()
      .projection(Map("res" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(0),
        stepVariable = v"r",
        groupVariable = v"rel",
        allReduceStepExpression = add(v"acc", prop(v"r", "prop")),
        allReducePredicate = greaterThan(prop(v"r", "otherProp"), v"acc"),
        nextAnonymousVariable = v"anon_0"
      )))
      .expandAll("(a)-[rel*1..]->()")
      .nodeByLabelScan("a", "N", IndexOrderNone)
      .build()
  }
}

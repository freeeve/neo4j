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
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.ir.SelectivePathPattern.CountInteger
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.TrailParameters
import org.neo4j.cypher.internal.logical.builder.TestNFABuilder
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode.Trail
import org.neo4j.cypher.internal.util.UpperBound.Limited
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class AllReducePlanningIntegrationTest extends CypherFunSuite with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport with CypherVersionTestSupport {

  test("allReduce in return clause should fallback to post-filter reduce() - node group variable") {
    val cfg = plannerBuilder()
      .setAllNodesCardinality(100)
      .setAllRelationshipsCardinality(50)
      .addSemanticFeature(SemanticFeature.AllReduceFunctionAvailable)
      .addSemanticFeature(SemanticFeature.ExperimentalCypherVersions)
      .build()

    val plan = cfg.plan(
      CypherVersion.Cypher25,
      "MATCH (os)((is)-[r]->(ie)){1,3}(oe) RETURN allReduce(acc = 0, acc + ie.x, acc < 12)"
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

    plan shouldEqual cfg.subPlanBuilder()
      // reduce(acc = {accumulator: 0, result: true}, ie IN ie | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator + ie.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result AS `allReduce(acc = 0, acc + ie.x, acc < 12)`
      .projection(Map("allReduce(acc = 0, acc + ie.x, acc < 12)" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(0),
        groupVariable = v"ie",
        allReduceStepExpression = add(v"acc", prop(v"ie", "x")),
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
    val cfg = plannerBuilder()
      .setAllNodesCardinality(100)
      .setAllRelationshipsCardinality(50)
      .addSemanticFeature(SemanticFeature.AllReduceFunctionAvailable)
      .addSemanticFeature(SemanticFeature.ExperimentalCypherVersions)
      .build()

    val plan = cfg.plan(
      CypherVersion.Cypher25,
      "MATCH (os)((is)-[r]->(ie)){1,3}(oe) RETURN allReduce(acc = 1, acc * r.x, acc < 12)"
    ).stripProduceResults

    plan shouldEqual cfg.subPlanBuilder()
      // reduce(acc = {accumulator: 1, result: true}, r IN r | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator * r.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result AS `allReduce(acc = 1, acc * r.x, acc < 12)`
      .projection(Map("allReduce(acc = 1, acc * r.x, acc < 12)" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(1),
        groupVariable = v"r",
        allReduceStepExpression = multiply(v"acc", prop(v"r", "x")),
        allReducePredicate = lessThan(v"acc", literalInt(12)),
        nextAnonymousVariable = v"anon_0"
      )))
      .expand("(os)-[r*1..3]->()")
      .allNodeScan("os")
      .build()
  }

  test("allReduce in WITH clause should fallback to post-filter reduce()") {
    val cfg = plannerBuilder()
      .setAllNodesCardinality(100)
      .setAllRelationshipsCardinality(50)
      .addSemanticFeature(SemanticFeature.AllReduceFunctionAvailable)
      .addSemanticFeature(SemanticFeature.ExperimentalCypherVersions)
      .build()

    val plan = cfg.plan(
      CypherVersion.Cypher25,
      "MATCH (os)((is)-[r]->(ie)){1,3}(oe) WITH allReduce(acc = 1, acc * r.x, acc < 12) AS x RETURN 1"
    ).stripProduceResults

    plan shouldEqual cfg.subPlanBuilder()
      // reduce(acc = {accumulator: 1, result: true}, r IN r | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator * r.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result AS x
      .projection("1 AS 1")
      .projection(Map("x" -> allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(1),
        groupVariable = v"r",
        allReduceStepExpression = multiply(v"acc", prop(v"r", "x")),
        allReducePredicate = lessThan(v"acc", literalInt(12)),
        nextAnonymousVariable = v"anon_0"
      )))
      .expand("(os)-[r*1..3]->()")
      .allNodeScan("os")
      .build()
  }

  test("allReduce in SSP clause should fallback to post-filter reduce()") {
    val cfg = plannerBuilder()
      .setAllNodesCardinality(100)
      .setAllRelationshipsCardinality(50)
      .addSemanticFeature(SemanticFeature.AllReduceFunctionAvailable)
      .addSemanticFeature(SemanticFeature.ExperimentalCypherVersions)
      .build()

    val plan = cfg.plan(
      CypherVersion.Cypher25,
      "MATCH ANY (os)((is)-[r]->(ie)){1,3}(oe) WHERE allReduce(acc = 1, acc * r.x, acc < 12) RETURN 1"
    ).stripProduceResults

    plan shouldEqual cfg.subPlanBuilder()
      // reduce(acc = {accumulator: 1, result: true}, r IN r | CASE
      //   WHEN acc.result = false THEN acc
      //   ELSE [anon_0 IN [acc.accumulator * r.x] | {accumulator: anon_0, result: acc.result AND anon_0 < 12}][0]
      // END).result
      .projection("1 AS 1")
      .filterExpression(allReduceFallBack(
        accumulator = v"acc",
        init = literalInt(1),
        groupVariable = v"r",
        allReduceStepExpression = multiply(v"acc", prop(v"r", "x")),
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
        reverseGroupVariableProjections = false,
        minLength = 1,
        maxLength = Some(3),
        pathMode = Trail
      )
      .allNodeScan("os")
      .build()
  }

  test("nested allReduce in RETURN clause should fallback to post-filter reduce()") {
    val cfg = plannerBuilder()
      .setAllNodesCardinality(100)
      .setAllRelationshipsCardinality(50)
      .addSemanticFeature(SemanticFeature.AllReduceFunctionAvailable)
      .addSemanticFeature(SemanticFeature.ExperimentalCypherVersions)
      .build()

    val plan = cfg.plan(
      CypherVersion.Cypher25,
      """
        |MATCH (a)-[r]->+(b)
        |RETURN allReduce(acc = allReduce(acc = [], acc + r, size(acc) < 5), toInteger(acc) + r.prop, toInteger(acc) <= 5)
        |""".stripMargin
    ).stripProduceResults

    plan shouldEqual cfg.subPlanBuilder()
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
        "allReduce(acc = allReduce(acc = [], acc + r, size(acc) < 5), toInteger(acc) + r.prop, toInteger(acc) <= 5)" ->
          allReduceFallBack(
            accumulator = v"acc",
            init = allReduceFallBack(
              accumulator = v"acc",
              init = ListLiteral(Seq.empty)(pos),
              groupVariable = v"r",
              allReduceStepExpression = add(v"acc", v"r"),
              allReducePredicate = lessThan(size(v"acc"), literalInt(5)),
              nextAnonymousVariable = v"anon_0"
            ),
            groupVariable = v"r",
            allReduceStepExpression = add(function("toInteger", v"acc"), prop(v"r", "prop")),
            allReducePredicate = lessThanOrEqual(function("toInteger", v"acc"), literalInt(5)),
            nextAnonymousVariable = v"anon_1"
          )
      ))
      .expand("(a)-[r*1..]->()")
      .allNodeScan("a")
      .build()
  }
}

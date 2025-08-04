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
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNodeFull
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.logical.plans.IndexOrderDescending
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class DynamicLabelScanPlanningIntegrationTest
    extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport {

  final private val planner =
    plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 10)
      .build()

  test("should plan dynamic label scan with single label") {
    val query =
      """MATCH (a:$('A'))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .dynamicLabelNodeLookup("a", literalString("A"), DynamicElement.All, IndexOrderNone)
        .build()
  }

  test("should plan dynamic label scan with single label in a WHERE clause") {
    val query =
      """MATCH (a)
        |WHERE a:$('A')
        |RETURN a""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .dynamicLabelNodeLookup("a", literalString("A"), DynamicElement.All, IndexOrderNone)
        .build()
  }

  test("probably could but currently does not combine dynamic labels in node pattern and in a WHERE clause") {
    val query =
      """MATCH (a:$('A'))
        |WHERE a:$('B')
        |RETURN a""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(hasDynamicLabels(varFor("a"), literalString("B")))
        .dynamicLabelNodeLookup("a", literalString("A"), DynamicElement.All, IndexOrderNone)
        .build()
  }

  test("should plan dynamic label scan with multiple labels – conjunction") {
    val query =
      """MATCH (a:$(['A', 'B']))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .dynamicLabelNodeLookup("a", listOfString("A", "B"), DynamicElement.All, IndexOrderNone)
        .build()
  }

  test("should plan dynamic label scan with multiple labels – disjunction") {
    val query =
      """MATCH (a:$any(['A', 'B']))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .dynamicLabelNodeLookup("a", listOfString("A", "B"), DynamicElement.Any, IndexOrderNone)
        .build()
  }

  test("should plan dynamic label scan with multiple dynamic labels predicates") {
    val query =
      """MATCH (a:$(['A', 'B']) & $('C'))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(hasDynamicLabels(varFor("a"), literalString("C")))
        .dynamicLabelNodeLookup("a", listOfString("A", "B"), DynamicElement.All, IndexOrderNone)
        .build()
  }

  test("should plan dynamic label scan given a combination of negated conjunction and disjunction") {
    val query =
      """WITH ['A', 'B'] AS labels
        |MATCH (a:!$all(labels) & $any(labels))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(not(hasDynamicLabels(varFor("a"), varFor("labels"))))
        .apply()
        .|.dynamicLabelNodeLookup("a", varFor("labels"), DynamicElement.Any, IndexOrderNone, "labels")
        .projection("['A', 'B'] AS labels")
        .argument()
        .build()
  }

  test("should plan dynamic label scan with complex expression in predicate") {
    val query =
      """MATCH (a:A)
        |WITH labels(a) AS labels
        |MATCH (b:$(labels))
        |RETURN b""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("b")
        .apply()
        .|.dynamicLabelNodeLookup("b", varFor("labels"), DynamicElement.All, IndexOrderNone, "labels")
        .projection("labels(a) AS labels")
        .nodeByLabelScan("a", "A", IndexOrderNone)
        .build()
  }

  test("should plan a union of static and dynamic label scans") {
    val query =
      """MATCH (a:A | $(['B', 'C']))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .distinct("a AS a")
        .union()
        .|.dynamicLabelNodeLookup("a", listOfString("B", "C"), DynamicElement.All, IndexOrderNone)
        .nodeByLabelScan("a", "A", IndexOrderNone)
        .build()
  }

  test("should not plan dynamic label scan for an argument") {
    val query =
      """MATCH (a)
        |WITH a SKIP 0
        |MATCH (a:$('A'))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(hasDynamicLabels(varFor("a"), literalString("A")), assertIsNode("a"))
        .skip(0)
        .allNodeScan("a")
        .build()
  }

  test("should not plan dynamic label scan if cypher_enable_dynamic_label_scan is false") {
    val query =
      """MATCH (a:$('A'))
        |RETURN a""".stripMargin

    val plan = plannerBuilder()
      .withSetting(GraphDatabaseInternalSettings.cypher_enable_dynamic_label_scan, java.lang.Boolean.FALSE)
      .setAllNodesCardinality(100)
      .build()
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(hasDynamicLabels(varFor("a"), literalString("A")))
        .allNodeScan("a")
        .build()
  }

  test("should not plan dynamic label scan if node lookup index is not available") {
    val query =
      """MATCH (a:$('A'))
        |RETURN a""".stripMargin

    val plan = plannerBuilder()
      .setAllNodesCardinality(100)
      .removeNodeLookupIndex()
      .build()
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .filterExpression(hasDynamicLabels(varFor("a"), literalString("A")))
        .allNodeScan("a")
        .build()
  }

  test("should plan dynamic label scan ordered by the variable name") {
    val query =
      """MATCH (a:$('A'))
        |RETURN a ORDER BY a""".stripMargin

    val plan = planner
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .dynamicLabelNodeLookup("a", literalString("A"), DynamicElement.All, IndexOrderAscending)
        .build()
  }

  test("should plan dynamic label scan ordered by the variable name renamed") {
    val query =
      """MATCH (a:$('A'))
        |RETURN a AS b ORDER BY b DESC""".stripMargin

    val plan = planner
      .plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("b")
        .projection("a AS b")
        .dynamicLabelNodeLookup("a", literalString("A"), DynamicElement.All, IndexOrderDescending)
        .build()
  }

  test("should plan dynamic label scan with dynamic labels in MERGE") {
    val query =
      """MERGE (a:$('A'))
        |RETURN a""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("a")
        .merge(Seq(createNodeFull("a", dynamicLabels = Seq("'A'"))))
        .dynamicLabelNodeLookup("a", literalString("A"), DynamicElement.All, IndexOrderNone)
        .build()
  }

  test("should plan dynamic label scan with sub-query label expression") {
    val query =
      """MATCH (n:$(COLLECT { UNWIND ['A', 'B'] AS label RETURN label }))
        |RETURN n""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("n")
        .apply()
        .|.dynamicLabelNodeLookup("n", varFor("anon_0"), DynamicElement.All, IndexOrderNone, "anon_0")
        .rollUpApply("anon_0", "label")
        .|.unwind("['A', 'B'] AS label")
        .|.argument()
        .argument()
        .build()
  }
}

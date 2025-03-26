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
package org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter

import org.neo4j.cypher.internal.compiler.helpers.LogicalPlanBuilder
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningTestSupport
import org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter.RemoveUnusedVariablesRewriterTest.beRewrittenTo
import org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter.RemoveUnusedVariablesRewriterTest.notBeRewritten
import org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter.RemoveUnusedVariablesRewriterTest.thePlan
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.Matcher

class RemoveUnusedVariablesRewriterTest extends CypherFunSuite with LogicalPlanningTestSupport {

  test("remove both start and end node") {
    thePlan(
      _.produceResults("r")
        .allRelationshipsScan("(a)-[r]->(b)")
    ) should beRewrittenTo(
      _.produceResults("r")
        .allRelationshipsScan("()-[r]->()")
    )
  }

  test("remove start node") {
    thePlan(
      _.produceResults("r", "b")
        .allRelationshipsScan("(a)-[r]->(b)")
    ) should beRewrittenTo(
      _.produceResults("r", "b")
        .allRelationshipsScan("()-[r]->(b)")
    )
  }

  test("remove end node") {
    thePlan(
      _.produceResults("r", "a")
        .allRelationshipsScan("(a)-[r]->(b)")
    ) should beRewrittenTo(
      _.produceResults("r", "a")
        .allRelationshipsScan("(a)-[r]->()")
    )
  }

  test("remove nothing") {
    thePlan(
      _.produceResults("r", "a", "b")
        .allRelationshipsScan("(a)-[r]->(b)")
    ) should notBeRewritten
  }
}

object RemoveUnusedVariablesRewriterTest extends CypherFunSuite {

  case class beRewrittenTo(factory: LogicalPlanBuilder => LogicalPlanBuilder) extends Matcher[LogicalPlan] {

    override def apply(left: LogicalPlan): MatchResult = {
      val leftRewritten = rewrite(left)
      val right = thePlan(factory)
      MatchResult(leftRewritten == right, s"$leftRewritten did not equal $right", s"$leftRewritten did equal $right")
    }
  }

  case object notBeRewritten extends Matcher[LogicalPlan] {

    override def apply(left: LogicalPlan): MatchResult = {
      val leftRewritten = rewrite(left)
      MatchResult(left == leftRewritten, s"$left was rewritten to $leftRewritten", s"$left wasn't rewritten")
    }
  }

  def thePlan(factory: LogicalPlanBuilder => LogicalPlanBuilder): LogicalPlan = {
    val builder = new LogicalPlanBuilder()
    factory(builder).build()
  }

  private def rewrite(p: LogicalPlan): LogicalPlan =
    p.endoRewrite(RemoveUnusedVariablesRewriter)
}

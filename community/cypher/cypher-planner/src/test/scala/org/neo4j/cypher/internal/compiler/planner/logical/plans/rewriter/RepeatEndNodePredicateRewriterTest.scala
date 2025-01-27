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
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.TrailParameters
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.WalkParameters
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Repeat.EndNodePredicates
import org.neo4j.cypher.internal.util.UpperBound.Unlimited
import org.neo4j.cypher.internal.util.attribution.Attributes
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class RepeatEndNodePredicateRewriterTest extends CypherFunSuite with LogicalPlanningTestSupport {
  private def subPlanBuilder = new LogicalPlanBuilder(wholePlan = false)

  private val `TRAIL (a) ((n)-[r]-(m))+ (b)`: TrailParameters = TrailParameters(
    min = 1,
    max = Unlimited,
    start = "a",
    end = "b",
    innerStart = "n_i",
    innerEnd = "m_i",
    groupNodes = Set(("n_i", "n"), ("m_i", "m")),
    groupRelationships = Set(("r_i", "r")),
    innerRelationships = Set("r_i"),
    previouslyBoundRelationships = Set.empty,
    previouslyBoundRelationshipGroups = Set.empty,
    reverseGroupVariableProjections = false,
    endNodePredicate = None
  )

  private val `WALK (a) ((n)-[r]-(m))+ (b)`: WalkParameters = WalkParameters(
    min = 1,
    max = Unlimited,
    start = "a",
    end = "b",
    innerStart = "n_i",
    innerEnd = "m_i",
    groupNodes = Set(("n_i", "n"), ("m_i", "m")),
    groupRelationships = Set(("r_i", "r")),
    reverseGroupVariableProjections = false,
    endNodePredicate = None
  )

  test("should not rewrite trail when no filter") {
    val before = subPlanBuilder
      .repeatTrail(`TRAIL (a) ((n)-[r]-(m))+ (b)`)
      .|.filterExpression(isRepeatTrailUnique("r_i"))
      .|.expand("(n_i)-[r_i]->(m_i)")
      .|.argument("n_i")
      .allNodeScan("a")
      .build()

    assertNotRewritten(before)
  }

  test("should not rewrite walk when no filter") {
    val before = subPlanBuilder
      .repeatWalk(`WALK (a) ((n)-[r]-(m))+ (b)`)
      .|.expand("(n_i)-[r_i]->(m_i)")
      .|.argument("n_i")
      .allNodeScan("a")
      .build()

    assertNotRewritten(before)
  }

  test("should push down trail with Into filter") {

    val before = subPlanBuilder
      .filter("b=a")
      .repeatTrail(`TRAIL (a) ((n)-[r]-(m))+ (b)`)
      .|.filterExpression(isRepeatTrailUnique("r_i"))
      .|.expand("(n_i)-[r_i]->(m_i)")
      .|.argument("n_i")
      .allNodeScan("a")
      .build()

    val newEndNodePredicate = EndNodePredicates(
      ands(equals(varFor(`TRAIL (a) ((n)-[r]-(m))+ (b)`.end), varFor("a"))),
      ands(equals(varFor(`TRAIL (a) ((n)-[r]-(m))+ (b)`.innerEnd), varFor("a")))
    )
    val rewrittenTrailParams = `TRAIL (a) ((n)-[r]-(m))+ (b)`.copy(endNodePredicate = Some(newEndNodePredicate))
    val after = subPlanBuilder
      .repeatTrail(rewrittenTrailParams)
      .|.filterExpression(isRepeatTrailUnique("r_i"))
      .|.expand("(n_i)-[r_i]->(m_i)")
      .|.argument("n_i")
      .allNodeScan("a")
      .build()

    rewrite(before) should equal(after)
  }

  test("should push down walk with Into filter") {

    val before = subPlanBuilder
      .filter("b=a")
      .repeatWalk(`WALK (a) ((n)-[r]-(m))+ (b)`)
      .|.expand("(n_i)-[r_i]->(m_i)")
      .|.argument("n_i")
      .allNodeScan("a")
      .build()

    val newEndNodePredicate = EndNodePredicates(
      ands(equals(varFor(`TRAIL (a) ((n)-[r]-(m))+ (b)`.end), varFor("a"))),
      ands(equals(varFor(`TRAIL (a) ((n)-[r]-(m))+ (b)`.innerEnd), varFor("a")))
    )
    val rewrittenWalkParams = `WALK (a) ((n)-[r]-(m))+ (b)`.copy(endNodePredicate = Some(newEndNodePredicate))
    val after = subPlanBuilder
      .repeatWalk(rewrittenWalkParams)
      .|.expand("(n_i)-[r_i]->(m_i)")
      .|.argument("n_i")
      .allNodeScan("a")
      .build()

    rewrite(before) should equal(after)
  }

  private def assertNotRewritten(p: LogicalPlan): Unit = {
    rewrite(p) should equal(p)
  }

  private def rewrite(p: LogicalPlan): LogicalPlan =
    p.endoRewrite(repeatEndNodePredicateRewriter(Attributes(idGen)))
}

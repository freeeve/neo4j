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
package org.neo4j.cypher.internal.compiler.planner.logical.steps

import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext.PlannerState
import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.LabelInfo
import org.neo4j.cypher.internal.compiler.planner.logical.plannerQueryPlanner
import org.neo4j.cypher.internal.ir.ast.ExistsIRExpression
import org.neo4j.cypher.internal.ir.helpers.CachedFunction
import org.neo4j.cypher.internal.logical.plans.CachedProperties
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.util.Ref

trait ExistsSubqueryPlanner {

  def planInnerOfExistsSubquery(
    subquery: ExistsIRExpression,
    labelInfo: LabelInfo,
    context: LogicalPlanningContext
  ): LogicalPlan
}

case object ExistsSubqueryPlanner extends ExistsSubqueryPlanner {

  override def planInnerOfExistsSubquery(
    subquery: ExistsIRExpression,
    labelInfo: LabelInfo,
    context: LogicalPlanningContext
  ): LogicalPlan = {
    val subqueryContext = context.withModifiedPlannerState(_.withFusedLabelInfo(labelInfo))
    plannerQueryPlanner.planSubquery(subquery, subqueryContext)
  }
}

final case class ExistsSubqueryPlannerWithCaching() extends ExistsSubqueryPlanner {

  private val cachedPlanInnerOfExistsSubquery = CachedFunction(doPlan _)

  override def planInnerOfExistsSubquery(
    subquery: ExistsIRExpression,
    labelInfo: LabelInfo,
    context: LogicalPlanningContext
  ): LogicalPlan = {
    cachedPlanInnerOfExistsSubquery(Ref(subquery), labelInfo, computeContextCacheKey(context))
  }

  private def doPlan(
    subqueryRef: Ref[ExistsIRExpression],
    labelInfo: LabelInfo,
    context: CachedFunction.CacheKey[CachedProperties, LogicalPlanningContext]
  ): LogicalPlan = {
    ExistsSubqueryPlanner.planInnerOfExistsSubquery(subqueryRef.value, labelInfo, context.value)
  }

  private def computeContextCacheKey(context: LogicalPlanningContext)
    : CachedFunction.CacheKey[CachedProperties, LogicalPlanningContext] = {
    CachedFunction.CacheKey.computeFrom(context) {
      // Only `previouslyCachedProperties` from LogicalPlanningContext are included in the cache key,
      // as it is currently assumed that other fields do not affect the resulting plan for an EXISTS subquery.
      // In SPD, previously cached properties will help determine if any properties can be reused from the outer query and potentially prevent a costly remoteBatchProperties operation.
      // This avoids unnecessary cache misses and redundant planning.
      // If new fields are added to LogicalPlanningContext that can influence subquery planning,
      // ensure they are also included in the cache key here.
      case LogicalPlanningContext(
          _,
          _,
          PlannerState(_, _, _, _, _, _, _, previouslyCachedProperties)
        ) =>
        previouslyCachedProperties
    }
  }
}

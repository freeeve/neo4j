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
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.planner.logical.QueryPlannerKit
import org.neo4j.cypher.internal.compiler.planner.logical.idp.cartesianProductsOrValueJoins.JoinPredicate
import org.neo4j.cypher.internal.compiler.planner.logical.idp.cartesianProductsOrValueJoins.joinPredicateCandidates
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.logical.plans.LogicalPlan

case object ValueMergeJoinComponentConnector extends ComponentConnector {

  override def solverStep(
    goalBitAllocation: GoalBitAllocation,
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    kit: QueryPlannerKit,
    context: LogicalPlanningContext
  ): ComponentConnectorSolverStep = {
    if (!context.settings.planningMergeJoinEnabled) {
      IDPSolverStep.empty
    } else {
      val predicates = joinPredicateCandidates(queryGraph.selections.flatPredicates)
      if (predicates.isEmpty) {
        IDPSolverStep.empty
      } else {
        new ValueMergeJoinComponentConnectorSolverStep(predicates)
      }
    }
  }
}

class ValueMergeJoinComponentConnectorSolverStep(predicates: Set[JoinPredicate]) extends ComponentConnectorSolverStep {

  override def apply(
    registry: IdRegistry[QueryGraph],
    goal: Goal,
    table: IDPCache[LogicalPlan],
    context: LogicalPlanningContext
  ): Iterator[LogicalPlan] = {
    for {
      predicate <- predicates.iterator.flatMap(p => Iterator(p, p.inverse))
      (leftGoal, rightGoal) <- goal.coveringSplits
      leftPlan <- table(leftGoal).iterator
      if leftPlan.satisfiesExpressionDependencies(predicate.lhs) &&
        !leftPlan.satisfiesExpressionDependencies(predicate.rhs) &&
        orderedByExpression(leftPlan, predicate.lhs, context)
      rightPlan <- table(rightGoal).iterator
      if rightPlan.satisfiesExpressionDependencies(predicate.rhs) &&
        !rightPlan.satisfiesExpressionDependencies(predicate.lhs) &&
        orderedByExpression(rightPlan, predicate.rhs, context)
      if sameOrderDirection(leftPlan, rightPlan, context)
    } yield {
      context.staticComponents.logicalPlanProducer.planMergeJoin(
        leftPlan,
        rightPlan,
        predicate.predicateToPlan,
        predicate.originalPredicate,
        context
      )
    }
  }

  private def orderedByExpression(plan: LogicalPlan, expr: Expression, context: LogicalPlanningContext): Boolean = {
    val order = context.staticComponents.planningAttributes.providedOrders.get(plan.id)
    order.columns.headOption.exists(_.expression == expr)
  }

  private def sameOrderDirection(lhs: LogicalPlan, rhs: LogicalPlan, context: LogicalPlanningContext): Boolean = {
    val left = context.staticComponents.planningAttributes.providedOrders.get(lhs.id)
    val right = context.staticComponents.planningAttributes.providedOrders.get(rhs.id)
    left.columns.head.isAscending == right.columns.head.isAscending
  }
}

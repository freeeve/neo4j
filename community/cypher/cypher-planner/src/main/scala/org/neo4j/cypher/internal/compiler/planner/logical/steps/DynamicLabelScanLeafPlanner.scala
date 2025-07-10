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

import org.neo4j.cypher.internal.compiler.planner.logical.LeafPlanner
import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.ResultOrdering
import org.neo4j.cypher.internal.compiler.planner.logical.steps.DynamicLabelScanLeafPlanner.DynamicLabelExpression
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasAnyDynamicLabel
import org.neo4j.cypher.internal.expressions.HasDynamicLabels
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.LogicalPlan

/**
 * Plans dynamic label scans for nodes with dynamic labels.
 * @param skipIDs IDs of variables that should not be planned as dynamic label scans.
 */
case class DynamicLabelScanLeafPlanner(skipIDs: Set[LogicalVariable]) extends LeafPlanner {

  override def apply(
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Set[LogicalPlan] =
    if (context.settings.dynamicLabelScansEnabled) {
      val logicalPlans = for {
        predicate <- queryGraph.selections.predicates.view
        expression <- extractDynamicLabelExpression(predicate)
        plan <- planDynamicNodeByLabelsScan(
          queryGraph.patternNodes,
          queryGraph.argumentIds,
          interestingOrderConfig,
          context,
          expression
        )
      } yield plan

      logicalPlans.toSet
    } else {
      Set.empty
    }

  final private def extractDynamicLabelExpression(predicate: Predicate): Option[DynamicLabelExpression] =
    Option(predicate.expr).collect {
      case HasDynamicLabels(variable: Variable, Seq(labels)) =>
        DynamicLabelExpression(variable, labels, DynamicElement.All)
      case HasAnyDynamicLabel(variable: Variable, Seq(labels)) =>
        DynamicLabelExpression(variable, labels, DynamicElement.Any)
    }

  final private def planDynamicNodeByLabelsScan(
    patternNodes: Set[LogicalVariable],
    argumentIds: Set[LogicalVariable],
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext,
    expression: DynamicLabelExpression
  ): Option[LogicalPlan] =
    if (
      !patternNodes.contains(expression.variable) ||
      skipIDs.contains(expression.variable) ||
      argumentIds.contains(expression.variable)
    ) {
      None
    } else {
      context.staticComponents.planContext.nodeTokenIndex.map { nodeTokenIndex =>
        val providedOrder = ResultOrdering.providedOrderForLabelScan(
          interestingOrder = interestingOrderConfig.orderToSolve,
          variable = expression.variable,
          indexOrderCapability = nodeTokenIndex.orderCapability,
          providedOrderFactory = context.providedOrderFactory
        )
        context.staticComponents.logicalPlanProducer.planDynamicNodeByLabelsScan(
          variable = expression.variable,
          labels = expression.labels,
          operator = expression.operator,
          argumentIds = argumentIds,
          providedOrder = providedOrder,
          context = context
        )
      }
    }
}

object DynamicLabelScanLeafPlanner {

  final private case class DynamicLabelExpression(
    variable: Variable,
    labels: Expression,
    operator: DynamicElement.SetOperator
  )
}

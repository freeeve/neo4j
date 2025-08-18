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
import org.neo4j.cypher.internal.compiler.planner.logical.steps.DynamicLabelLookupLeafPlanner.DynamicLabelExpression
import org.neo4j.cypher.internal.compiler.planner.logical.steps.DynamicLabelLookupLeafPlanner.PropertyPredicatesHelper
import org.neo4j.cypher.internal.compiler.planner.logical.steps.index.EntityIndexLeafPlanner.IndexCompatiblePredicate
import org.neo4j.cypher.internal.compiler.planner.logical.steps.index.EntityIndexLeafPlanner.SingleExactPredicate
import org.neo4j.cypher.internal.compiler.planner.logical.steps.index.IndexCompatiblePredicatesProvider
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasAnyDynamicLabel
import org.neo4j.cypher.internal.expressions.HasDynamicLabels
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.PropertyKeyToken
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.SingleQueryExpression

/**
 * Plans dynamic label scans for nodes with dynamic labels.
 * @param skipIDs IDs of variables that should not be planned as dynamic label scans.
 */
case class DynamicLabelLookupLeafPlanner(skipIDs: Set[LogicalVariable]) extends LeafPlanner {

  override def apply(
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Set[LogicalPlan] =
    if (context.settings.dynamicLabelScansEnabled) {
      val propertyPredicatesHelper = new PropertyPredicatesHelper(queryGraph, context)
      val logicalPlans = for {
        predicate <- queryGraph.selections.predicates.view
        expression <- extractDynamicLabelExpression(predicate)
        plan <- planDynamicLabelNodeLookup(
          queryGraph.patternNodes,
          queryGraph.argumentIds,
          context,
          expression,
          propertyPredicatesHelper
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

  final private def planDynamicLabelNodeLookup(
    patternNodes: Set[LogicalVariable],
    argumentIds: Set[LogicalVariable],
    context: LogicalPlanningContext,
    expression: DynamicLabelExpression,
    propertiesHelper: PropertyPredicatesHelper
  ): Option[LogicalPlan] =
    if (
      !patternNodes.contains(expression.variable) ||
      skipIDs.contains(expression.variable) ||
      argumentIds.contains(expression.variable) ||
      context.staticComponents.planContext.nodeTokenIndex.isEmpty
    ) {
      None
    } else {
      val propertyPredicates = propertiesHelper.predicatesForVariable(expression.variable)

      Some(context.staticComponents.logicalPlanProducer.planDynamicLabelNodeLookup(
        variable = expression.variable,
        labels = expression.labels,
        operator = expression.operator,
        argumentIds = argumentIds,
        context = context,
        solvedPropertyPredicates = propertyPredicates.values.map(_.solvedPredicate).toSet,
        propertyPredicates = propertyPredicates.view.mapValues(_.indexSeekArgument).toMap
      ))
    }
}

object DynamicLabelLookupLeafPlanner {

  final private case class DynamicLabelExpression(
    variable: Variable,
    labels: Expression,
    operator: DynamicElement.SetOperator
  )

  private case class PropertyPredicate(solvedPredicate: Expression, indexSeekArgument: Expression)

  private class PropertyPredicatesHelper(queryGraph: QueryGraph, context: LogicalPlanningContext) {

    def predicatesForVariable(v: LogicalVariable): Map[PropertyKeyToken, PropertyPredicate] = {
      if (context.settings.dynamicLabelIndexUseEnabled)
        predicates.getOrElse(v, Map.empty)
      else
        Map.empty
    }

    private lazy val predicates: Map[LogicalVariable, Map[PropertyKeyToken, PropertyPredicate]] = {
      IndexCompatiblePredicatesProvider.findExplicitCompatiblePredicates(
        queryGraph.argumentIds,
        queryGraph.selections.flatPredicatesSet,
        context.semanticTable
      ).groupBy(_.variable).view.mapValues(icps =>
        icps.collect {
          case IndexCompatiblePredicate(
              _,
              property,
              originalPredicate,
              SingleQueryExpression(indexSeekArgument),
              SingleExactPredicate,
              solvedPredicate,
              _,
              isImplicit,
              _,
              _
            )
            if !isImplicit && context.semanticTable.id(property.propertyKey).isDefined =>

            val key = PropertyKeyToken(property.propertyKey, context.semanticTable.id(property.propertyKey).get)
            val value = PropertyPredicate(
              solvedPredicate = solvedPredicate.getOrElse(originalPredicate),
              indexSeekArgument = indexSeekArgument
            )
            key -> value
        }.groupMapReduce(_._1)(_._2)((v1, v2) => Seq(v1, v2).minBy(_.solvedPredicate.position))
      ).filter(_._2.nonEmpty).toMap
    }
  }
}

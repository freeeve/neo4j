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
import org.neo4j.cypher.internal.compiler.planner.logical.steps.DynamicRelationshipTypeLookupLeafPlanner.DynamicRelationshipTypeLookupDetails
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasAnyDynamicType
import org.neo4j.cypher.internal.expressions.HasDynamicType
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.ordering.ProvidedOrder

/**
 * Plans dynamic relationship type scans for relationships with dynamic labels.
 * @param skipIDs IDs of variables that should not be planned using dynamic relationship type scans.
 */
case class DynamicRelationshipTypeLookupLeafPlanner(skipIDs: Set[LogicalVariable]) extends LeafPlanner {

  override def apply(
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Set[LogicalPlan] =
    DynamicRelationshipTypeLookupLeafPlanner
      .collectDynamicRelationshipTypeLookupDetails(skipIDs, context, queryGraph, interestingOrderConfig)
      .map(planDynamicRelationshipByTypeLookup(context, _))
      .toSet

  final private def planDynamicRelationshipByTypeLookup(
    context: LogicalPlanningContext,
    details: DynamicRelationshipTypeLookupDetails
  ): LogicalPlan =
    RelationshipLeafPlanner.planHiddenSelectionAndRelationshipLeafPlan(
      argumentIds = details.argumentIds,
      relationship = details.relationship,
      context = context,
      relationshipLeafPlanProvider = (patternForLeafPlan, originalPattern, hiddenSelections) =>
        context.staticComponents.logicalPlanProducer.planDynamicRelationshipByTypeLookup(
          variable = details.relationship.variable,
          relationshipTypes = details.relationshipTypes,
          operator = details.operator,
          patternForLeafPlan = patternForLeafPlan,
          originalPattern = originalPattern,
          hiddenSelections = hiddenSelections,
          argumentIds = details.argumentIds,
          providedOrder = details.providedOrder,
          context = context
        )
    )
}

object DynamicRelationshipTypeLookupLeafPlanner {

  final case class DynamicRelationshipTypeLookupDetails(
    argumentIds: Set[LogicalVariable],
    relationship: PatternRelationship,
    relationshipTypes: Expression,
    operator: DynamicElement.SetOperator,
    providedOrder: ProvidedOrder
  )

  def collectDynamicRelationshipTypeLookupDetails(
    skipIDs: Set[LogicalVariable],
    context: LogicalPlanningContext,
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig
  ): LazyList[DynamicRelationshipTypeLookupDetails] =
    if (context.settings.dynamicLabelScansEnabled) {
      for {
        predicate <- queryGraph.selections.predicates.to(LazyList)
        expression <- extractDynamicRelationshipTypeExpression(predicate)
        plan <- extractDynamicRelationshipTypeLookupDetails(
          skipIDs,
          context,
          queryGraph.patternRelationships,
          queryGraph.argumentIds,
          interestingOrderConfig,
          expression
        )
      } yield plan
    } else {
      LazyList.empty
    }

  final private case class DynamicRelationshipTypeExpression(
    variable: Variable,
    relationshipTypes: Expression,
    operator: DynamicElement.SetOperator
  )

  final private def extractDynamicRelationshipTypeExpression(predicate: Predicate)
    : Option[DynamicRelationshipTypeExpression] =
    Option(predicate.expr).collect {
      case HasDynamicType(variable: Variable, Seq(relationshipTypes)) =>
        DynamicRelationshipTypeExpression(variable, relationshipTypes, DynamicElement.All)
      case HasAnyDynamicType(variable: Variable, Seq(relationshipTypes)) =>
        DynamicRelationshipTypeExpression(variable, relationshipTypes, DynamicElement.Any)
    }

  final private def extractDynamicRelationshipTypeLookupDetails(
    skipIDs: Set[LogicalVariable],
    context: LogicalPlanningContext,
    patternRelationships: Set[PatternRelationship],
    argumentIds: Set[LogicalVariable],
    interestingOrderConfig: InterestingOrderConfig,
    expression: DynamicRelationshipTypeExpression
  ): Option[DynamicRelationshipTypeLookupDetails] =
    for {
      relationship <- patternRelationships.find(_.variable == expression.variable)
      if skipIDs.intersect(Set(relationship.variable, relationship.left, relationship.right)).isEmpty &&
        !argumentIds.contains(relationship.variable)
      relationshipTokenIndex <- context.staticComponents.planContext.relationshipTokenIndex
      providedOrder = ResultOrdering.providedOrderForLabelScan(
        interestingOrder = interestingOrderConfig.orderToSolve,
        variable = expression.variable,
        indexOrderCapability = relationshipTokenIndex.orderCapability,
        providedOrderFactory = context.providedOrderFactory
      )
    } yield DynamicRelationshipTypeLookupDetails(
      argumentIds = argumentIds,
      relationship = relationship,
      relationshipTypes = expression.relationshipTypes,
      operator = expression.operator,
      providedOrder = providedOrder
    )
}

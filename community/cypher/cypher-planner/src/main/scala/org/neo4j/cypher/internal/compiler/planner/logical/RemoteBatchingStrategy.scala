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

import org.neo4j.configuration.GraphDatabaseInternalSettings.RemoteBatchPropertiesImplementation
import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper
import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper.ContextualPropertyAccess
import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper.PropertyAccess
import org.neo4j.cypher.internal.compiler.helpers.predicatesPushedDownToRemote
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.compiler.planner.logical.idp.ExtraRequirement
import org.neo4j.cypher.internal.compiler.planner.logical.steps.index.EntityIndexLeafPlanner.IndexCompatiblePredicate
import org.neo4j.cypher.internal.compiler.planner.logical.steps.index.IndexCompatiblePredicateWithValueBehavior
import org.neo4j.cypher.internal.expressions.CachedProperty
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RELATIONSHIP_TYPE
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.ast.IRExpression
import org.neo4j.cypher.internal.logical.plans.CachedProperties
import org.neo4j.cypher.internal.logical.plans.CanGetValue
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.NestedPlanExpression
import org.neo4j.cypher.internal.logical.plans.RewrittenExpressions
import org.neo4j.cypher.internal.planner.spi.DatabaseMode.SHARDED
import org.neo4j.cypher.internal.planner.spi.IndexDescriptor
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTRelationship
import org.neo4j.cypher.internal.util.topDown

sealed trait RemoteBatchingStrategy {

  def getValueFromIndexBehaviors(
    indexDescriptor: IndexDescriptor,
    propertyPredicates: Seq[IndexCompatiblePredicate],
    exactPredicatesCanGetValue: Boolean,
    context: LogicalPlanningContext,
    queryGraph: QueryGraph
  ): Seq[IndexCompatiblePredicateWithValueBehavior]

  def planBatchPropertiesForSelections(
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    predicatesToSolve: Set[Expression]
  ): RemoteBatchingResult

  def planRemoteBatchProperties(
    inputPlan: LogicalPlan,
    context: LogicalPlanningContext,
    expressions: Iterable[Expression]
  ): (RewrittenExpressions, LogicalPlan)

  def planBatchPropertiesForProjections(
    input: LogicalPlan,
    context: LogicalPlanningContext,
    projections: Map[LogicalVariable, Expression],
    orderToLeverage: Seq[Expression] = Seq.empty
  ): RemoteBatchingResult

  def planBatchPropertiesForExpressionsWithLookahead(
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    expressions: Iterable[Expression]
  ): (RewrittenExpressions, LogicalPlan)

  def planBatchPropertiesForExpressionWithLookahead(
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    expression: Expression
  ): (Expression, LogicalPlan)

  def interestingPropertiesAsIDPExtraRequirement(
    queryGraph: QueryGraph,
    context: LogicalPlanningContext
  ): ExtraRequirement[LogicalPlan]

  def planPrefetchRemoteBatchPropertiesIfRequired(
    queryGraph: QueryGraph,
    plans: Iterable[LogicalPlan],
    context: LogicalPlanningContext
  ): Iterable[LogicalPlan]
}

case class RemoteBatchingResult(
  rewrittenExpressionsWithCachedProperties: CachePropertiesRewritableExpressions,
  plan: LogicalPlan
)

case class CachePropertiesRewritableExpressions(
  selections: Set[Expression] = Set.empty,
  projections: Map[LogicalVariable, Expression] = Map.empty,
  orderToLeverage: Seq[Expression] = Seq.empty
)

object RemoteBatchingStrategy {

  def fromConfig(query: PlannerQuery, context: PlannerContext): RemoteBatchingStrategy = {
    if (
      query.readOnly
      && context.planContext.databaseMode == SHARDED
      && context.config.remoteBatchPropertiesImplementation() == RemoteBatchPropertiesImplementation.PLANNER
    )
      InPlannerRemoteBatching
    else
      SkipRemoteBatching
  }

  def defaultValue(): RemoteBatchingStrategy = SkipRemoteBatching

  case object InPlannerRemoteBatching extends RemoteBatchingStrategy {

    override def planBatchPropertiesForSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression]
    ): RemoteBatchingResult = {
      val accessedProperties = accessedPropertiesForPredicates(queryGraph, input, context) ++
        context.plannerState.contextualPropertyAccess.interestingOrder ++
        context.plannerState.contextualPropertyAccess.horizon ++
        context.plannerState.contextualPropertyAccess.propertyAccessInOtherComponents

      val rewriter = cachedPropertiesRewriter(input, context)
      val rewrittenSelections = predicatesToSolve.map(expr => expr -> expr.endoRewrite(rewriter))
      val planWithRemainingFiltersTuple =
        planBatchPropertiesWithFilters(input, context, accessedProperties, rewrittenSelections)
      RemoteBatchingResult(
        rewrittenExpressionsWithCachedProperties =
          CachePropertiesRewritableExpressions(selections = planWithRemainingFiltersTuple._2.toSet),
        plan = planWithRemainingFiltersTuple._1
      )
    }

    override def planRemoteBatchProperties(
      inputPlan: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): (RewrittenExpressions, LogicalPlan) = {
      val accessedProperties = PropertyAccessHelper.findPropertyAccesses(expressions)
      val rewriter = cachedPropertiesRewriter(inputPlan, context)
      val rewrittenExpressions = RewrittenExpressions(expressions.map(expr => expr -> expr.endoRewrite(rewriter)).toMap)
      (
        rewrittenExpressions,
        planBatchProperties(inputPlan, context, accessedProperties, rewrittenExpressions.allRewrittenExpressions.toSeq)
      )
    }

    override def planBatchPropertiesForProjections(
      input: LogicalPlan,
      context: LogicalPlanningContext,
      projections: Map[LogicalVariable, Expression],
      orderToLeverage: Seq[Expression]
    ): RemoteBatchingResult = {
      val accessedProperties = PropertyAccessHelper.findPropertyAccesses(projections.values.toSeq ++ orderToLeverage)
      val rewriter = cachedPropertiesRewriter(input, context)
      val rewrittenProjections = projections.map {
        case (v, e) => v -> e.endoRewrite(rewriter)
      }
      val rewrittenOrderToLeverage = orderToLeverage.map(_.endoRewrite(rewriter))
      RemoteBatchingResult(
        rewrittenExpressionsWithCachedProperties =
          CachePropertiesRewritableExpressions(
            projections = rewrittenProjections,
            orderToLeverage = rewrittenOrderToLeverage
          ),
        plan = planBatchProperties(
          input,
          context,
          accessedProperties,
          (rewrittenProjections.values ++ rewrittenOrderToLeverage).toSeq
        )
      )
    }

    override def planBatchPropertiesForExpressionsWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): (RewrittenExpressions, LogicalPlan) = {
      val accessedProperties = remainingPropertyAccesses(queryGraph, input, context)

      val rewriter = cachedPropertiesRewriter(input, context)
      val rewrittenExpressions = RewrittenExpressions(expressions.map(expr => expr -> expr.endoRewrite(rewriter)).toMap)
      (
        rewrittenExpressions,
        planBatchProperties(input, context, accessedProperties, rewrittenExpressions.allRewrittenExpressions.toSeq)
      )
    }

    override def planBatchPropertiesForExpressionWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      expression: Expression
    ): (Expression, LogicalPlan) = {
      val accessedProperties = remainingPropertyAccesses(queryGraph, input, context)

      val rewriter = cachedPropertiesRewriter(input, context)
      val rewrittenExpr = expression.endoRewrite(rewriter)
      (rewrittenExpr, planBatchProperties(input, context, accessedProperties, Seq(rewrittenExpr)))
    }

    private def remainingPropertyAccesses(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext
    ) = {
      accessedPropertiesForPredicates(queryGraph, input, context) ++
        context.plannerState.contextualPropertyAccess.interestingOrder ++
        context.plannerState.contextualPropertyAccess.horizon ++
        context.plannerState.contextualPropertyAccess.propertyAccessInOtherComponents
    }

    override def interestingPropertiesAsIDPExtraRequirement(
      queryGraph: QueryGraph,
      context: LogicalPlanningContext
    ): ExtraRequirement[LogicalPlan] = {
      new ExtraRequirement[LogicalPlan]() {
        override def fulfils(plan: LogicalPlan): Boolean = {
          val availableSymbols = plan.availableSymbols
          val cachedProperties = context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(plan.id)
          val interestingPropertyAccesses = remainingPropertyAccesses(
            queryGraph,
            plan,
            context
          ).filter(propertyAccess => availableSymbols.contains(propertyAccess.variable))
          if (interestingPropertyAccesses.nonEmpty)
            interestingPropertyAccesses.forall(propertyAccess =>
              cachedProperties.contains(
                propertyAccess.variable,
                PropertyKeyName(propertyAccess.propertyName)(InputPosition.NONE)
              )
            )
          else
            false // we will force this to false since it makes IDP cheaper and let sorted plans take precedence.
        }
      }
    }

    private def shouldGetPropertyValue(
      propertyPredicate: IndexCompatiblePredicate,
      propsAccessForPredsMap: PropertyAccessInPredicates,
      contextualPropertyAccess: ContextualPropertyAccess
    ): Boolean = {
      val allHorizonAcceses = contextualPropertyAccess.horizon ++ contextualPropertyAccess.interestingOrder
      val propertyAccess = PropertyAccess(
        propertyPredicate.variable,
        propertyPredicate.propertyKeyName.name
      )

      propsAccessForPredsMap
        .propertyAccessInPredicatesOtherThat(propertyAccess, propertyPredicate.predicate) ||
      allHorizonAcceses.contains(propertyAccess) ||
      contextualPropertyAccess.propertyAccessInOtherComponents.contains(propertyAccess)
    }

    override def planPrefetchRemoteBatchPropertiesIfRequired(
      queryGraph: QueryGraph,
      plans: Iterable[LogicalPlan],
      context: LogicalPlanningContext
    ): Iterable[LogicalPlan] = {
      val extraRequirement = interestingPropertiesAsIDPExtraRequirement(queryGraph, context)
      plans.flatMap { plan =>
        if (extraRequirement.fulfils(plan)) Iterator(plan)
        else planRemoteBatchPropertiesWithLookahead(queryGraph, plan, context)
      }.toVector
    }

    private def planRemoteBatchPropertiesWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext
    ): Iterator[LogicalPlan] = {
      val accessedProperties = remainingPropertyAccesses(queryGraph, input, context)
      val alreadyCachedProperties =
        context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(input.id)
      val availableSymbols = input.availableSymbols
      val cachedPropertiesForHeadPlan =
        cachedPropertiesFromPropAccesses(accessedProperties, alreadyCachedProperties, availableSymbols, context)

      if (cachedPropertiesForHeadPlan.nonEmpty)
        Iterator(context.staticComponents.logicalPlanProducer.planRemoteBatchProperties(
          input,
          cachedPropertiesForHeadPlan,
          context
        ))
      else Iterator.empty
    }

    private def cachedPropertiesFromPropAccesses(
      accessedProperties: Set[PropertyAccess],
      alreadyCachedProperties: CachedProperties,
      availableSymbols: Set[LogicalVariable],
      context: LogicalPlanningContext
    ): Set[CachedProperty] =
      accessedProperties.collect {
        case PropertyAccess(variable, propertyName)
          if availableSymbols.contains(variable) && !alreadyCachedProperties.contains(
            variable,
            PropertyKeyName(propertyName)(InputPosition.NONE)
          ) =>
          toCachedProperty(
            context,
            alreadyCachedProperties,
            variable,
            PropertyKeyName(propertyName)(InputPosition.NONE),
            InputPosition.NONE,
            knownToCacheStore = true
          )
      }.flatten

    override def getValueFromIndexBehaviors(
      indexDescriptor: IndexDescriptor,
      propertyPredicates: Seq[IndexCompatiblePredicate],
      exactPredicatesCanGetValue: Boolean,
      context: LogicalPlanningContext,
      queryGraph: QueryGraph
    ): Seq[IndexCompatiblePredicateWithValueBehavior] = {
      val propsAccessForPredsMap = propertyAccessesToPredicatesMap(queryGraph.selections.flatPredicatesSet)
      val contextualPropertyAccess = context.plannerState.contextualPropertyAccess
      val rewriter = externalPropertyAccessesRewriter(context)

      def determineGetValueBehaviour(predicate: IndexCompatiblePredicate) = {
        if (shouldGetPropertyValue(predicate, propsAccessForPredsMap, contextualPropertyAccess))
          IndexCompatiblePredicateWithValueBehavior(predicate, GetValue)
        else IndexCompatiblePredicateWithValueBehavior(predicate, DoNotGetValue)
      }

      propertyPredicates.map { predicate =>
        val rewrittenQueryExpression = predicate.queryExpression.endoRewrite(rewriter)
        predicate.copy(queryExpression = rewrittenQueryExpression) match {
          case predicateWithRewrittenExpr
            if predicateWithRewrittenExpr.predicateExactness.isExact && exactPredicatesCanGetValue =>
            determineGetValueBehaviour(predicateWithRewrittenExpr)
          case predicateWithRewrittenExpr =>
            indexDescriptor.valueCapability match {
              case DoNotGetValue =>
                IndexCompatiblePredicateWithValueBehavior(predicateWithRewrittenExpr, DoNotGetValue)
              case _ =>
                determineGetValueBehaviour(predicateWithRewrittenExpr)
            }
        }
      }
    }

    private def externalPropertyAccessesRewriter(context: LogicalPlanningContext) = {
      bottomUp.apply(
        rewriter = Rewriter.lift {
          case property @ Property(logicalVariable: LogicalVariable, propertyKeyName)
            if context.plannerState.previouslyCachedProperties.contains(logicalVariable, propertyKeyName) =>
            val entry = context.plannerState.previouslyCachedProperties.entries(logicalVariable)
            CachedProperty(
              entry.originalEntity,
              logicalVariable,
              propertyKeyName,
              entry.entityType
            )(
              property.position
            )
        },
        cancellation = context.staticComponents.cancellationChecker
      )
    }

    private def accessedPropertiesForPredicates(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext
    ): Set[PropertyAccess] = {
      val previouslySolvedPredicates = context.staticComponents
        .planningAttributes.solveds
        .get(input.id)
        .asSinglePlannerQuery.queryGraph
        .selections
        .flatPredicatesSet

      // we compute not only the predicates that will be solved by this selection
      val predicatesToBeSolvedLater =
        queryGraph.selections.flatPredicatesSet
          .diff(previouslySolvedPredicates)
          .filter(expr => expr.dependencies.intersect(input.availableSymbols).nonEmpty)
      PropertyAccessHelper.findPropertyAccesses(predicatesToBeSolvedLater.toSeq)
    }

    private def cachedPropertiesRewriter(
      inputPlan: LogicalPlan,
      context: LogicalPlanningContext
    ) = {
      val alreadyCachedProperties =
        context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(inputPlan.id)
      topDown.apply(
        rewriter = Rewriter.lift {
          case property @ Property(logicalVariable: LogicalVariable, propertyKeyName)
            if inputPlan.availableSymbols.contains(logicalVariable) =>
            toCachedProperty(
              context,
              alreadyCachedProperties,
              logicalVariable,
              propertyKeyName,
              property.position
            ).getOrElse(property)
        },
        stopper = {
          case _: IRExpression => true // This gets planned in the SubQueryExpressionSolvers instead.
          case _               => false
        },
        cancellation = context.staticComponents.cancellationChecker
      )
    }

    private def toCachedProperty(
      context: LogicalPlanningContext,
      alreadyCachedProperties: CachedProperties,
      logicalVariable: LogicalVariable,
      propertyKeyName: PropertyKeyName,
      position: InputPosition,
      knownToCacheStore: Boolean = false
    ): Option[CachedProperty] = {
      alreadyCachedProperties.entries.get(logicalVariable) match {
        case Some(entry) =>
          Some(CachedProperty(
            entry.originalEntity,
            logicalVariable,
            propertyKeyName,
            entry.entityType,
            knownToCacheStore
          )(
            position
          ))
        case None =>
          val entityType = context.semanticTable.typeFor(logicalVariable)
          if (entityType.is(CTNode))
            Some(
              CachedProperty(logicalVariable, logicalVariable, propertyKeyName, NODE_TYPE, knownToCacheStore)(position)
            )
          else if (entityType.is(CTRelationship))
            Some(CachedProperty(
              logicalVariable,
              logicalVariable,
              propertyKeyName,
              RELATIONSHIP_TYPE,
              knownToCacheStore
            )(position))
          else
            None
      }
    }

    private def planBatchProperties(
      input: LogicalPlan,
      context: LogicalPlanningContext,
      accessedProperties: Set[PropertyAccess],
      expressions: Seq[Expression]
    ): LogicalPlan = {
      val props = propertiesToFetch(input, context, accessedProperties, expressions)
      if (props.nonEmpty)
        context.staticComponents.logicalPlanProducer.planRemoteBatchProperties(input, props, context)
      else input
    }

    private def planBatchPropertiesWithFilters(
      input: LogicalPlan,
      context: LogicalPlanningContext,
      accessedProperties: Set[PropertyAccess],
      expressions: Set[(Expression, Expression)]
    ): (LogicalPlan, Seq[Expression]) = {
      val (inlinablePreds, nonInlinablePreds) = expressions
        .partition(exprsToRewrittenExprs => predicatesPushedDownToRemote(exprsToRewrittenExprs._2))
      val allRewrittenPreds = expressions.map(_._2).toSeq
      if (inlinablePreds.nonEmpty && context.settings.cachePropertiesForEntitiesWithFilter) {
        val rewrittenExprs = inlinablePreds.map(_._2).toSeq
        val solvedExprs = inlinablePreds.map(_._1)
        val props =
          propertiesToFetch(
            input,
            context,
            accessedProperties,
            allRewrittenPreds
          ) // we still fetch properties even if the corresponding expression isn't pushed down to remote batch properties.

        if (props.nonEmpty) {
          // we separate the predicates that access properties from the ones that don't so we can push down the later
          val (propAccessPreds, predsToPushDown) =
            nonInlinablePreds.map(_._2).partition(_.folder.treeExists(_.isInstanceOf[CachedProperty]))
          (
            context.staticComponents.logicalPlanProducer.planRemoteBatchPropertiesWithFilter(
              input,
              props,
              context,
              rewrittenExprs,
              solvedExprs.toSeq,
              predsToPushDown.toSeq
            ),
            propAccessPreds.toSeq
          )
        } else
          (
            planBatchProperties(input, context, accessedProperties, allRewrittenPreds),
            allRewrittenPreds
          )
      } else
        (
          planBatchProperties(input, context, accessedProperties, allRewrittenPreds),
          allRewrittenPreds
        )
    }

    private def propertiesToFetch(
      input: LogicalPlan,
      context: LogicalPlanningContext,
      accessedProperties: Set[PropertyAccess],
      expressions: Seq[Expression]
    ): Set[CachedProperty] = {
      val accessedPropertiesMap = accessedProperties.map {
        accessedProperty =>
          accessedProperty.variable -> PropertyKeyName(accessedProperty.propertyName)(InputPosition.NONE)
      }.groupMap(_._1)(_._2)

      val alreadyCachedProperties =
        context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(input.id)

      expressions.folder.treeFold(Set[CachedProperty]()) {
        case _: NestedPlanExpression => set => SkipChildren(set)
        case cachedProperty: CachedProperty
          if !alreadyCachedProperties.contains(cachedProperty.entityVariable, cachedProperty.propertyKey) =>
          set =>
            val props = accessedPropertiesMap
              .getOrElse(cachedProperty.entityVariable, Set.empty)
              .incl(cachedProperty.propertyKey)
            val newProps = alreadyCachedProperties.propertiesNotYetCached(cachedProperty.entityVariable, props)
              .map { propertyKeyName =>
                cachedProperty.copy(propertyKey = propertyKeyName, knownToAccessStore = true)(InputPosition.NONE)
              }
            SkipChildren(set ++ newProps)
      }
    }

    private def propertyAccessesToPredicatesMap(predicates: Set[Expression]): PropertyAccessInPredicates = {
      val propertyAccessToPredicatesMap = predicates.flatMap {
        predicate =>
          PropertyAccessHelper
            .findPropertyAccesses(Seq(predicate))
            .map((_, predicate))
      }.groupMap(_._1)(_._2)

      PropertyAccessInPredicates(propertyAccessToPredicatesMap)
    }

    private case class PropertyAccessInPredicates(backingMap: Map[PropertyAccess, Set[Expression]]) extends AnyVal {

      def propertyAccessInPredicatesOtherThat(propertyAccess: PropertyAccess, inputPredicate: Expression): Boolean =
        backingMap.get(propertyAccess).exists(_.exists(expr => expr != inputPredicate))
    }
  }

  private case object SkipRemoteBatching extends RemoteBatchingStrategy {

    override def getValueFromIndexBehaviors(
      indexDescriptor: IndexDescriptor,
      propertyPredicates: Seq[IndexCompatiblePredicate],
      exactPredicatesCanGetValue: Boolean,
      context: LogicalPlanningContext,
      queryGraph: QueryGraph
    ): Seq[IndexCompatiblePredicateWithValueBehavior] = propertyPredicates.map {
      case predicate if predicate.predicateExactness.isExact && exactPredicatesCanGetValue =>
        IndexCompatiblePredicateWithValueBehavior(predicate, CanGetValue)
      case predicate => IndexCompatiblePredicateWithValueBehavior(predicate, indexDescriptor.valueCapability)
    }

    override def planBatchPropertiesForSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression]
    ): RemoteBatchingResult =
      RemoteBatchingResult(CachePropertiesRewritableExpressions(selections = predicatesToSolve), input)

    override def planBatchPropertiesForProjections(
      input: LogicalPlan,
      context: LogicalPlanningContext,
      projections: Map[LogicalVariable, Expression],
      orderToLeverage: Seq[Expression]
    ): RemoteBatchingResult = RemoteBatchingResult(
      CachePropertiesRewritableExpressions(projections = projections, orderToLeverage = orderToLeverage),
      plan = input
    )

    override def planBatchPropertiesForExpressionsWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): (RewrittenExpressions, LogicalPlan) = (RewrittenExpressions.withNoRewrittenExprs(expressions), input)

    override def planBatchPropertiesForExpressionWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      expression: Expression
    ): (Expression, LogicalPlan) = (expression, input)

    override def planRemoteBatchProperties(
      inputPlan: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): (RewrittenExpressions, LogicalPlan) =
      (RewrittenExpressions.withNoRewrittenExprs(expressions), inputPlan)

    override def interestingPropertiesAsIDPExtraRequirement(
      queryGraph: QueryGraph,
      context: LogicalPlanningContext
    ): ExtraRequirement[LogicalPlan] = ExtraRequirement.empty

    override def planPrefetchRemoteBatchPropertiesIfRequired(
      queryGraph: QueryGraph,
      plans: Iterable[LogicalPlan],
      context: LogicalPlanningContext
    ): Iterable[LogicalPlan] = Iterable.empty
  }
}

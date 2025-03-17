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
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.compiler.planner.logical.idp.ExtraRequirement
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
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

  def planBatchPropertiesForHorizonSelections(
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    predicatesToSolve: Set[Expression],
    interestingOrderConfig: InterestingOrderConfig
  ): RemoteBatchingResult

  def planRemoteBatchProperties(
    inputPlan: LogicalPlan,
    context: LogicalPlanningContext,
    expressions: Iterable[Expression]
  ): RemoteBatchingResult

  def planBatchPropertiesForExpressionsWithLookahead(
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    expressions: Iterable[Expression]
  ): RemoteBatchingResult

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
  rewrittenExpressionsWithCachedProperties: RewrittenExpressions,
  plan: LogicalPlan
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
      def planPreBatchSelection(selectionOnMain: Option[SelectionOnMain]) = (plan: LogicalPlan) =>
        selectionOnMain match {
          case Some(SelectionOnMain(predicates)) =>
            context.staticComponents.logicalPlanProducer.planSelectionWithSolvedPredicates(
              plan,
              predicates,
              context
            )
          case None => plan
        }

      def planRemoteBatchPropertiesWithFilter(selectionOnShard: Option[SelectionOnShard]) = (plan: LogicalPlan) =>
        selectionOnShard match {
          case Some(SelectionOnShard(propertiesToFetch, predicatesToExecute)) =>
            context.staticComponents.logicalPlanProducer.planRemoteBatchPropertiesWithFilter(
              plan,
              propertiesToFetch,
              context,
              predicatesToExecute.toSeq
            )
          case None => plan
        }

      def planRemoteBatchProperties(fetchPropertiesOnly: Option[FetchPropertiesOnly]) = (plan: LogicalPlan) =>
        fetchPropertiesOnly match {
          case Some(FetchPropertiesOnly(propertiesToFetch)) =>
            context.staticComponents.logicalPlanProducer.planRemoteBatchProperties(
              plan,
              propertiesToFetch,
              context
            )
          case None => plan
        }

      operatorSequenceForSelections(queryGraph, input, context, predicatesToSolve) match {
        case ShardOperatorSequence(Some(SelectionOnMain(predicates)), None, None, None) =>
          RemoteBatchingResult(
            predicates,
            input
          )
        case ShardOperatorSequence(preBatchSelection, pushedDownPredicates, batchedProperties, postBatchSelection) =>
          val planWithProperties = Function.chain(Seq(
            planPreBatchSelection(preBatchSelection),
            planRemoteBatchPropertiesWithFilter(pushedDownPredicates),
            planRemoteBatchProperties(batchedProperties)
          ))(input)
          RemoteBatchingResult(
            postBatchSelection.map(_.predicates).getOrElse(RewrittenExpressions.empty),
            planWithProperties
          )
      }
    }

    override def planBatchPropertiesForHorizonSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression],
      interestingOrderConfig: InterestingOrderConfig
    ): RemoteBatchingResult = {
      def planPreBatchSelection(maybeMain: Option[InPlannerRemoteBatching.SelectionOnMain]) = (plan: LogicalPlan) =>
        maybeMain match {
          case Some(InPlannerRemoteBatching.SelectionOnMain(predicates)) =>
            context.staticComponents.logicalPlanProducer.planHorizonSelection(
              plan,
              predicates,
              interestingOrderConfig,
              context
            )
          case None => plan
        }

      def planRemoteBatchPropertiesWithFilter(maybeShard: Option[InPlannerRemoteBatching.SelectionOnShard]) =
        (plan: LogicalPlan) =>
          maybeShard match {
            case Some(InPlannerRemoteBatching.SelectionOnShard(propertiesToFetch, predicatesToExecute)) =>
              context.staticComponents.logicalPlanProducer.planRemoteBatchPropertiesForHorizonFilters(
                plan,
                propertiesToFetch,
                context,
                predicatesToExecute.toSeq
              )
            case None => plan
          }

      def planRemoteBatchProperties(maybeFetch: Option[InPlannerRemoteBatching.FetchPropertiesOnly]) =
        (plan: LogicalPlan) =>
          maybeFetch match {
            case Some(InPlannerRemoteBatching.FetchPropertiesOnly(propertiesToFetch)) =>
              context.staticComponents.logicalPlanProducer.planRemoteBatchProperties(
                plan,
                propertiesToFetch,
                context
              )
            case None => plan
          }

      operatorSequenceForSelections(queryGraph, input, context, predicatesToSolve) match {
        case ShardOperatorSequence(Some(SelectionOnMain(predicates)), None, None, None) =>
          RemoteBatchingResult(
            predicates,
            input
          )
        case ShardOperatorSequence(preBatchSelection, pushedDownPredicates, batchedProperties, postBatchSelection) =>
          val planWithProperties = Function.chain(Seq(
            planPreBatchSelection(preBatchSelection),
            planRemoteBatchPropertiesWithFilter(pushedDownPredicates),
            planRemoteBatchProperties(batchedProperties)
          ))(input)
          RemoteBatchingResult(
            postBatchSelection.map(_.predicates).getOrElse(RewrittenExpressions.empty),
            planWithProperties
          )
      }
    }

    override def planRemoteBatchProperties(
      inputPlan: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): RemoteBatchingResult = {
      val accessedProperties = PropertyAccessHelper.findPropertyAccesses(expressions)
      val rewriter = cachedPropertiesRewriter(inputPlan, context)
      val rewrittenExpressions =
        RewrittenExpressions.forMap(expressions.map(expr => expr -> expr.endoRewrite(rewriter)).toMap)
      RemoteBatchingResult(
        rewrittenExpressions,
        planBatchProperties(inputPlan, context, accessedProperties, rewrittenExpressions.allRewrittenExpressions.toSeq)
      )
    }

    override def planBatchPropertiesForExpressionsWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): RemoteBatchingResult = {
      val accessedProperties = remainingPropertyAccesses(queryGraph, input, context)

      val rewriter = cachedPropertiesRewriter(input, context)
      val rewrittenExpressions =
        RewrittenExpressions.forMap(expressions.map(expr => expr -> expr.endoRewrite(rewriter)).toMap)
      RemoteBatchingResult(
        rewrittenExpressions,
        planBatchProperties(input, context, accessedProperties, rewrittenExpressions.allRewrittenExpressions.toSeq)
      )
    }

    private def remainingPropertyAccesses(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToIgnore: Set[Expression] = Set.empty
    ): Set[PropertyAccess] = {
      accessedPropertiesForPredicates(queryGraph, input, context, predicatesToIgnore) ++
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
      context: LogicalPlanningContext,
      predicatesToIgnore: Set[Expression]
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
          .diff(previouslySolvedPredicates ++ predicatesToIgnore)
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
      expressions: Iterable[Expression]
    ): LogicalPlan = {
      val props = propertiesToFetch(input, context, accessedProperties, expressions)
      if (props.nonEmpty)
        context.staticComponents.logicalPlanProducer.planRemoteBatchProperties(input, props, context)
      else input
    }

    /*
     * Identify the sequence of operations to perform for this selection. Depending on the predicates, the sequence will be something like this:
     * 1. prefilter with selections on the main (before fetching anything from shards)
     * 2. push predicates down to the shards
     * 3. fetch properties from the shards for predicates that cannot be pushed down
     * 4. apply the remaining predicates on main.
     */
    private def operatorSequenceForSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression]
    ): ShardOperatorSequence = {
      val shardPredicatePushdownPartition = ShardPredicatePushdownPartition(
        input,
        context,
        predicatesToSolve
      )
      val accessedProperties = remainingPropertyAccesses(
        queryGraph,
        input,
        context,
        predicatesToIgnore = shardPredicatePushdownPartition.filterOnShards.map(_.expressions).getOrElse(Set.empty)
      )
      val rewriter = cachedPropertiesRewriter(input, context)
      // re-write prefilter predicates since there may be some already cached property accesses there.
      val rewrittenPreFilterBeforePushdown =
        RewrittenExpressions.forMap(shardPredicatePushdownPartition.preFilterBeforePushdown.map(expr =>
          expr -> expr.endoRewrite(rewriter)
        ).toMap)
      // re-write post remoteBatchProperties predicates to identify property accesses there.
      val rewrittenExprsAfterRemoteBatchProperties =
        RewrittenExpressions.forMap(shardPredicatePushdownPartition.filterOnMainWithRemoteProperties.map(expr =>
          expr -> expr.endoRewrite(rewriter)
        ).toMap)

      // Apart from variables used in the rewritten predicates we would also like to find property accesses on the variable for which predicates are being pushed down to the shard.
      // Ideally the property accesses for this variable must be from expressions (like projections or future predicates) outside those being pushed down to the shard
      val propsToFetchToMain =
        propertiesToFetch(
          input,
          context,
          accessedProperties,
          rewrittenExprsAfterRemoteBatchProperties.allRewrittenExpressions.toSeq,
          shardPredicatePushdownPartition.filterOnShards.map(_.logicalVariable)
        )

      val propertiesWithPushdownPredicates = shardPredicatePushdownPartition.filterOnShards match {
        case Some(PushedPredicatesDetails(variableToFetchRemoteProperties, predicates)) =>
          // Find properties that are either going to be used in predicates that cannot be pushed down or in later expressions
          val propertiesToFetchForVar = propsToFetchToMain.filter(_.entityVariable == variableToFetchRemoteProperties)
          if (propertiesToFetchForVar.nonEmpty) {
            propertiesToFetchForVar
          } else {
            // Could not find later property accesses. The main goal is to execute these queries on the shards.
            // Unfortunately remoteBatchPropertiesWithFilter requires at least one property back
            // We'll use one property access from the predicates being pushed down to limit the amount of data being returned.
            val rewriter = cachedPropertiesRewriter(input, context)
            propertiesToFetch(
              input,
              context,
              Set.empty,
              predicates.map(_.endoRewrite(rewriter))
            ).headOption match {
              case Some(cachedProperty) => Set(cachedProperty)
              case None                 =>
                // This should not happen. If this happens that means we are not pushing down these predicates.
                Set.empty[CachedProperty]
            }
          }
        case None => Set.empty[CachedProperty]
      }

      val propsToFetchWithoutFilter = propsToFetchToMain.diff(propertiesWithPushdownPredicates)

      ShardOperatorSequence(
        Option.when(rewrittenPreFilterBeforePushdown.nonEmpty)(SelectionOnMain(
          rewrittenPreFilterBeforePushdown
        )),
        Option.when(propertiesWithPushdownPredicates.nonEmpty)(SelectionOnShard(
          propertiesToFetch = propertiesWithPushdownPredicates,
          predicatesToExecute = shardPredicatePushdownPartition.filterOnShards.get.expressions
        )),
        Option.when(propsToFetchWithoutFilter.nonEmpty && rewrittenExprsAfterRemoteBatchProperties.nonEmpty)(
          FetchPropertiesOnly(propsToFetchWithoutFilter)
        ),
        Option.when(rewrittenExprsAfterRemoteBatchProperties.nonEmpty)(SelectionOnMain(
          rewrittenExprsAfterRemoteBatchProperties
        ))
      )
    }

    private def propertiesToFetch(
      input: LogicalPlan,
      context: LogicalPlanningContext,
      accessedProperties: Set[PropertyAccess],
      expressions: Iterable[Expression],
      includeVariable: Option[LogicalVariable] = None
    ): Set[CachedProperty] = {
      val accessedPropertiesMap = accessedProperties.map {
        accessedProperty =>
          accessedProperty.variable -> PropertyKeyName(accessedProperty.propertyName)(InputPosition.NONE)
      }.groupMap(_._1)(_._2)

      val alreadyCachedProperties =
        context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(input.id)

      val propsForUsedVars = expressions.folder.treeFold(Set[CachedProperty]()) {
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

      val propsForIncludedVar = includeVariable.map(variable =>
        alreadyCachedProperties.propertiesNotYetCached(
          variable,
          accessedPropertiesMap.getOrElse(variable, Set.empty)
        ).flatMap(propertyKeyName =>
          toCachedProperty(
            context,
            alreadyCachedProperties,
            variable,
            propertyKeyName,
            variable.position,
            knownToCacheStore = true
          )
        )
      ).getOrElse(Set.empty)

      propsForUsedVars ++ propsForIncludedVar
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

    private case class ShardOperatorSequence(
      preBatchSelection: Option[SelectionOnMain],
      pushedDownPredicates: Option[SelectionOnShard],
      batchedProperties: Option[FetchPropertiesOnly],
      postBatchSelection: Option[SelectionOnMain]
    )

    sealed private trait ShardOperator

    private case class SelectionOnMain(predicates: RewrittenExpressions)
        extends ShardOperator

    private case class SelectionOnShard(propertiesToFetch: Set[CachedProperty], predicatesToExecute: Set[Expression])
        extends ShardOperator
    private case class FetchPropertiesOnly(propertiesToFetch: Set[CachedProperty]) extends ShardOperator
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
      RemoteBatchingResult(RewrittenExpressions.withNoRewrittenExprs(predicatesToSolve), input)

    override def planBatchPropertiesForHorizonSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression],
      interestingOrderConfig: InterestingOrderConfig
    ): RemoteBatchingResult =
      RemoteBatchingResult(RewrittenExpressions.withNoRewrittenExprs(predicatesToSolve), input)

    override def planBatchPropertiesForExpressionsWithLookahead(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): RemoteBatchingResult = RemoteBatchingResult(RewrittenExpressions.withNoRewrittenExprs(expressions), input)

    override def planRemoteBatchProperties(
      inputPlan: LogicalPlan,
      context: LogicalPlanningContext,
      expressions: Iterable[Expression]
    ): RemoteBatchingResult =
      RemoteBatchingResult(RewrittenExpressions.withNoRewrittenExprs(expressions), inputPlan)

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

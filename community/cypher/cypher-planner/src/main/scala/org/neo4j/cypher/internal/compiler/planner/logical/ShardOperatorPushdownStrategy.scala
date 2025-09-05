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

import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper.PropertyAccess
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.compiler.planner.logical.steps.SelectionCandidate
import org.neo4j.cypher.internal.expressions.Add
import org.neo4j.cypher.internal.expressions.CachedProperty
import org.neo4j.cypher.internal.expressions.EntityType
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalProperty
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RELATIONSHIP_TYPE
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.QueryProjection
import org.neo4j.cypher.internal.logical.plans.Apply
import org.neo4j.cypher.internal.logical.plans.Argument
import org.neo4j.cypher.internal.logical.plans.CachedProperties
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.RemoteBatchPropertiesWithPushdownOperators
import org.neo4j.cypher.internal.logical.plans.RewrittenSubQueryPredicates
import org.neo4j.cypher.internal.logical.plans.RewrittenSubQueryPredicates.RewrittenSubQueryPredicatesMap
import org.neo4j.cypher.internal.planner.spi.DatabaseMode
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.symbols.CTRelationship

sealed trait ShardOperatorPushdownStrategy {

  def isPushdownEnabled: Boolean

  def skipAndLimit(
    input: LogicalPlan,
    queryGraph: QueryGraph,
    queryProjection: QueryProjection,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): LogicalPlan

  def selections(
    input: LogicalPlan,
    queryGraph: QueryGraph,
    context: LogicalPlanningContext,
    predicatesToSolve: RewrittenSubQueryPredicatesMap
  ): Option[SelectionCandidate]

  def horizonSelections(
    input: LogicalPlan,
    queryGraph: QueryGraph,
    context: LogicalPlanningContext,
    interestingOrderConfig: InterestingOrderConfig,
    predicatesToSolve: RewrittenSubQueryPredicatesMap
  ): Option[SelectionCandidate]
}

object ShardOperatorPushdownStrategy {

  private case object NoPushdown extends ShardOperatorPushdownStrategy {

    override def selections(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      context: LogicalPlanningContext,
      predicatesToSolve: RewrittenSubQueryPredicatesMap
    ): Option[SelectionCandidate] = None

    override def skipAndLimit(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      queryProjection: QueryProjection,
      interestingOrderConfig: InterestingOrderConfig,
      context: LogicalPlanningContext
    ): LogicalPlan = input

    override val isPushdownEnabled: Boolean = false

    override def horizonSelections(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      context: LogicalPlanningContext,
      interestingOrderConfig: InterestingOrderConfig,
      predicatesToSolve: RewrittenSubQueryPredicatesMap
    ): Option[SelectionCandidate] = None
  }

  private case object PushdownSelections extends ShardOperatorPushdownStrategy {

    private def cachedProperty(
      context: LogicalPlanningContext,
      alreadyCachedProperties: CachedProperties,
      propertyAccess: PropertyAccess
    ): CachedProperty = {
      val positionToUse = propertyAccess.variable.position
      alreadyCachedProperties.entries.get(propertyAccess.variable) match {
        case Some(entry) =>
          CachedProperty(
            entry.originalEntity,
            propertyAccess.variable,
            PropertyKeyName(propertyAccess.propertyName)(positionToUse),
            entry.entityType,
            knownToAccessStore = true
          )(
            positionToUse
          )
        case None =>
          val entityType = if (context.semanticTable.typeFor(propertyAccess.variable).is(CTRelationship))
            RELATIONSHIP_TYPE
          else
            NODE_TYPE

          CachedProperty(
            propertyAccess.variable,
            propertyAccess.variable,
            PropertyKeyName(propertyAccess.propertyName)(positionToUse),
            entityType,
            knownToAccessStore = true
          )(positionToUse)
      }
    }

    override def selections(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      context: LogicalPlanningContext,
      predicatesToSolve: RewrittenSubQueryPredicatesMap
    ): Option[SelectionCandidate] = {
      val operatorSequence = operatorSequenceForSelections(queryGraph, input, context, predicatesToSolve)
      val alreadyCachedProperties = context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(input.id)
      val planWithPrefetchedSelection = operatorSequence.preBatchSelection.map(preFilter => {

        val prefilteredSelection = context.staticComponents.logicalPlanProducer.planSelectionWithSolvedPredicates(
          input,
          preFilter.predicates,
          context
        )
        SelectionCandidate(prefilteredSelection, preFilter.predicates.originalExpressions.toSet)
      })

      val planWithPushedDownPredicates = operatorSequence.pushedDownPredicates.map(pushedDownPredicates => {
        val propertiesToFetch =
          pushedDownPredicates.propertiesToFetch.map(cachedProperty(context, alreadyCachedProperties, _))
        val plan = context.staticComponents.logicalPlanProducer.planRemoteBatchPropertiesWithFilter(
          planWithPrefetchedSelection.map(_.plan).getOrElse(input),
          propertiesToFetch,
          context,
          pushedDownPredicates.predicatesToExecute.allRewrittenExpressions.toSeq,
          pushedDownPredicates.predicatesToExecute.originalExpressions.toSeq
        )
        SelectionCandidate(
          plan,
          planWithPrefetchedSelection.map(_.solvedPredicates).getOrElse(
            Set.empty
          ) ++ pushedDownPredicates.predicatesToExecute.originalExpressions
        )
      })

      planWithPushedDownPredicates.orElse(planWithPrefetchedSelection)
    }

    override def skipAndLimit(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      queryProjection: QueryProjection,
      interestingOrderConfig: InterestingOrderConfig,
      context: LogicalPlanningContext
    ): LogicalPlan =
      input

    override val isPushdownEnabled: Boolean = true

    override def horizonSelections(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      context: LogicalPlanningContext,
      interestingOrderConfig: InterestingOrderConfig,
      predicatesToSolve: RewrittenSubQueryPredicatesMap
    ): Option[SelectionCandidate] = {
      val operatorSequence = operatorSequenceForSelections(queryGraph, input, context, predicatesToSolve)
      val alreadyCachedProperties = context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(input.id)
      val planWithPrefetchedSelection = operatorSequence.preBatchSelection match {
        case Some(preFilter) => Some(SelectionCandidate(
            context.staticComponents.logicalPlanProducer.planHorizonSelection(
              input,
              preFilter.predicates,
              interestingOrderConfig,
              context
            ),
            preFilter.predicates.originalExpressions.toSet
          ))
        case None => None
      }

      operatorSequence.pushedDownPredicates match {
        case Some(pushedDownPredicates) =>
          val propertiesToFetch =
            pushedDownPredicates.propertiesToFetch.map(cachedProperty(context, alreadyCachedProperties, _))
          val plan = context.staticComponents.logicalPlanProducer.planRemoteBatchPropertiesForHorizonFilters(
            planWithPrefetchedSelection.map(_.plan).getOrElse(input),
            propertiesToFetch,
            context,
            pushedDownPredicates.predicatesToExecute
          )
          Some(SelectionCandidate(
            plan,
            planWithPrefetchedSelection.map(_.solvedPredicates).getOrElse(
              Set.empty
            ) ++ pushedDownPredicates.predicatesToExecute.originalExpressions
          ))
        case None => planWithPrefetchedSelection
      }
    }

  }

  case object PushdownOperators extends ShardOperatorPushdownStrategy {

    override def skipAndLimit(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      queryProjection: QueryProjection,
      interestingOrderConfig: InterestingOrderConfig,
      context: LogicalPlanningContext
    ): LogicalPlan = {
      val pushdownLimitOpt =
        pushdownSkipAndLimit(
          input,
          queryProjection,
          context
        )

      pushdownLimitOpt.map(context.staticComponents.logicalPlanProducer.planProjectionsOnShards(
        _,
        RewrittenSubQueryPredicates.empty,
        context
      )).getOrElse(input)
    }

    private def previousPushdownOperator(
      input: LogicalPlan
    ): Option[RemoteBatchPropertiesWithPushdownOperators] = input match {
      case pushdownOperator: RemoteBatchPropertiesWithPushdownOperators =>
        Some(pushdownOperator)
      case Apply(pushdownOperator: RemoteBatchPropertiesWithPushdownOperators, _: Argument) => Some(pushdownOperator)
      case _                                                                                => None
    }

    private def pushdownSkipAndLimit(
      inputPlan: LogicalPlan,
      queryProjection: QueryProjection,
      context: LogicalPlanningContext
    ): Option[RemoteBatchPropertiesWithPushdownOperators] = {
      if (queryProjection.queryPagination.nonEmpty) {
        previousPushdownOperator(inputPlan)
          .filter(op => op.limit.isEmpty)
          .map { op =>
            val maybeLimit = (queryProjection.queryPagination.skip, queryProjection.queryPagination.limit) match {
              case (Some(skipExpr), Some(limitExpr)) => Some(Add(limitExpr, skipExpr)(limitExpr.position))
              case (_, limit)                        => limit
            }

            RemoteBatchPropertiesWithPushdownOperators(
              source =
                inputPlan, // if it is an empty apply we should still point to it. The logical plan producer will merge the two operators correctly.
              properties = op.properties,
              variable = op.variable,
              entityType = op.entityType,
              limit = maybeLimit
            )(
              context.staticComponents.idGen
            ) // if the previous pushdown operator has no skip and limit, we can use it as is.
          }
      } else
        None
    }

    override def selections(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      context: LogicalPlanningContext,
      predicatesToSolve: RewrittenSubQueryPredicatesMap
    ): Option[SelectionCandidate] = {
      val operatorSequence = operatorSequenceForSelections(queryGraph, input, context, predicatesToSolve)
      val planWithPrefetchedSelection = operatorSequence.preBatchSelection.map(preFilter => {

        val prefilteredSelection = context.staticComponents.logicalPlanProducer.planSelectionWithSolvedPredicates(
          input,
          preFilter.predicates,
          context
        )
        SelectionCandidate(prefilteredSelection, preFilter.predicates.originalExpressions.toSet)
      })

      val planWithPushedDownPredicates = operatorSequence.pushedDownPredicates.map(pushedDownPredicates => {
        val (previouslyCachedProperties, arguments) =
          findPreviousCachedPropsAndArgs(pushedDownPredicates.logicalVariable, pushedDownPredicates.predicatesToExecute)
        val remoteBatchPropertiesWithPushdownOperators = RemoteBatchPropertiesWithPushdownOperators(
          source = planWithPrefetchedSelection.map(_.plan).getOrElse(input),
          variable = pushedDownPredicates.logicalVariable,
          entityType = pushedDownPredicates.entityType,
          properties = pushedDownPredicates.propertiesToFetch.map(propertyAccess =>
            PropertyKeyName(propertyAccess.propertyName)(InputPosition.NONE)
          ),
          predicates =
            pushedDownPredicates.predicatesToExecute.allRewrittenExpressions.toSeq,
          arguments = arguments,
          previouslyCachedProperties = previouslyCachedProperties
        )(context.staticComponents.idGen)

        val plan = context.staticComponents.logicalPlanProducer.planShardSelections(
          remoteBatchPropertiesWithPushdownOperators,
          pushedDownPredicates.predicatesToExecute,
          context
        )
        SelectionCandidate(
          plan,
          planWithPrefetchedSelection.map(_.solvedPredicates).getOrElse(
            Set.empty
          ) ++ pushedDownPredicates.predicatesToExecute.originalExpressions
        )
      })

      planWithPushedDownPredicates.orElse(planWithPrefetchedSelection)
    }

    private def findPreviousCachedPropsAndArgs(
      pushDownVariable: LogicalVariable,
      rewrittenSubQueryPredicates: RewrittenSubQueryPredicatesMap
    ): (Set[LogicalProperty], Set[LogicalVariable]) = {
      rewrittenSubQueryPredicates.allRewrittenExpressions.folder.treeFold((
        Set.empty[LogicalProperty],
        Set.empty[LogicalVariable]
      )) {
        case cachedProperty: CachedProperty if cachedProperty.entityVariable != pushDownVariable =>
          acc => SkipChildren((acc._1 + cachedProperty, acc._2 + cachedProperty.entityVariable))
        case logicalVariable: LogicalVariable if logicalVariable != pushDownVariable =>
          acc => SkipChildren((acc._1, acc._2 + logicalVariable))
      }
    }

    override def isPushdownEnabled: Boolean = true

    override def horizonSelections(
      input: LogicalPlan,
      queryGraph: QueryGraph,
      context: LogicalPlanningContext,
      interestingOrderConfig: InterestingOrderConfig,
      predicatesToSolve: RewrittenSubQueryPredicatesMap
    ): Option[SelectionCandidate] = {
      val operatorSequence = operatorSequenceForSelections(queryGraph, input, context, predicatesToSolve)
      val planWithPrefetchedSelection = operatorSequence.preBatchSelection.map(selectionOnMain => {
        val prefilteredSelection = context.staticComponents.logicalPlanProducer.planHorizonSelection(
          input,
          selectionOnMain.predicates,
          interestingOrderConfig,
          context
        )
        SelectionCandidate(prefilteredSelection, selectionOnMain.predicates.originalExpressions.toSet)
      })

      val planWithPushedDownPredicates = operatorSequence.pushedDownPredicates.map(pushedDownPredicates => {
        val (previouslyCachedProperties, arguments) =
          findPreviousCachedPropsAndArgs(pushedDownPredicates.logicalVariable, pushedDownPredicates.predicatesToExecute)
        val remoteBatchPropertiesWithPushdownOperators = RemoteBatchPropertiesWithPushdownOperators(
          source = planWithPrefetchedSelection.map(_.plan).getOrElse(input),
          variable = pushedDownPredicates.logicalVariable,
          entityType = pushedDownPredicates.entityType,
          properties = pushedDownPredicates.propertiesToFetch.map(propertyAccess =>
            PropertyKeyName(propertyAccess.propertyName)(InputPosition.NONE)
          ),
          predicates =
            pushedDownPredicates.predicatesToExecute.allRewrittenExpressions.toSeq,
          arguments = arguments,
          previouslyCachedProperties = previouslyCachedProperties
        )(context.staticComponents.idGen)

        val plan = context.staticComponents.logicalPlanProducer.planProjectionsOnShards(
          remoteBatchPropertiesWithPushdownOperators,
          operatorSequence.pushedDownPredicates.map(_.predicatesToExecute).getOrElse(RewrittenSubQueryPredicates.empty),
          context
        )
        SelectionCandidate(
          plan,
          planWithPrefetchedSelection.map(_.solvedPredicates).getOrElse(
            Set.empty
          ) ++ pushedDownPredicates.predicatesToExecute.originalExpressions
        )
      })
      planWithPushedDownPredicates.orElse(planWithPrefetchedSelection)
    }
  }

  def fromConfig(query: PlannerQuery, context: PlannerContext): ShardOperatorPushdownStrategy = {
    if (
      context.planContext.databaseMode == DatabaseMode.SHARDED
      && isReadOnlyWithEmptyTxState(query, context)
    ) {
      if (context.config.pushOperatorsToRemoteBatchPropertiesEnabled())
        PushdownOperators
      else
        PushdownSelections
    } else {
      NoPushdown
    }
  }

  def defaultValue(): ShardOperatorPushdownStrategy = NoPushdown

  private def isReadOnlyWithEmptyTxState(query: PlannerQuery, context: PlannerContext): Boolean =
    query.readOnly && !context.planContext.txStateHasChanges()

  private def remainingPropertyAccesses(
    variable: LogicalVariable,
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    predicatesSolvedByShard: Set[Expression]
  ): Set[PropertyAccess] = {
    def interestingPropAccesses(expr: Expression): Set[PropertyAccess] = {
      expr.folder.treeFold(Set[PropertyAccess]()) {
        case Property(propVariable, PropertyKeyName(propName)) if propVariable == variable =>
          set =>
            SkipChildren(set + PropertyAccess(variable, propName))
      }
    }

    val alreadyCachedProperties =
      context.staticComponents
        .planningAttributes.cachedPropertiesPerPlan.get(input.id)
        .entries.get(variable)
        .map(_.properties.map(_.name))
        .getOrElse(Set.empty[String])

    val previouslySolvedPredicates = context.staticComponents
      .planningAttributes.solveds
      .get(input.id)
      .asSinglePlannerQuery.queryGraph
      .selections
      .flatPredicatesSet

    // We also want to find property accesses that have not yet been cached for the given variable
    val propAccesses = (context.plannerState.contextualPropertyAccess.interestingOrder ++
      context.plannerState.contextualPropertyAccess.horizon ++
      context.plannerState.contextualPropertyAccess.propertyAccessInOtherComponents).filter(_.variable == variable)

    // we compute not only the predicates that will be solved by this selection
    val propertiesFromUnsolvedPredicates =
      queryGraph.selections.flatPredicatesSet
        .diff(previouslySolvedPredicates ++ predicatesSolvedByShard)
        .flatMap(interestingPropAccesses)

    // the properties for unsolved predicates and other expressions that are not yet cached need to fetched.
    // if there are none, i.e., we just want to run the filter on the shard,
    // we will fetch at least one property used in the predicates on the shard to match the signature of remoteBatchPropertiesWithFilter
    val propertiesToFetch = (propAccesses ++ propertiesFromUnsolvedPredicates).filterNot(propAccesses =>
      alreadyCachedProperties.contains(propAccesses.propertyName)
    )
    if (propertiesToFetch.isEmpty)
      predicatesSolvedByShard.collectFirst {
        case expr if interestingPropAccesses(expr).nonEmpty =>
          interestingPropAccesses(
            expr
          ).head // we just need one property access to run the remoteBatchPropertiesWithFilter
      }.map(Set(_))
        .getOrElse(Set.empty[PropertyAccess])
    else
      propertiesToFetch
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
    predicatesToSolve: RewrittenSubQueryPredicatesMap
  ): ShardOperatorSequence = {
    val shardPredicatePushdownPartition = ShardPredicatePushdownPartition(
      input,
      context,
      predicatesToSolve.allRewrittenExpressions.toSet
    )
    val rewriter = alreadyCachedPropertiesRewriter(input, context)

    val selectionOnShard = shardPredicatePushdownPartition.filterOnShards match {
      case Some(pushedPredicatesDetails: PushedPredicatesDetails) =>
        val propertiesToFetch = remainingPropertyAccesses(
          pushedPredicatesDetails.logicalVariable,
          queryGraph,
          input,
          context,
          pushedPredicatesDetails.expressions ++ shardPredicatePushdownPartition.preFilterBeforePushdown
        )
        val entityType = if (context.semanticTable.typeFor(pushedPredicatesDetails.logicalVariable).is(CTRelationship))
          RELATIONSHIP_TYPE
        else
          NODE_TYPE // we trust the shard predicate pushdown partition to only select relationship or node type predicates.

        Some(SelectionOnShard(
          pushedPredicatesDetails.logicalVariable,
          entityType,
          propertiesToFetch,
          RewrittenSubQueryPredicates.forMap(pushedPredicatesDetails.expressions.map(expr =>
            expr.endoRewrite(rewriter) -> predicatesToSolve.originalExpressionOrSelf(expr)
          ).toMap)
        ))
      case _ => None
    }
    // re-write prefilter predicates since there may be some already cached property accesses there.
    val prefilterBeforePushdown =
      if (
        shardPredicatePushdownPartition.preFilterBeforePushdown.nonEmpty && (shardPredicatePushdownPartition.filterOnShards.nonEmpty || shardPredicatePushdownPartition.filterOnMainWithRemoteProperties.nonEmpty)
      ) {
        Some(SelectionOnMain(RewrittenSubQueryPredicatesMap(shardPredicatePushdownPartition.preFilterBeforePushdown.map(
          expr =>
            expr.endoRewrite(rewriter) -> predicatesToSolve.originalExpressionOrSelf(expr)
        ).toMap)))
      } else None

    ShardOperatorSequence(
      prefilterBeforePushdown,
      selectionOnShard
    )
  }

  private def alreadyCachedProperty(
    alreadyCachedProperties: CachedProperties,
    logicalVariable: LogicalVariable,
    propertyKeyName: PropertyKeyName,
    position: InputPosition,
    knownToCacheStore: Boolean = true
  ): Option[CachedProperty] = {
    alreadyCachedProperties.entries.get(logicalVariable) match {
      case Some(CachedProperties.Entry(originalEntity, entityType, properties)) =>
        if (properties.contains(propertyKeyName))
          Some(CachedProperty(
            originalEntity,
            logicalVariable,
            propertyKeyName,
            entityType,
            knownToCacheStore
          )(
            position
          ))
        else None
      case None => None
    }
  }

  private def alreadyCachedPropertiesRewriter(
    inputPlan: LogicalPlan,
    context: LogicalPlanningContext
  ) = {
    val alreadyCachedProperties =
      context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(inputPlan.id)
    bottomUp.apply(
      rewriter = Rewriter.lift {
        case property @ Property(logicalVariable: LogicalVariable, propertyKeyName)
          if inputPlan.availableSymbols.contains(logicalVariable) =>
          alreadyCachedProperty(
            alreadyCachedProperties,
            logicalVariable,
            propertyKeyName,
            property.position
          ).getOrElse(property)
      },
      cancellation = context.staticComponents.cancellationChecker
    )
  }

  private case class ShardOperatorSequence(
    preBatchSelection: Option[SelectionOnMain],
    pushedDownPredicates: Option[SelectionOnShard]
  )

  private case class SelectionOnMain(predicates: RewrittenSubQueryPredicatesMap)

  private case class SelectionOnShard(
    logicalVariable: LogicalVariable,
    entityType: EntityType,
    propertiesToFetch: Set[PropertyAccess],
    predicatesToExecute: RewrittenSubQueryPredicatesMap
  )
}

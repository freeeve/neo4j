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

import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper
import org.neo4j.cypher.internal.compiler.planner.logical.idp.BestResults
import org.neo4j.cypher.internal.compiler.planner.logical.steps.BestPlans
import org.neo4j.cypher.internal.ir.SinglePlannerQuery
import org.neo4j.cypher.internal.ir.ordering.InterestingOrder
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.DatabaseMode

/*
This class ties together disparate query graphs through their event horizons. It does so by using Apply,
which in most cases is then rewritten away by LogicalPlan rewriting.
 */
case class PlanWithTail(
  eventHorizonPlanner: EventHorizonPlanner = PlanEventHorizon,
  matchPlanner: MatchPlanner = planMatch,
  updatesPlanner: UpdatesPlanner = PlanUpdates
) extends TailPlanner {

  private val defaultPlannerWithTail = DefaultPlanWithTail(eventHorizonPlanner, matchPlanner, updatesPlanner)
  private val spdPlannerWithTail = SPDPlanWithTailStrategy(eventHorizonPlanner, matchPlanner, updatesPlanner)

  /**
   * @param previousInterestingOrder The previous interesting order, if it exists, and only if the tailQuery has an empty query graph.
   */
  override def plan(
    lhsPlans: BestPlans,
    tailQuery: SinglePlannerQuery,
    previousInterestingOrder: Option[InterestingOrder],
    context: LogicalPlanningContext
  ): (BestPlans, LogicalPlanningContext) = {
    if (context.staticComponents.planContext.databaseMode == DatabaseMode.SHARDED) {
      spdPlannerWithTail.plan(lhsPlans, tailQuery, previousInterestingOrder, context)
    } else {
      defaultPlannerWithTail.plan(lhsPlans, tailQuery, previousInterestingOrder, context)
    }
  }
}

sealed private trait PlanWithTailStrategy {

  def plan(
    lhsPlans: BestPlans,
    tailQuery: SinglePlannerQuery,
    previousInterestingOrder: Option[InterestingOrder],
    context: LogicalPlanningContext
  ): (BestPlans, LogicalPlanningContext)
}

private case class DefaultPlanWithTail(
  eventHorizonPlanner: EventHorizonPlanner = PlanEventHorizon,
  matchPlanner: MatchPlanner = planMatch,
  updatesPlanner: UpdatesPlanner = PlanUpdates
) extends PlanWithTailStrategy {

  override def plan(
    lhsPlans: BestPlans,
    tailQuery: SinglePlannerQuery,
    previousInterestingOrder: Option[InterestingOrder],
    context: LogicalPlanningContext
  ): (BestPlans, LogicalPlanningContext) = {
    val updatedContext = context.withModifiedPlannerState(_
      .withAccessedProperties(PropertyAccessHelper.findLocalPropertyAccesses(tailQuery)))

    val rhsPlan = planRhs(
      tailQuery,
      updatedContext
        .withModifiedPlannerState(_
          .withOuterPlan(lhsPlans.bestResult)
          .withPreviouslyCachedProperties(
            updatedContext.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(lhsPlans.bestResult.id)
          )
          .withUpdatedLabelInfo(lhsPlans.bestResult, context.staticComponents.planningAttributes.solveds))
    )
    (planApply(lhsPlans, rhsPlan, previousInterestingOrder, tailQuery, updatedContext), updatedContext)
  }

  private def planRhs(tailQuery: SinglePlannerQuery, context: LogicalPlanningContext): LogicalPlan = {
    val rhsPlan =
      matchPlanner.plan(tailQuery, context, rhsPart = true).result // always expecting a single plan currently
    context.staticComponents.logicalPlanProducer.addMissingStandaloneArgumentPatternNodes(
      rhsPlan,
      tailQuery,
      context
    )
  }

  private def planApply(
    lhsPlans: BestPlans,
    rhsPlan: LogicalPlan,
    previousInterestingOrder: Option[InterestingOrder],
    tailQuery: SinglePlannerQuery,
    context: LogicalPlanningContext
  ): BestPlans = {
    val applyPlans = lhsPlans.map(context.staticComponents.logicalPlanProducer.planTailApply(_, rhsPlan, context))

    val plansWithUpdates =
      applyPlans.map(p => updatesPlanner.plan(tailQuery, p, firstPlannerQuery = false, context))

    eventHorizonPlanner.planHorizon(tailQuery, plansWithUpdates, previousInterestingOrder, context)
  }
}

private case class SPDPlanWithTailStrategy(
  eventHorizonPlanner: EventHorizonPlanner = PlanEventHorizon,
  matchPlanner: MatchPlanner = planMatch,
  updatesPlanner: UpdatesPlanner = PlanUpdates
) extends PlanWithTailStrategy {

  override def plan(
    lhsPlans: BestPlans,
    tailQuery: SinglePlannerQuery,
    previousInterestingOrder: Option[InterestingOrder],
    context: LogicalPlanningContext
  ): (BestPlans, LogicalPlanningContext) = {
    (planApply(lhsPlans, previousInterestingOrder, tailQuery, context), context)
  }

  private def planRhs(
    tailQuery: SinglePlannerQuery,
    context: LogicalPlanningContext,
    lhsPlan: LogicalPlan
  ): BestPlans = {
    val updatedContext = context.withModifiedPlannerState(_
      .withAccessedProperties(PropertyAccessHelper.findLocalPropertyAccesses(tailQuery))
      .withContextualPropertyAccess(PropertyAccessHelper.findGlobalPropertyAccessesWithContext(tailQuery))
      .withOuterPlan(lhsPlan)
      .withPreviouslyCachedProperties(
        context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(lhsPlan.id)
      )
      .withUpdatedLabelInfo(lhsPlan, context.staticComponents.planningAttributes.solveds))
    matchPlanner.plan(tailQuery, updatedContext, rhsPart = true).map(
      context.staticComponents.logicalPlanProducer.addMissingStandaloneArgumentPatternNodes(
        _,
        tailQuery,
        context
      )
    )
  }

  private def planApply(
    lhsPlans: BestPlans,
    previousInterestingOrder: Option[InterestingOrder],
    tailQuery: SinglePlannerQuery,
    context: LogicalPlanningContext
  ): BestPlans = {
    val plansOnLHSBestOverall = planRhs(tailQuery, context, lhsPlans.bestResult).allResults.map(
      context.staticComponents.logicalPlanProducer.planTailApply(
        lhsPlans.bestResult,
        _,
        context
      )
    ).map(p => updatesPlanner.plan(tailQuery, p, firstPlannerQuery = false, context))
    val plansOnLHSBestSorted = lhsPlans.bestSortedResult.map(lhsPlan => {
      val rhsPlan = planRhs(tailQuery, context, lhsPlan).result
      val applyPlan = context.staticComponents.logicalPlanProducer.planTailApply(lhsPlan, rhsPlan, context)
      updatesPlanner.plan(tailQuery, applyPlan, firstPlannerQuery = false, context)
    })
    val plansOnLHSWithBestProperties = lhsPlans.bestExtraPropertiesResult.map(lhsPlan =>
      planRhs(tailQuery, context, lhsPlan).allResults.map(context.staticComponents.logicalPlanProducer.planTailApply(
        lhsPlan,
        _,
        context
      )).map(p =>
        updatesPlanner.plan(tailQuery, p, firstPlannerQuery = false, context)
      )
    ).getOrElse(List.empty)

    val pickBest: CandidateSelector = context.plannerState.config.pickBestCandidate(context)
    val extraPropertyRequirements =
      context.settings.remoteBatchPropertiesStrategy.interestingPropertiesAsIDPExtraRequirement(
        tailQuery.queryGraph,
        context
      )
    val bestPlansWithApplyAndUpdates = BestResults(
      bestResult = pickBest(
        plansOnLHSBestOverall.toSet ++ plansOnLHSWithBestProperties ++ plansOnLHSBestSorted,
        "best overall plan with applies and updates"
      ).getOrElse(
        throw new IllegalStateException("Planner returned no best overall plan")
      ),
      bestSortedResult = plansOnLHSBestSorted,
      bestExtraPropertiesResult = pickBest(
        plansOnLHSWithBestProperties.filter(extraPropertyRequirements.fulfils),
        "best plan with prefetched properties for applies and updates"
      )
    )
    eventHorizonPlanner.planHorizon(tailQuery, bestPlansWithApplyAndUpdates, previousInterestingOrder, context)
  }
}

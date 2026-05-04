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

import org.neo4j.cypher.internal.ast.IrHint
import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.planner.logical.ShardPredicatePushdownPartition
import org.neo4j.cypher.internal.compiler.planner.logical.SpdSelections
import org.neo4j.cypher.internal.compiler.planner.logical.SpdSelections.SpdSelectionAndChild
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.compiler.planner.logical.steps.QuerySolvableByGetDegree.SetExtractor
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.QueryPagination
import org.neo4j.cypher.internal.ir.RegularQueryProjection
import org.neo4j.cypher.internal.ir.RegularSinglePlannerQuery
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.ir.Selections.containsExistsSubquery
import org.neo4j.cypher.internal.ir.SimplePatternLength
import org.neo4j.cypher.internal.ir.ast.ExistsIRExpression
import org.neo4j.cypher.internal.ir.ordering.InterestingOrder
import org.neo4j.cypher.internal.logical.plans.Expand
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Selection
import org.neo4j.cypher.internal.util.attribution.Id

case object triadicSelectionFinder extends SelectionCandidateGenerator {

  case class ExpandWithSelections(
    expand: Expand,
    predicatesOnMainAfterRBP: Set[Expression] = Set.empty,
    predicatesOnShards: Set[Expression] = Set.empty,
    predicatesOnMainBeforeRBP: Seq[Expression] = Seq.empty
  )

  override def apply(
    input: LogicalPlan,
    unsolvedPredicates: Set[Expression],
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Iterator[SelectionCandidate] = {
    // The current runtime implementations of TriadicSelection relies on order being preserved and is not yet supported
    // by parallel runtime.
    if (context.settings.executionModel.providedOrderPreserving) {
      unsolvedPredicates.iterator.filter(containsExistsSubquery).collect {
        // WHERE NOT (a)-[:X]->(c)
        case predicate @ Not(subqueryExpression: ExistsIRExpression) =>
          findMatchingRelationshipPattern(
            positivePredicate = false,
            predicate,
            subqueryExpression,
            input,
            queryGraph,
            context
          )
            .map(SelectionCandidate(_, Set(predicate)))
        // WHERE (a)-[:X]->(c)
        case predicate: ExistsIRExpression =>
          findMatchingRelationshipPattern(
            positivePredicate = true,
            predicate,
            predicate,
            input,
            queryGraph,
            context
          )
            .map(SelectionCandidate(_, Set(predicate)))
      }.flatten
    } else {
      Iterator.empty[SelectionCandidate]
    }
  }

  private def findMatchingRelationshipPattern(
    positivePredicate: Boolean,
    triadicPredicate: Expression,
    subqueryExpression: ExistsIRExpression,
    in: LogicalPlan,
    qg: QueryGraph,
    context: LogicalPlanningContext
  ): Seq[LogicalPlan] = in match {

    // MATCH (a)-[:X]->(b)-[:X]->(c) WHERE (predicate involving (a)-[:X]->(c))
    case Selection(Ands(predicates), exp: Expand) => findMatchingOuterExpand(
        positivePredicate,
        triadicPredicate,
        subqueryExpression,
        ExpandWithSelections(exp, predicatesOnMainBeforeRBP = predicates.toSeq),
        qg,
        context
      )

    // MATCH (a)-[:X]->(b)-[:Y]->(c) WHERE (predicate involving (a)-[:X]->(c))
    case exp: Expand =>
      findMatchingOuterExpand(
        positivePredicate,
        triadicPredicate,
        subqueryExpression,
        ExpandWithSelections(exp),
        qg,
        context
      )

    case SpdSelections(SpdSelectionAndChild(
        spdSelections: ShardPredicatePushdownPartition,
        exp: Expand
      )) =>
      findMatchingOuterExpand(
        positivePredicate,
        triadicPredicate,
        subqueryExpression,
        ExpandWithSelections(
          exp,
          predicatesOnMainAfterRBP = spdSelections.filterOnMainWithRemoteProperties,
          predicatesOnShards = spdSelections.filterOnShards.map(_.expressions).getOrElse(Set.empty),
          predicatesOnMainBeforeRBP = spdSelections.preFilterBeforePushdown.toSeq
        ),
        qg,
        context
      )

    case _ => Seq.empty
  }

  private def findMatchingOuterExpand(
    positivePredicate: Boolean,
    triadicPredicate: Expression,
    subqueryExpression: ExistsIRExpression,
    expandWithSelections: ExpandWithSelections,
    qg: QueryGraph,
    context: LogicalPlanningContext
  ): Seq[LogicalPlan] = expandWithSelections.expand match {
    case Expand(exp1: Expand, _, _, _, _, _, ExpandAll) =>
      findMatchingInnerExpand(
        positivePredicate,
        triadicPredicate,
        subqueryExpression,
        ExpandWithSelections(exp1),
        expandWithSelections,
        qg,
        context
      )

    case Expand(Selection(Ands(innerPredicates), exp1: Expand), _, _, _, _, _, ExpandAll) =>
      findMatchingInnerExpand(
        positivePredicate,
        triadicPredicate,
        subqueryExpression,
        ExpandWithSelections(
          exp1,
          predicatesOnMainBeforeRBP = innerPredicates.toSeq
        ),
        expandWithSelections,
        qg,
        context
      )

    case Expand(
        SpdSelections(SpdSelectionAndChild(
          spdSelections: ShardPredicatePushdownPartition,
          exp: Expand
        )),
        _,
        _,
        _,
        _,
        _,
        ExpandAll
      ) =>
      findMatchingInnerExpand(
        positivePredicate,
        triadicPredicate,
        subqueryExpression,
        ExpandWithSelections(
          exp,
          predicatesOnMainAfterRBP = spdSelections.filterOnMainWithRemoteProperties,
          predicatesOnShards = spdSelections.filterOnShards.map(_.expressions).getOrElse(Set.empty),
          predicatesOnMainBeforeRBP = spdSelections.preFilterBeforePushdown.toSeq
        ),
        expandWithSelections,
        qg,
        context
      )

    case _ => Seq.empty
  }

  private def findMatchingInnerExpand(
    positivePredicate: Boolean,
    triadicPredicate: Expression,
    subqueryExpression: ExistsIRExpression,
    exp1WithSelections: ExpandWithSelections,
    exp2WithSelections: ExpandWithSelections,
    qg: QueryGraph,
    context: LogicalPlanningContext
  ): Seq[LogicalPlan] = {
    val exp1 = exp1WithSelections.expand
    val exp2 = exp2WithSelections.expand
    val (acceptableLeftPredicates, newRightPredicates) =
      exp1WithSelections.predicatesOnMainBeforeRBP.partition(leftPredicatesAcceptable(exp1.to, _))
    if (
      exp1.mode == ExpandAll && exp1.to == exp2.from &&
      matchingLabels(positivePredicate, exp1.to, exp2.to, qg) &&
      matchingIRExpression(subqueryExpression, exp1.from, exp2.to, exp1.types, exp1.dir)
    ) {
      val left: LogicalPlan =
        if (acceptableLeftPredicates.nonEmpty)
          context.staticComponents.logicalPlanProducer.planSelection(exp1, acceptableLeftPredicates, context)
        else
          exp1

      // Update the plannerState with the labelInfo from the LHS
      val updatedContext = context.withModifiedPlannerState(
        _.withUpdatedLabelInfo(left, context.staticComponents.planningAttributes.solveds)
      )

      val exp2PR = getPatternRelationshipFromExpand(exp2, qg)
      val right =
        planRhs(
          updatedContext,
          exp1.relName,
          exp2,
          exp2PR,
          left.id,
          exp2WithSelections.predicatesOnMainBeforeRBP,
          qg.hints
        )

      val triadicSelection = updatedContext.staticComponents.logicalPlanProducer.planTriadicSelection(
        positivePredicate,
        left,
        exp1.from,
        exp2.from,
        exp2.to,
        right,
        triadicPredicate,
        updatedContext
      )

      val possibleRemainingSelections = newRightPredicates ++
        exp1WithSelections.predicatesOnShards ++ exp1WithSelections.predicatesOnMainAfterRBP ++
        exp2WithSelections.predicatesOnShards ++ exp2WithSelections.predicatesOnMainAfterRBP

      val newPlan =
        if (possibleRemainingSelections.nonEmpty) {
          updatedContext.staticComponents.logicalPlanProducer
            .planSelection(triadicSelection, possibleRemainingSelections, updatedContext)
        } else
          triadicSelection

      Seq(newPlan)
    } else
      Seq.empty
  }

  private def getPatternRelationshipFromExpand(exp: Expand, qg: QueryGraph): PatternRelationship = {
    val from = exp.from
    val to = exp.to
    qg.patternRelationships.find {
      case PatternRelationship(_, (`from`, `to`), _, _, _) => true
      case PatternRelationship(_, (`to`, `from`), _, _, _) => true
      case _                                               => false
    }.get
  }

  // (.|.filter)  Possibly a filter
  // .|.expandAll(exp2)
  // .|.argument
  private def planRhs(
    context: LogicalPlanningContext,
    exp1RelName: LogicalVariable,
    exp2: Expand,
    exp2PatternRelationship: PatternRelationship,
    lhsId: Id,
    incomingPredicates: Seq[Expression],
    hints: Iterable[IrHint]
  ): LogicalPlan = {
    val argument = context.staticComponents.logicalPlanProducer.planArgument(
      patternNodes = Set(exp2.from),
      patternRels = Set(exp1RelName),
      other = Set.empty,
      context = context,
      previouslyCachedProperties = context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(lhsId)
    )
    val newExpand2 =
      context.staticComponents.logicalPlanProducer.planSimpleExpand(
        argument,
        exp2.from,
        exp2.to,
        exp2PatternRelationship,
        ExpandAll,
        context,
        hints
      )

    if (incomingPredicates.nonEmpty)
      context.staticComponents.logicalPlanProducer.planSelection(newExpand2, incomingPredicates, context)
    else
      newExpand2
  }

  private def leftPredicatesAcceptable(leftId: LogicalVariable, leftPredicate: Expression): Boolean =
    leftPredicate match {
      case HasLabels(v: Variable, Seq(_)) if v == leftId => true
      case _                                             => false
    }

  private def matchingLabels(
    positivePredicate: Boolean,
    node1: LogicalVariable,
    node2: LogicalVariable,
    qg: QueryGraph
  ): Boolean = {
    val labels1 = qg.selections.labelsOnNode(node1)
    val labels2 = qg.selections.labelsOnNode(node2)
    if (positivePredicate)
      labels1 == labels2
    else
      labels1.isEmpty || labels2.nonEmpty && (labels2 subsetOf labels1)
  }

  private def matchingIRExpression(
    pattern: ExistsIRExpression,
    from: LogicalVariable,
    to: LogicalVariable,
    types: Seq[RelTypeName],
    dir: SemanticDirection
  ): Boolean = pattern match {
    // (a)-[:X]->(c)
    case ExistsIRExpression(
        RegularSinglePlannerQuery(
          QueryGraph(
            SetExtractor(PatternRelationship(
              rel,
              (predicateFrom, predicateTo),
              predicateDir,
              predicateTypes,
              SimplePatternLength
            )),
            SetExtractor(),
            patternNodes,
            _,
            Selections.empty,
            SetExtractor(),
            SetExtractor(),
            SetExtractor(),
            IndexedSeq(),
            SetExtractor(),
            None
          ),
          InterestingOrder.empty,
          RegularQueryProjection(_, QueryPagination.empty, Selections.empty, _, _),
          None,
          None
        ),
        _,
        _
      )
      if patternNodes == Set(predicateFrom, predicateTo)
        && predicateFrom == from
        && predicateTo == to
        && predicateDir == dir
        && predicateTypes == types
        && !pattern.dependencies.contains(rel) => true
    case _ => false
  }
}

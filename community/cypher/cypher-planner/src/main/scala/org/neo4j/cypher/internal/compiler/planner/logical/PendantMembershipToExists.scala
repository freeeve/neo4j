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

import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.compiler.phases.CompilationContains
import org.neo4j.cypher.internal.compiler.phases.LogicalPlanState
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.UnPositionedVariable.varFor
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerConfig
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.ir.AggregatingQueryProjection
import org.neo4j.cypher.internal.ir.DistinctQueryProjection
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.QueryHorizon
import org.neo4j.cypher.internal.ir.RegularQueryProjection
import org.neo4j.cypher.internal.ir.RegularSinglePlannerQuery
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.ir.SimplePatternLength
import org.neo4j.cypher.internal.ir.ast.ExistsIRExpression
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.DefaultPostCondition
import org.neo4j.cypher.internal.util.topDown

/**
 * Demotes a "membership" pattern -- an anonymous, single-row-selective pendant node hanging off the
 * main pattern -- into an existential subquery, so the planner cannot pick it as the leaf the whole
 * component is anchored on.
 *
 * {{{
 *   MATCH (f)-[:HAS_CREATOR]->(post)-[:HAS_TAG]->(:Tag {name: $seed})       (f bound from a WITH)
 *   MATCH (post)-[:HAS_TAG]->(other:Tag)
 *   RETURN other.name, count(DISTINCT post)
 *   =>
 *   MATCH (f)-[:HAS_CREATOR]->(post)-[:HAS_TAG]->(other:Tag)
 *   WHERE EXISTS { (post)-[:HAS_TAG]->(:Tag {name: $seed}) }
 *   RETURN other.name, count(DISTINCT post)
 * }}}
 *
 * The seed `(:Tag {name})` is seekable to a single node, but a *high-degree* one (a popular tag has
 * orders of magnitude more incoming `:HAS_TAG` than the average). The cost model estimates expansions
 * from it using the *average* degree, so it cannot see the hub and happily anchors the component on
 * the seed, fanning out across every post that carries it. Bound `f` is the cheap anchor the query
 * intends to drive from. Rewriting the seed to `EXISTS { ... }` removes it from the set of leaf
 * candidates, so the component is anchored on `f` and the seed becomes a per-row membership check
 * (a `SemiApply`).
 *
 * This is the bottleneck in LDBC SNB IC6 ("tag co-occurrence"): anchored on the seed tag the plan is
 * ~`friends * posts-with-seed-tag`; anchored on the bound friend it is ~`friends * posts-per-friend`,
 * which is the difference between a query that times out and one that returns in about a second.
 *
 * == Soundness ==
 * The membership node and its relationship are anonymous and used nowhere else, so removing them from
 * the pattern changes nothing about the surviving rows except their multiplicity: a `post` linked to
 * the seed by `k` relationships contributes `k` rows to the join but is either kept or dropped once by
 * the existential. The rewrite therefore only fires when the immediately-following horizon is
 * insensitive to that multiplicity -- a `DISTINCT` projection, or an aggregation whose every aggregating
 * function is `DISTINCT` (`count(DISTINCT post)` in IC6). This mirrors [[OptionalMatchRemover]]'s
 * `validAggregations` guard and needs neither a uniqueness constraint nor the absence of parallel
 * relationships to be correct.
 *
 * == Benefit guard ==
 * The seed is only a bad anchor when the component has a *better* one. The rewrite fires only when the
 * node the membership hangs off is connected -- through the remaining pattern -- to a bound argument
 * variable (`f`, imported from a preceding `WITH`). With such an anchor present, demoting the seed can
 * only help; with none (e.g. a standalone `MATCH (p)-[:LIVES_IN]->(:City {name: 'Paris'}) RETURN p`,
 * where seeking the single city is exactly right) the rewrite leaves the pattern alone.
 */
case object PendantMembershipToExists extends PlannerQueryRewriter
    with StepSequencer.Step
    with DefaultPostCondition
    with PlanPipelineTransformerFactory {

  private val stringifier = ExpressionStringifier(_.asCanonicalStringVal)

  override def preConditions: Set[StepSequencer.Condition] = Set(
    // This works on the IR
    CompilationContains[PlannerQuery](),
    // Let the getDegree rewriter claim the pure-existence patterns it handles first.
    GetDegreeRewriterStep.completed
  )

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def getTransformer(planPipelineConfig: PlanPipelineTransformerConfig)
    : Transformer[PlannerContext, LogicalPlanState, LogicalPlanState] = this

  override def instance(from: LogicalPlanState, context: PlannerContext): Rewriter =
    topDown(
      rewriter = Rewriter.lift {
        case rsq @ RegularSinglePlannerQuery(qg, _, horizon, _, _) if collapsesDuplicates(horizon) =>
          rewriteQueryGraph(qg, horizon, from.anonymousVariableNameGenerator) match {
            case `qg`  => rsq
            case newQg => rsq.withQueryGraph(newQg)
          }
      },
      cancellation = context.cancellationChecker
    )

  /**
   * A horizon is insensitive to pure row-duplication when it is a `DISTINCT` projection, or an
   * aggregation whose every aggregating function is `DISTINCT`. Duplicated rows agree on every grouping
   * key (the membership node is not read by the horizon), so they fall in the same group, and a
   * `DISTINCT` aggregate depends only on the set -- not the multiplicity -- of values within it.
   */
  private def collapsesDuplicates(horizon: QueryHorizon): Boolean = horizon match {
    case _: DistinctQueryProjection => true
    case agg: AggregatingQueryProjection =>
      agg.aggregationExpressions.values.forall {
        case fi: FunctionInvocation => fi.distinct
        case _                      => false
      }
    case _ => false
  }

  /**
   * Repeatedly demote eligible membership patterns until none remain (a single query graph may have
   * several). Each demotion removes a pattern relationship, so this terminates.
   */
  private def rewriteQueryGraph(
    qg: QueryGraph,
    horizon: QueryHorizon,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator
  ): QueryGraph = {
    val protectedVars = horizon.dependingExpressions.flatMap(_.dependencies).toSet
    findCandidate(qg, protectedVars) match {
      case Some(candidate) =>
        rewriteQueryGraph(
          demote(qg, candidate, anonymousVariableNameGenerator),
          horizon,
          anonymousVariableNameGenerator
        )
      case None => qg
    }
  }

  /**
   * The membership node `node` hangs off `attach` through the single relationship `pattern`; the
   * predicates on the node (its label and the seekable property) are `innerPredicates`.
   */
  private case class Candidate(
    pattern: PatternRelationship,
    node: LogicalVariable,
    attach: LogicalVariable,
    innerPredicates: Set[Predicate]
  )

  private def findCandidate(qg: QueryGraph, protectedVars: Set[LogicalVariable]): Option[Candidate] =
    qg.patternRelationships.iterator.flatMap(candidateFor(_, qg, protectedVars)).nextOption()

  private def candidateFor(
    pattern: PatternRelationship,
    qg: QueryGraph,
    protectedVars: Set[LogicalVariable]
  ): Option[Candidate] = {
    if (pattern.length != SimplePatternLength || pattern.selfLoop) return None
    if (!isLocalAnonymous(pattern.variable, pattern, qg, protectedVars)) return None

    Seq((pattern.left, pattern.right), (pattern.right, pattern.left)).iterator.flatMap { case (node, attach) =>
      val coveredIds = pattern.coveredIds
      val innerPredicates = qg.selections.predicates.filter(p =>
        p.dependencies.contains(node) || p.dependencies.contains(pattern.variable)
      )
      val eligible =
        isMembershipNode(node, pattern, qg, protectedVars) &&
          // every predicate touching the node/relationship stays inside the pattern's own variables,
          // so nothing outside the membership depends on them
          innerPredicates.forall(_.dependencies.subsetOf(coveredIds)) &&
          // a seekable predicate (beyond the bare label) is what makes the node a single-row anchor
          innerPredicates.exists(p => p.dependencies.contains(node) && !p.expr.isInstanceOf[HasLabels]) &&
          attachReachesArgument(attach, pattern, qg)

      if (eligible) Some(Candidate(pattern, node, attach, innerPredicates)) else None
    }.nextOption()
  }

  /**
   * A membership node is anonymous, not an argument, appears in no node connection but `pattern`, and is
   * not read by the horizon (`protectedVars`). Those together mean dropping it can only affect row
   * multiplicity, never which surviving rows or values are produced.
   */
  private def isMembershipNode(
    node: LogicalVariable,
    pattern: PatternRelationship,
    qg: QueryGraph,
    protectedVars: Set[LogicalVariable]
  ): Boolean =
    AnonymousVariableNameGenerator.notNamed(node.name) &&
      !qg.argumentIds.contains(node) &&
      !protectedVars.contains(node) &&
      qg.nodeConnections.count(_.coveredIds.contains(node)) == 1

  /**
   * The relationship variable is anonymous, appears in no node connection but `pattern`, is not read by
   * the horizon, and is referenced by no predicate other than those that move into the subquery. The
   * last check is what keeps the rewrite away from relationship-uniqueness predicates (`r <> r2`): if
   * the relationship is constrained against another, it is not purely local and the rewrite backs off.
   */
  private def isLocalAnonymous(
    relationship: LogicalVariable,
    pattern: PatternRelationship,
    qg: QueryGraph,
    protectedVars: Set[LogicalVariable]
  ): Boolean =
    AnonymousVariableNameGenerator.notNamed(relationship.name) &&
      !protectedVars.contains(relationship) &&
      qg.nodeConnections.count(_.coveredIds.contains(relationship)) == 1

  /**
   * True when, through the pattern with the membership relationship removed, `attach` is connected to a
   * bound argument node -- the cheaper anchor that makes demoting the membership worthwhile.
   */
  private def attachReachesArgument(
    attach: LogicalVariable,
    pattern: PatternRelationship,
    qg: QueryGraph
  ): Boolean = {
    val arguments = qg.patternNodes intersect qg.argumentIds
    arguments.nonEmpty && {
      val remaining = qg.patternRelationships - pattern
      var reached = Set(attach)
      var frontier = Set(attach)
      while (frontier.nonEmpty) {
        val next = remaining.collect {
          case r if frontier.contains(r.left)  => r.right
          case r if frontier.contains(r.right) => r.left
        }.diff(reached)
        reached ++= next
        frontier = next
      }
      reached.intersect(arguments).nonEmpty
    }
  }

  /**
   * Remove the membership node, its relationship and its predicates from the query graph, and add the
   * equivalent existential subquery as a predicate on `attach`. Modelled on
   * [[OptionalMatchRemover]]'s pattern-to-`ExistsIRExpression` conversion.
   */
  private def demote(
    qg: QueryGraph,
    candidate: Candidate,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator
  ): QueryGraph = {
    val Candidate(pattern, node, attach, innerPredicates) = candidate
    qg.removePatternRelationship(pattern)
      .withPatternNodes(qg.patternNodes - node)
      .removePredicates(innerPredicates)
      .addPredicates(toExists(pattern, attach, innerPredicates, anonymousVariableNameGenerator))
  }

  private def toExists(
    pattern: PatternRelationship,
    attach: LogicalVariable,
    innerPredicates: Set[Predicate],
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator
  ): ExistsIRExpression = {
    val arguments = Set(attach)
    val innerExpressions = innerPredicates.map(_.expr)
    val query = RegularSinglePlannerQuery(
      queryGraph = QueryGraph(
        argumentIds = arguments,
        patternNodes = pattern.nodes,
        patternRelationships = Set(pattern),
        selections = Selections.from(innerExpressions)
      ),
      horizon = RegularQueryProjection(importedExposedSymbols = arguments)
    )

    val description = innerExpressions.toSeq match {
      case Seq() =>
        s"EXISTS { MATCH ${pattern.solvedString(withTypes = true)} }"
      case Seq(single) =>
        s"""EXISTS {
           |  MATCH ${pattern.solvedString(withTypes = true)}
           |    WHERE ${stringifier(single)}
           |}""".stripMargin
      case many =>
        s"""EXISTS {
           |  MATCH ${pattern.solvedString(withTypes = true)}
           |    WHERE ${stringifier(Ands(many.toSet)(InputPosition.NONE))}
           |}""".stripMargin
    }

    ExistsIRExpression(query, varFor(anonymousVariableNameGenerator.nextName), description)(
      InputPosition.NONE,
      None,
      Some(arguments)
    )
  }
}

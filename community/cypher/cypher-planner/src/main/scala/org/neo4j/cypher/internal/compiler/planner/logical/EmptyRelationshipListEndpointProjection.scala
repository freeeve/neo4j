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

import org.neo4j.cypher.internal.compiler.phases.CompilationContains
import org.neo4j.cypher.internal.compiler.phases.LogicalPlanState
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.UnPositionedVariable.varFor
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerConfig
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.ir.PatternLength
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.ir.SimplePatternLength
import org.neo4j.cypher.internal.ir.VarPatternLength
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.AddElementUniquenessPredicates
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.DefaultPostCondition
import org.neo4j.cypher.internal.util.topDown

/**
 * Rename repeated var-length relationships if they cannot get solved by ProjectEndpoints.
 */
case object EmptyRelationshipListEndpointProjection extends PlannerQueryRewriter with StepSequencer.Step
    with DefaultPostCondition
    with PlanPipelineTransformerFactory {

  override def instance(from: LogicalPlanState, context: PlannerContext): Rewriter = {
    topDown(
      rewriter = Rewriter.lift {
        case qg: QueryGraph =>
          val rels = qg.patternRelationships.toSeq
          val relGroupNames = qg.quantifiedPathPatterns.flatMap(_.relationshipVariableGroupings).map(_.group)

          val (newRels, predicates) = rels
            .zipWithIndex.map {
              // Cases where the relationship is repeated, but across multiple query graphs.
              case (rel @ PatternRelationship(name, nodes, _, _, length), _)
                if relationshipQualifies(qg, nodes, length) &&
                  // Where the relationship is an argument
                  qg.argumentIds.contains(name) =>
                copyRelWithPredicates(from, rel, name, qg.selections)

              // Cases where a legacy var-length relationship has the same name as a relationship group variable
              case (rel @ PatternRelationship(name, _, _, _, _: VarPatternLength), _)
                if relGroupNames.contains(rel.variable) =>
                copyRelWithPredicates(from, rel, name, qg.selections)

              // Cases where the relationship is repeated in the same query graph.
              case (rel @ PatternRelationship(name, nodes, _, _, length), i)
                if relationshipQualifies(qg, nodes, length) =>
                // Where the relationship is defined more than once in this query graph.
                // We look in the remainder of the sortedRels list to find a copy with the same name.
                rels.drop(i + 1).find(_.variable == name) match {
                  // And where no node is shared between the 2 occurences of the relationships.
                  case Some(sameRel) if !atLeastOneSharedNode(rel.boundaryNodes, sameRel.boundaryNodes) =>
                    copyRelWithPredicates(from, rel, name, qg.selections)
                  case _ => (rel, None)
                }

              case (rel, _) =>
                (rel, None)
            }.unzip

          qg.withPatternRelationships(newRels.toSet)
            .withSelections(qg.selections ++ predicates.flatten)
      },
      cancellation = context.cancellationChecker
    )
  }

  private def atLeastOneSharedNode(
    firstNodes: (LogicalVariable, LogicalVariable),
    secondNodes: (LogicalVariable, LogicalVariable)
  ): Boolean = {
    firstNodes._1 == secondNodes._1 ||
    firstNodes._1 == secondNodes._2 ||
    firstNodes._2 == secondNodes._1 ||
    firstNodes._2 == secondNodes._2
  }

  private def relationshipQualifies(
    qg: QueryGraph,
    nodes: (LogicalVariable, LogicalVariable),
    length: PatternLength
  ) = {
    // Var length rels that include length 0
    (length match {
      case SimplePatternLength      => false
      case VarPatternLength(min, _) => min == 0
    }) &&
    // Where no node is an argument
    !qg.argumentIds.contains(nodes._1) &&
    !qg.argumentIds.contains(nodes._2)
  }

  /**
   * Return a copy of the var-length relationship with a new name.
   * Also return an Equals predicate asserting that the original relationship and the copy are equal.
   * If the original relationship had a Unique predicate (i.e. it has Trail semantics),
   * then a Unique predicate is also added for the new relationship copy.
   */
  private def copyRelWithPredicates(
    from: LogicalPlanState,
    rel: PatternRelationship,
    variable: LogicalVariable,
    queryGraphSelections: Selections
  ): (PatternRelationship, Seq[Expression]) = {
    val newVariable = varFor(from.anonymousVariableNameGenerator.nextName)
    val relCopy = rel.copy(variable = newVariable)
    val equalsPredicate = Equals(
      variable,
      relCopy.variable
    )(InputPosition.NONE)

    // Get the Unique predicate for 'variable' if it is present in the query
    val maybeUniquePred = queryGraphSelections.predicates.collectFirst {
      case Predicate(_, uniqueExpr @ Unique(v)) if v == variable => Unique(newVariable)(uniqueExpr.position)
    }

    // If a Unique predicate was available, return it together with the equality predicate.
    // Otherwise, return only the equality predicate.
    (
      relCopy,
      maybeUniquePred
        .map(uniqueExpr => Seq(equalsPredicate, uniqueExpr))
        .getOrElse(Seq(equalsPredicate))
    )
  }

  override def preConditions: Set[StepSequencer.Condition] = Set(
    // This works on the IR
    CompilationContains[PlannerQuery](),
    VarLengthQuantifierMerger.completed,
    AddElementUniquenessPredicates.completed
  )

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def getTransformer(planPipelineConfig: PlanPipelineTransformerConfig)
    : Transformer[PlannerContext, LogicalPlanState, LogicalPlanState] = this
}

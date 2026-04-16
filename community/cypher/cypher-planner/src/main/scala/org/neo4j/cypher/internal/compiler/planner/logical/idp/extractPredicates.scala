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
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import org.neo4j.cypher.internal.compiler.planner.logical.idp.expandSolverStep.VariableList
import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.AllRelationships
import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.NoRelationships
import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.NodesFunctionArguments
import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.RelationshipsFunctionArguments
import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.expressions.AllReducePredicate
import org.neo4j.cypher.internal.expressions.AllReduceSingletonPredicate
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FilterScope
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.IsRepeatAcyclic
import org.neo4j.cypher.internal.expressions.IsRepeatTrailUnique
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.MultiRelationshipPathStep
import org.neo4j.cypher.internal.expressions.NilPathStep
import org.neo4j.cypher.internal.expressions.NodePathStep
import org.neo4j.cypher.internal.expressions.NoneIterablePredicate
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.PathExpression
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.expressions.UniqueNodes
import org.neo4j.cypher.internal.expressions.VarLengthLowerBound
import org.neo4j.cypher.internal.expressions.VarLengthUpperBound
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.VariableGrouping
import org.neo4j.cypher.internal.ir.VarPatternLength
import org.neo4j.cypher.internal.ir.ast.ForAllRepetitions
import org.neo4j.cypher.internal.logical.plans.Expand.VariablePredicate
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.collection.immutable.ListSet

object extractPredicates {

  // Using type predicates to make this more readable.
  type NodePredicates = ListSet[VariablePredicate]
  type RelationshipPredicates = ListSet[VariablePredicate]
  type SolvedPredicates = ListSet[Expression] // for marking predicates as solved

  def apply(
    availablePredicates: collection.Seq[Expression],
    originalRelationship: LogicalVariable,
    originalNode: LogicalVariable,
    targetNode: LogicalVariable,
    targetNodeIsBound: Boolean,
    varLength: VarPatternLength
  ): (NodePredicates, RelationshipPredicates, SolvedPredicates) = {

    /*
    We extract predicates that we can evaluate eagerly during the traversal, which allows us to abort traversing
    down paths that would not match. To make it easy to evaluate these predicates, we rewrite them a little bit so
    a single slot can be used for all predicates against a relationship (similarly done for nodes)

    During the folding, we also accumulate the original predicate, which we can mark as solved by this plan.
     */
    val seed: (NodePredicates, RelationshipPredicates, SolvedPredicates) =
      (ListSet.empty, ListSet.empty, ListSet.empty)

    /**
     * Checks if an inner predicate depends on the path (i.e. the relationship or end node). In that case
     * we cannot solve the predicates during the traversal. Unless the target node is bound.
     */
    def pathDependent(innerPredicate: Expression) = {
      val dependencies = innerPredicate.dependencies
      dependencies.contains(originalRelationship) || (dependencies.contains(targetNode) && !targetNodeIsBound)
    }

    availablePredicates.foldLeft(seed) {

      // MATCH ()-[r* {prop:1337}]->()
      case ((n, e, s), p @ AllRelationships(variable, `originalRelationship`, innerPredicate))
        if targetNodeIsBound || !innerPredicate.dependencies.contains(targetNode) =>
        val predicate = VariablePredicate(variable, innerPredicate)
        (n, e + predicate, s + p)

      // MATCH (a)-[rs*]-(b) WHERE NONE(r IN rs WHERE r.prop=4)
      case ((n, e, s), p @ NoRelationships(variable, `originalRelationship`, innerPredicate))
        if targetNodeIsBound || !innerPredicate.dependencies.contains(targetNode) =>
        val predicate = VariablePredicate(variable, Not(innerPredicate)(innerPredicate.position))
        (n, e + predicate, s + p)

      // MATCH p = (a)-[x*]->(b) WHERE ALL(r in relationships(p) WHERE r.prop > 5)
      case (
          (n, e, s),
          p @ AllRelationshipsInPath(`originalNode`, `originalRelationship`, variable, innerPredicate)
        ) if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, innerPredicate)
        (n, e + predicate, s + p)

      // MATCH p = ()-[*]->() WHERE NONE(r in relationships(p) WHERE <innerPredicate>)
      case (
          (n, e, s),
          p @ NoRelationshipInPath(`originalNode`, `originalRelationship`, variable, innerPredicate)
        ) if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, Not(innerPredicate)(innerPredicate.position))
        (n, e + predicate, s + p)

      // MATCH p = ()-[*]->() WHERE ALL(r in nodes(p) WHERE <innerPredicate>)
      case ((n, e, s), p @ AllNodesInPath(`originalNode`, `originalRelationship`, variable, innerPredicate))
        if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, innerPredicate)
        (n + predicate, e, s + p)

      // MATCH p = ()-[*]->() WHERE NONE(r in nodes(p) WHERE <innerPredicate>)
      case ((n, e, s), p @ NoNodeInPath(`originalNode`, `originalRelationship`, variable, innerPredicate))
        if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, Not(innerPredicate)(innerPredicate.position))
        (n + predicate, e, s + p)

      // Inserted by AddUniquenessPredicates
      case ((n, e, s), p @ Unique(`originalRelationship`)) =>
        (n, e, s + p)

      // Inserted by AddVarLengthPredicates. We solve these predicates, iff the var-length expand we are about to plan is more restrictive
      case (
          (n, e, s),
          p @ VarLengthLowerBound(
            `originalRelationship`,
            predicateLowerBound
          )
        ) if predicateLowerBound <= varLength.min =>
        (n, e, s + p)
      case (
          (n, e, s),
          p @ VarLengthUpperBound(
            `originalRelationship`,
            predicateUpperBound
          )
        ) if predicateUpperBound >= varLength.max.getOrElse(Int.MaxValue) =>
        (n, e, s + p)

      case (acc, _) =>
        acc
    }
  }

  object AllRelationships {

    def unapply(v: Any): Option[(LogicalVariable, LogicalVariable, Expression)] =
      v match {
        case AllIterablePredicate(FilterScope(variable, Some(innerPredicate)), relId: LogicalVariable)
          if variable == relId || !innerPredicate.dependencies(relId) =>
          Some((variable, relId, innerPredicate))

        case _ => None
      }
  }

  object NoRelationships {

    def unapply(v: Any): Option[(LogicalVariable, LogicalVariable, Expression)] =
      v match {
        case NoneIterablePredicate(FilterScope(variable, Some(innerPredicate)), relId: LogicalVariable)
          if variable == relId || !innerPredicate.dependencies(relId) =>
          Some((variable, relId, innerPredicate))

        case _ => None
      }
  }

  private object AllRelationshipsInPath {

    def unapply(v: Any): Option[(LogicalVariable, LogicalVariable, LogicalVariable, Expression)] =
      v match {
        case AllIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            RelationshipsFunctionArguments(PathExpression(
              NodePathStep(
                startNode: LogicalVariable,
                MultiRelationshipPathStep(rel: LogicalVariable, _, _, NilPathStep())
              )
            ))
          ) =>
          Some((startNode, rel, variable, innerPredicate))

        case _ => None
      }
  }

  private object AllNodesInPath {

    def unapply(v: Any): Option[(LogicalVariable, LogicalVariable, LogicalVariable, Expression)] =
      v match {
        case AllIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            NodesFunctionArguments(
              PathExpression(
                NodePathStep(
                  startNode: LogicalVariable,
                  MultiRelationshipPathStep(rel: LogicalVariable, _, _, NilPathStep())
                )
              )
            )
          ) =>
          Some((startNode, rel, variable, innerPredicate))

        case _ => None
      }
  }

  private object NoRelationshipInPath {

    def unapply(v: Any): Option[(LogicalVariable, LogicalVariable, LogicalVariable, Expression)] =
      v match {
        case NoneIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            RelationshipsFunctionArguments(
              PathExpression(
                NodePathStep(
                  startNode: LogicalVariable,
                  MultiRelationshipPathStep(rel: LogicalVariable, _, _, NilPathStep())
                )
              )
            )
          ) =>
          Some((startNode, rel, variable, innerPredicate))

        case _ => None
      }
  }

  private object NoNodeInPath {

    def unapply(v: Any): Option[(LogicalVariable, LogicalVariable, LogicalVariable, Expression)] =
      v match {
        case NoneIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            NodesFunctionArguments(
              PathExpression(
                NodePathStep(
                  startNode: LogicalVariable,
                  MultiRelationshipPathStep(rel: LogicalVariable, _, _, NilPathStep())
                )
              )
            )
          ) =>
          Some((startNode, rel, variable, innerPredicate))

        case _ => None
      }
  }

  object NodesFunctionArguments {

    def unapplySeq(f: FunctionInvocation): Option[IndexedSeq[Expression]] = f match {
      case FunctionInvocation(FunctionName(_, fname), false, args, _, _, _)
        if fname.equalsIgnoreCase("nodes") => Some(args)
      case _ => None
    }
  }

  object RelationshipsFunctionArguments {

    def unapplySeq(f: FunctionInvocation): Option[IndexedSeq[Expression]] = f match {
      case FunctionInvocation(FunctionName(_, fname), false, args, _, _, _)
        if fname.equalsIgnoreCase("relationships") => Some(args)
      case _ => None
    }
  }
}

object extractShortestPathPredicates {

  import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.NodePredicates
  import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.RelationshipPredicates
  import org.neo4j.cypher.internal.compiler.planner.logical.idp.extractPredicates.SolvedPredicates

  def apply(
    availablePredicates: Set[Expression],
    path: Option[LogicalVariable],
    rels: Option[LogicalVariable]
  ): (NodePredicates, RelationshipPredicates, SolvedPredicates) = {

    /*
    We extract predicates that we can evaluate eagerly during the traversal, which allows us to abort traversing
    down paths that would not match. To make it easy to evaluate these predicates, we rewrite them a little bit so
    a single slot can be used for all predicates against a relationship (similarly done for nodes)

    During the folding, we also accumulate the original predicate, which we can mark as solved by this plan.
     */
    val seed: (NodePredicates, RelationshipPredicates, SolvedPredicates) =
      (ListSet.empty, ListSet.empty, ListSet.empty)

    /**
     * Checks if an inner predicate depends on the path (i.e. the relationship or end node). In that case
     * we cannot solve the predicates during the traversal.
     */
    def pathDependent(innerPredicate: Expression) = {
      val dependencies = innerPredicate.dependencies
      dependencies.intersect(path.toSet ++ rels.toSet).nonEmpty
    }

    availablePredicates.foldLeft(seed) {
      // MATCH p=shortestPath((a)-[rs*]-(b)) WHERE all(r IN rs WHERE r.prop = 2)
      case ((n, e, s), p @ AllRelationships(variable, relationships, innerPredicate))
        if !pathDependent(innerPredicate) && rels.contains(relationships) =>
        val predicate = VariablePredicate(variable, innerPredicate)
        (n, e + predicate, s + p)

      // MATCH p=shortestPath((a)-[rs*]-(b)) WHERE NONE(r IN rs WHERE r.prop = 2)
      case ((n, e, s), p @ NoRelationships(variable, relationships, innerPredicate))
        if !pathDependent(innerPredicate) && rels.contains(relationships) =>
        val predicate = VariablePredicate(variable, Not(innerPredicate)(innerPredicate.position))
        (n, e + predicate, s + p)

      // MATCH p = shortestPath((a)-[x*]->(b)) WHERE ALL(r in relationships(p) WHERE r.prop > 5)
      case (
          (n, e, s),
          p @ AllRelationshipsInNamedPath(`path`, variable, innerPredicate)
        ) if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, innerPredicate)
        (n, e + predicate, s + p)

      // MATCH p = shortestPath((a)-[*]->(b)) WHERE NONE(r in relationships(p) WHERE <innerPredicate>)
      case (
          (n, e, s),
          p @ NoRelationshipInNamedPath(`path`, variable, innerPredicate)
        ) if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, Not(innerPredicate)(innerPredicate.position))
        (n, e + predicate, s + p)

      // MATCH p = shortestPath((a)-[*]->(b)) WHERE ALL(r in nodes(p) WHERE <innerPredicate>)
      case ((n, e, s), p @ AllNodesInNamedPath(`path`, variable, innerPredicate))
        if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, innerPredicate)
        (n + predicate, e, s + p)

      // MATCH p = shortestPath((a)-[*]->(b)) WHERE NONE(r in nodes(p) WHERE <innerPredicate>)
      case ((n, e, s), p @ NoNodeInNamedPath(`path`, variable, innerPredicate)) if !pathDependent(innerPredicate) =>
        val predicate = VariablePredicate(variable, Not(innerPredicate)(innerPredicate.position))
        (n + predicate, e, s + p)

      case (acc, _) =>
        acc
    }
  }

  private object AllRelationshipsInNamedPath {

    def unapply(v: Any): Option[(Option[LogicalVariable], LogicalVariable, Expression)] =
      v match {
        case AllIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            RelationshipsFunctionArguments(path: Variable)
          ) =>
          Some((Some(path), variable, innerPredicate))

        case _ => None
      }
  }

  private object AllNodesInNamedPath {

    def unapply(v: Any): Option[(Option[LogicalVariable], LogicalVariable, Expression)] =
      v match {
        case AllIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            NodesFunctionArguments(path: Variable)
          ) =>
          Some((Some(path), variable, innerPredicate))

        case _ => None
      }
  }

  private object NoRelationshipInNamedPath {

    def unapply(v: Any): Option[(Option[LogicalVariable], LogicalVariable, Expression)] =
      v match {
        case NoneIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            RelationshipsFunctionArguments(path: Variable)
          ) =>
          Some((Some(path), variable, innerPredicate))

        case _ => None
      }
  }

  private object NoNodeInNamedPath {

    def unapply(v: Any): Option[(Option[LogicalVariable], LogicalVariable, Expression)] =
      v match {
        case NoneIterablePredicate(
            FilterScope(variable, Some(innerPredicate)),
            NodesFunctionArguments(path: Variable)
          ) =>
          Some((Some(path), variable, innerPredicate))

        case _ => None
      }
  }

}

/**
 * During MoveQuantifiedPathPatternPredicates we move inner QPP predicates from their pre-filter position to their post-filter
 * position. [[extractQppPredicates]] works for QPPs as [[extractPredicates]] works for var-length relationships.
 * For any of these post-filter predicates that could not be solved up to this point, the goal is to move as many of
 * these post-filter predicates back to their pre-filter positions and solve them during the planning of the inner QPP.
 * Planning these post-filter predicates in the inner QPP plan is much faster as it can potentially short-circuit the
 * expansion of the QPP.
 */
object extractQppPredicates {

  /**
   * @param insideRepeat whether the pre-filter position is on the RHS of a Repeat.
   *                     Thus, whether Unique may be extracted into an IsRepeatTrailUnique.
   * @param repeatStartNode the startNode of Repeat. This can be None when `insideRepeat` = false
   */
  def apply(
    predicates: Seq[Expression],
    availableLocalSymbols: Set[VariableGrouping],
    availableNonLocalSymbols: Set[LogicalVariable],
    insideRepeat: Boolean,
    repeatStartNode: Option[LogicalVariable]
  ): ExtractedPredicates = {
    val solvables = filterSolvablePredicates(predicates, availableLocalSymbols, availableNonLocalSymbols)
    val extracted = getExtractablePredicates(solvables, availableLocalSymbols, insideRepeat, repeatStartNode)
    val requiredSymbols = getRequiredNonLocalSymbols(extracted, availableLocalSymbols)
    ExtractedPredicates(requiredSymbols, extracted)
  }

  /**
   * Unsolvable predicates are predicates that cannot be solved due to missing dependencies.
   *
   * @param predicates                Potentially solvable predicates
   * @param availableNonLocalSymbols  Non-local symbols previously bound that can be used by the predicates
   * @param availableLocalSymbols     Local symbols bound during the inner QPP that can also be used by the predicates
   * @return                          List of predicates that can be solved
   */
  private def filterSolvablePredicates(
    predicates: Seq[Expression],
    availableLocalSymbols: Set[VariableGrouping],
    availableNonLocalSymbols: Set[LogicalVariable]
  ): Seq[Expression] = {
    val availableLocalGroupNames = availableLocalSymbols.map(_.group)
    val availableSymbols = availableLocalGroupNames ++ availableNonLocalSymbols
    predicates.filter(_.dependencies.subsetOf(availableSymbols))
  }

  /**
   * Extracted predicates are predicates that were previously normalized by MoveQuantifiedPathPatternPredicates to their post-filter
   * form and that we now want to convert back to their pre-filter form.
   *
   * @param predicates            Potentially extractable predicates
   * @param availableLocalSymbols Local symbol mappings used for swapping variable references in extracted predicates
   * @param insideRepeat          Whether the predicates will be used in the RHS of a Repeat
   * @param startNode             The inner group variable of the QPP from where processing starts
   * @return                      Extracted predicates
   */
  private def getExtractablePredicates(
    predicates: Seq[Expression],
    availableLocalSymbols: Set[VariableGrouping],
    insideRepeat: Boolean,
    startNode: Option[LogicalVariable]
  ): Seq[ExtractedPredicate] = {
    val availableLocalSymbolsMapping = availableLocalSymbols
      .map(g => g.group -> g.singleton)
      .toMap

    predicates.collect {
      case original @ AllIterablePredicate(FilterScope(iterator, Some(predicate)), groupVariable: LogicalVariable)
        if availableLocalSymbolsMapping.contains(groupVariable) &&
          availableLocalSymbolsMapping.keySet.intersect(predicate.dependencies).isEmpty =>
        val singletonVariable = availableLocalSymbolsMapping(groupVariable)
        val extracted = predicate.replaceAllOccurrencesBy(iterator, singletonVariable)
        ExtractedPredicate(original, extracted)

      case far: ForAllRepetitions
        // only extract if this predicate is actually on this QPP
        if availableLocalSymbolsMapping.contains(far.groupVariableAnchor) =>
        ExtractedPredicate(far, far.originalInnerPredicate)

      case unique @ Unique(VariableList(variables))
        if insideRepeat && variables.forall(availableLocalSymbolsMapping.contains) =>
        val extractedPredicates: Set[Expression] = variables
          .map(availableLocalSymbolsMapping)
          .map(relVar =>
            IsRepeatTrailUnique(relVar.asInstanceOf[Variable])(InputPosition.NONE)
          )
        ExtractedPredicate(unique, Ands.create(extractedPredicates))

      case uniqueNodes @ UniqueNodes(VariableList(nodeVariables), maybeRelationshipVariables)
        if insideRepeat && nodeVariables.forall(availableLocalSymbolsMapping.contains) && startNode.nonEmpty =>
        val uniqueVariables = nodeVariables - startNode.get
        val extractedPredicates: Set[Expression] = uniqueVariables
          .map(availableLocalSymbolsMapping)
          .map(nodeVar =>
            IsRepeatAcyclic(nodeVar.asInstanceOf[Variable])(InputPosition.NONE)
          )
        val extractedRelationshipPredicates = maybeRelationshipVariables match {
          case Some(VariableList(relationshipVariables)) => relationshipVariables
              .map(availableLocalSymbolsMapping)
              .map(relVar =>
                IsRepeatTrailUnique(relVar.asInstanceOf[Variable])(InputPosition.NONE)
              )
          case _ => Seq.empty
        }
        ExtractedPredicate(uniqueNodes, Ands.create(extractedPredicates ++ extractedRelationshipPredicates))

      case allReduce @ AllReducePredicate(scope, _, listVariable: LogicalVariable)
        if availableLocalSymbolsMapping.contains(
          listVariable
        ) && !(allReduce.init.dependencies ++ allReduce.predicate.dependencies ++ allReduce.reductionStep.dependencies).exists(
          availableLocalSymbolsMapping.contains
        ) =>
        val singletonVariableInQpp = availableLocalSymbolsMapping(listVariable)
        val newReductionStep =
          scope.reductionStepScope
            .reductionStep
            .replaceAllOccurrencesBy(
              allReduce.reductionStepVariable,
              singletonVariableInQpp
            )
        val newPredicate =
          scope.reductionStepScope
            .predicate
            .replaceAllOccurrencesBy(
              allReduce.reductionStepVariable,
              singletonVariableInQpp
            )
        ExtractedPredicate(
          allReduce,
          AllReduceSingletonPredicate(
            scope.accumulator,
            newReductionStep,
            newPredicate
          )(allReduce.position)
        )
    }
  }

  /**
   * Required non-local symbols represent the subset of previously bound non-local symbols which our extracted
   * predicates have dependencies on.
   *
   * @param predicates            Extracted predicates which may dependencies on previously bound symbols
   * @param availableLocalSymbols Symbols during inner QPP which are therefor always available to the predicates
   * @return                      Subset of the previously bound non-local symbols which are required
   */
  private def getRequiredNonLocalSymbols(
    predicates: Seq[ExtractedPredicate],
    availableLocalSymbols: Set[VariableGrouping]
  ): Set[LogicalVariable] = {
    val availableLocalSingletons = availableLocalSymbols.map(_.singleton)
    val predicateDependencies = predicates.flatMap(_.extracted.dependencies).toSet
    val requiredDependencies = predicateDependencies -- availableLocalSingletons
    requiredDependencies
  }

  case class ExtractedPredicate(original: Expression, extracted: Expression)
  case class ExtractedPredicates(requiredSymbols: Set[LogicalVariable], predicates: Seq[ExtractedPredicate])

}

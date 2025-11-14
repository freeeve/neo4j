/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.rewriting.rewriters.astRewriters

import org.neo4j.cypher.internal.ast.Match
import org.neo4j.cypher.internal.ast.Merge
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.DifferentNodes
import org.neo4j.cypher.internal.expressions.DifferentRelationships
import org.neo4j.cypher.internal.expressions.Disjoint
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.False
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.MatchMode
import org.neo4j.cypher.internal.expressions.NonPrefixedPatternPart
import org.neo4j.cypher.internal.expressions.NoneOfRelationships
import org.neo4j.cypher.internal.expressions.ParenthesizedPath
import org.neo4j.cypher.internal.expressions.PathMode
import org.neo4j.cypher.internal.expressions.PathMode.Acyclic
import org.neo4j.cypher.internal.expressions.PathMode.Trail
import org.neo4j.cypher.internal.expressions.PathMode.Walk
import org.neo4j.cypher.internal.expressions.PathMode.effectivePathMode
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternPart
import org.neo4j.cypher.internal.expressions.PatternPart.SelectiveSelector
import org.neo4j.cypher.internal.expressions.PrefixedPatternPart
import org.neo4j.cypher.internal.expressions.QuantifiedPath
import org.neo4j.cypher.internal.expressions.Range
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.ScopeExpression
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.expressions.SymbolicName
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.label_expressions.LabelExpression.ColonConjunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression.ColonDisjunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Conjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Disjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.DynamicLeaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Leaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Negation
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Wildcard
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.AddElementUniquenessPredicates.getRelTypesToConsider
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.AddElementUniquenessPredicates.overlaps
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.RelationshipUniqueness.NodeConnection
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.RelationshipUniqueness.RelationshipGroup
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.RelationshipUniqueness.SingleRelationship
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildrenNewAccForSiblings
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.SeqSupport.RichSeq
import org.neo4j.cypher.internal.util.bottomUp

import scala.util.control.TailCalls
import scala.util.control.TailCalls.TailRec

case object AddElementUniquenessPredicates extends AddPathPredicates[NodeConnection] {

  override val rewriter: Rewriter = bottomUp(Rewriter.lift {
    case m @ Match(_, matchMode, pattern: Pattern, _, where, _) =>
      val acrossPredicates = matchMode match {
        case _: MatchMode.DifferentRelationships => getPredicatesAcrossPathPatterns(pattern)
        case _                                   => Seq.empty
      }
      val withinPredicates =
        pattern.patternParts
          .filter(!_.isSelective)
          .flatMap { p =>
            val mode = effectivePathMode(matchMode, p.pathMode)
            getPredicatesWithinPathPattern(p.part, mode)
          }
      val maybePredicate = (acrossPredicates ++ withinPredicates)
        .reduceOption(expressions.And(_, _)(m.position))
      val newWhere = Where.combineOrCreateBeforeCnf(where, maybePredicate)(m.position)
      // TODO we need to address different path modes here when implementing for variable-length patterns
      //  See PLAN-2338
      val newPattern =
        pattern.copy(
          pattern.patternParts.map { part =>
            part.endoRewrite(patternRewriter(effectivePathMode(matchMode, part.pathMode)))
          }
        )(pattern.position)
      m.copy(pattern = newPattern, where = newWhere)(m.position)

    // Merge only supports Trail path mode for its Match phase, so we only add relationship uniqueness predicates
    case m @ Merge(pattern: PatternPart, _, where) =>
      val nodeConnections = collectNodeConnections(pattern)
      val newWhere = withPredicates(m, nodeConnections, where)
      m.copy(where = newWhere)(m.position)
  })

  private def getPredicatesAcrossPathPatterns(pattern: Pattern.ForMatch): Seq[Expression] = {
    val relsByPathPatterns: Seq[Seq[NodeConnection]] = pattern.patternParts.map(collectNodeConnections(_))
    val pairs: Seq[(NodeConnection, NodeConnection)] =
      for {
        (relsInPathPattern, relsPerSubsequentPathPattern: Seq[Seq[NodeConnection]]) <- relsByPathPatterns.zipWithTail
        rel <- relsInPathPattern
        relsInSubsequentPathPattern <- relsPerSubsequentPathPattern
        otherRel <- relsInSubsequentPathPattern
      } yield (rel, otherRel)
    createInterRelUniquenessPredicates(pairs, pattern.position)
  }

  private def getPredicatesWithinPathPattern(
    // we cannot make this a PatternElement because it could be a
    // ShortestPathsPatternPart that we do not want to traverse into
    part: NonPrefixedPatternPart,
    pathMode: PathMode
  ): IterableOnce[Expression] = {
    pathMode match {
      case Acyclic() =>
        val containsGroupVariables =
          part.element.folder.treeExists {
            case _: QuantifiedPath                           => true
            case RelationshipPattern(_, _, Some(_), _, _, _) => true
          }
        if (containsGroupVariables) {
          throw new IllegalArgumentException(
            s"ACYCLIC path mode is not supported for patterns with variable-length relationships at ${part.position}"
          )
        }
        val nodeVariables = part.element.allSingletonNodeVariables
        nodeVariables
          .subsets(2)
          .map(_.toSeq)
          .map {
            case Seq(x, y) =>
              DifferentNodes(x.copyId, y.copyId)(part.position)
            case _ =>
              throw new IllegalStateException("Expected only pairs of node variables")
          }

      case Trail() =>
        val nodeConnections = collectNodeConnections(part)
        createPredicatesFor(nodeConnections, part.position)

      case Walk(_) =>
        // WALK semantics means we do not create any uniqueness predicates
        Seq.empty
    }
  }

  private def getMaybePredicatesWithinPathPattern(
    part: NonPrefixedPatternPart,
    pathMode: PathMode
  ): Option[Expression] =
    getPredicatesWithinPathPattern(part, pathMode).iterator.reduceOption(expressions.And(_, _)(part.position))

  private def patternRewriter(pathMode: PathMode): Rewriter = bottomUp(Rewriter.lift {
    case part @ PrefixedPatternPart(_: SelectiveSelector, _, _) =>
      val maybePredicate = {
        val element = part.element match {
          case path: ParenthesizedPath => path.part
          case _                       => part.part
        }
        getMaybePredicatesWithinPathPattern(element, pathMode)
      }
      rewriteSelectivePatternPart(part, maybePredicate)

    case qpp @ QuantifiedPath(patternPart, _, where, _) =>
      val maybePredicate = getMaybePredicatesWithinPathPattern(patternPart, pathMode)
      val newWhere =
        Where.combineOrCreateExpressionBeforeCnf(where, maybePredicate)(Some(qpp.position))
      qpp.copy(optionalWhereExpression = newWhere)(qpp.position)
  })

  def canBeEmpty(range: Option[Range]): Boolean =
    range match {
      case None                        => false // * means lower bound of 1 in var length relationships
      case Some(Range(None, _))        => false // default lower bound is 1 in var length relationships
      case Some(Range(Some(lower), _)) => lower.value == 0
    }

  def collectNodeConnections(pattern: ASTNode): Seq[NodeConnection] =
    pattern.folder.treeFold(Seq.empty[NodeConnection]) {
      case _: ScopeExpression =>
        acc => SkipChildren(acc)

      case PrefixedPatternPart(_: SelectiveSelector, _, _) =>
        acc => SkipChildren(acc)

      case qpp: QuantifiedPath =>
        acc =>
          TraverseChildrenNewAccForSiblings(
            Seq.empty[SingleRelationship],
            innerAcc => {
              // Make sure that predicates we generate for QPPs use the group variable, not the singleton variable.
              // To ensure this, we need to change the position to that of the QPP.
              val innerRelsWithFixedPositions = innerAcc.asInstanceOf[Seq[SingleRelationship]]
                .map(x => x.copy(variable = x.variable.withPosition(qpp.position)))
              acc :+ RelationshipGroup(innerRelsWithFixedPositions, qpp.quantifier.canBeEmpty)
            }
          )

      case _: ShortestPathsPatternPart =>
        acc => SkipChildren(acc)

      case RelationshipChain(_, RelationshipPattern(optIdent, labelExpression, None, _, _, _), _) =>
        acc => {
          val ident =
            optIdent.getOrElse(throw new IllegalStateException("This rewriter cannot work with unnamed patterns"))
          TraverseChildren(acc :+ SingleRelationship(ident, labelExpression))
        }

      case RelationshipChain(_, RelationshipPattern(optIdent, labelExpression, Some(range), _, _, _), _) =>
        acc => {
          val ident =
            optIdent.getOrElse(throw new IllegalStateException("This rewriter cannot work with unnamed patterns"))
          TraverseChildren(acc :+ RelationshipGroup(Seq(SingleRelationship(ident, labelExpression)), canBeEmpty(range)))
        }
    }

  def createPredicatesFor(nodeConnections: Seq[NodeConnection], pos: InputPosition): Seq[Expression] = {
    val pairs = for {
      (x, i) <- nodeConnections.zipWithIndex
      y <- nodeConnections.drop(i + 1)
    } yield (x, y)

    val interRelUniqueness = createInterRelUniquenessPredicates(pairs, pos)

    val intraRelUniqueness = nodeConnections.collect {
      case rg: RelationshipGroup =>
        val singleList = reduceLists(rg.innerRelationships.map(_.variable.copyId), pos)
        Unique(singleList)(pos)
    }

    interRelUniqueness ++ intraRelUniqueness
  }

  private def createInterRelUniquenessPredicates(pairs: Seq[(NodeConnection, NodeConnection)], pos: InputPosition) = {
    pairs.collect {
      case (x: SingleRelationship, y: SingleRelationship) if x.name == y.name =>
        Seq(False()(pos.zeroLength))

      case (x: SingleRelationship, y: SingleRelationship) if !x.isAlwaysDifferentFrom(y) =>
        Seq(DifferentRelationships(x.variable.copyId, y.variable.copyId)(pos))

      case (x: SingleRelationship, y: RelationshipGroup) =>
        y.innerRelationships
          .filterNot(_.isAlwaysDifferentFrom(x))
          .map(_.variable.copyId)
          .reduceRightOption[Expression]((y, x) => expressions.Add(x, y)(pos))
          .map { innerY =>
            NoneOfRelationships(x.variable.copyId, innerY)(pos)
          }

      case (x: RelationshipGroup, y: SingleRelationship) =>
        x.innerRelationships
          .filterNot(_.isAlwaysDifferentFrom(y))
          .map(_.variable.copyId)
          .reduceRightOption[Expression]((y, x) => expressions.Add(x, y)(pos))
          .map { innerX =>
            NoneOfRelationships(y.variable.copyId, innerX)(pos)
          }

      case (x: RelationshipGroup, y: RelationshipGroup) =>
        val xRels = x.innerRelationships.filter(innerX => y.innerRelationships.exists(!_.isAlwaysDifferentFrom(innerX)))
        val yRels = y.innerRelationships.filter(innerY => x.innerRelationships.exists(!_.isAlwaysDifferentFrom(innerY)))
        Option.when(xRels.nonEmpty && yRels.nonEmpty) {
          if (xRels.map(_.name).intersect(yRels.map(_.name)).nonEmpty && !(x.canBeEmpty || y.canBeEmpty)) {
            False()(pos.zeroLength)
          } else {
            val xList = reduceLists(xRels.map(_.variable.copyId), pos)
            val yList = reduceLists(yRels.map(_.variable.copyId), pos)
            Disjoint(xList, yList)(pos)
          }
        }
    }.flatten
  }

  private def reduceLists(vars: Seq[LogicalVariable], pos: InputPosition): Expression =
    vars.reduceRight[Expression]((y, x) => expressions.Add(x, y)(pos))

  private[rewriters] def evaluate(expression: LabelExpression, relType: SymbolicName): TailRec[Boolean] =
    expression match {
      case Conjunctions(children, _)               => ands(children, relType)
      case ColonConjunction(lhs, rhs, _)           => ands(Seq(lhs, rhs), relType)
      case Disjunctions(children, _)               => ors(children, relType)
      case ColonDisjunction(lhs, rhs, _)           => ors(Seq(lhs, rhs), relType)
      case Negation(e, _)                          => TailCalls.tailcall(evaluate(e, relType)).map(value => !value)
      case Wildcard(_)                             => TailCalls.done(true)
      case Leaf(expressionRelType: RelTypeName, _) => TailCalls.done(expressionRelType == relType)
      case DynamicLeaf(_, _)                       => TailCalls.done(true)
      case x =>
        throw new IllegalArgumentException(s"Unexpected label expression $x when evaluating relationship overlap")
    }

  private def ors(exprs: Seq[LabelExpression], relType: SymbolicName): TailRec[Boolean] = {
    if (exprs.isEmpty) TailCalls.done(false)
    else {
      for {
        head <- TailCalls.tailcall(evaluate(exprs.head, relType))
        tail <- if (head) TailCalls.done(true) else ors(exprs.tail, relType)
      } yield head || tail
    }
  }

  private def ands(exprs: Seq[LabelExpression], relType: SymbolicName): TailRec[Boolean] = {
    if (exprs.isEmpty) TailCalls.done(true)
    else {
      for {
        head <- TailCalls.tailcall(evaluate(exprs.head, relType))
        tail <- if (!head) TailCalls.done(false) else ands(exprs.tail, relType)
      } yield head && tail
    }
  }

  private[rewriters] def overlaps(
    relTypesToConsider: Seq[SymbolicName],
    labelExpression0: Option[LabelExpression],
    labelExpression1: Option[LabelExpression]
  ): Boolean = {
    // if both labelExpression0 and labelExpression1 evaluate to true when relType is present on a rel, then there's an overlap between the label expressions
    relTypesToConsider.exists(relType => ands(Seq(labelExpression0, labelExpression1).flatten, relType).result) ||
    // labelExpressions containing dynamic Types should always overlap.
    labelExpression0.exists(_.containsDynamicLabelOrTypeExpression) ||
    labelExpression1.exists(_.containsDynamicLabelOrTypeExpression)
  }

  private[rewriters] def getRelTypesToConsider(labelExpression: Option[LabelExpression]): Seq[SymbolicName] = {
    // also add the arbitrary rel type "" to check for rel types which are not explicitly named (such as in -[r]-> or -[r:%]->)
    labelExpression.map(_.flatten).getOrElse(Seq.empty) appended RelTypeName("")(InputPosition.NONE)
  }
}

object RelationshipUniqueness {

  sealed trait NodeConnection

  case class RelationshipGroup(innerRelationships: Seq[SingleRelationship], canBeEmpty: Boolean = false)
      extends NodeConnection

  case class SingleRelationship(
    variable: LogicalVariable,
    labelExpression: Option[LabelExpression]
  ) extends NodeConnection {
    def name: String = variable.name

    def isAlwaysDifferentFrom(other: SingleRelationship): Boolean = {
      val relTypesToConsider =
        getRelTypesToConsider(labelExpression).concat(getRelTypesToConsider(other.labelExpression)).distinct
      !overlaps(relTypesToConsider, labelExpression, other.labelExpression)
    }
  }
}

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
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.ParenthesizedPath
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternPart.SelectiveSelector
import org.neo4j.cypher.internal.expressions.PrefixedPatternPart
import org.neo4j.cypher.internal.expressions.Range
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.ScopeExpression
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.expressions.VarLengthLowerBound
import org.neo4j.cypher.internal.expressions.VarLengthUpperBound
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.bottomUp

case object AddVarLengthBoundPredicates extends AddPathPredicates[RelationshipPattern] {

  override val rewriter: Rewriter = bottomUp(Rewriter.lift {
    case matchClause @ Match(_, _, pattern: Pattern, _, where, _) =>
      val relationships = collectNodeConnections(pattern)
      val newWhere = withPredicates(matchClause, relationships, where)
      matchClause.copy(where = newWhere)(matchClause.position)
    case part @ PrefixedPatternPart(_: SelectiveSelector, _, _) =>
      val maybePredicate = {
        val element = part.element match {
          case path: ParenthesizedPath => path.part.element
          case otherElement            => otherElement
        }
        createPredicateFor(collectNodeConnections(element), part.position)
      }
      rewriteSelectivePatternPart(part, maybePredicate)
  })

  def collectNodeConnections(pattern: ASTNode): Seq[RelationshipPattern] =
    pattern.folder.treeFold(Seq.empty[RelationshipPattern]) {
      case _: ScopeExpression =>
        acc => SkipChildren(acc)

      case _: ShortestPathsPatternPart =>
        acc => SkipChildren(acc)

      case PrefixedPatternPart(_: SelectiveSelector, _, _) =>
        acc => SkipChildren(acc)

      case RelationshipChain(_, rel @ RelationshipPattern(_, _, Some(_), _, _, _), _) =>
        acc => TraverseChildren(acc :+ rel)
    }

  def createPredicatesFor(relationships: Seq[RelationshipPattern], pos: InputPosition): Seq[Expression] =
    relationships.flatMap {
      case RelationshipPattern(Some(relName), _, Some(None), _, _, _) =>
        createSizePredicatesForVarLengthRelationship(relName.name, 1, None, pos)
      case RelationshipPattern(Some(relName), _, Some(Some(Range(lowerLiteral, upperLiteral))), _, _, _) =>
        val lowerValue = lowerLiteral.map(_.value.longValue()).getOrElse(1L)
        val maybeUpperValue = upperLiteral.map(_.value.longValue())
        createSizePredicatesForVarLengthRelationship(relName.name, lowerValue, maybeUpperValue, pos)
      case e => throw new IllegalStateException(s"Did expect named var-length relationship. Got: $e")
    }

  private def createSizePredicatesForVarLengthRelationship(
    relName: String,
    lowerBoundValue: Long,
    maybeUpperBoundValue: Option[Long],
    pos: InputPosition
  ): Seq[Expression] = {
    def relNameVar = Variable(relName)(pos, Variable.isIsolatedDefault)
    val lowerBound = Option.when(lowerBoundValue > 0)(VarLengthLowerBound(relNameVar, lowerBoundValue)(pos))
    val upperBound = maybeUpperBoundValue.map { max =>
      VarLengthUpperBound(relNameVar, max)(pos)
    }
    Seq.empty ++ lowerBound ++ upperBound
  }

  override def preConditions: Set[StepSequencer.Condition] = {
    // we do not really need a dependency here but this is to deflake tests which assert on the order of predicates
    // generated in the WHERE clause.
    super.preConditions + AddElementUniquenessPredicates.completed
  }
}

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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.ParenthesizedPath
import org.neo4j.cypher.internal.expressions.PathPatternPart
import org.neo4j.cypher.internal.expressions.PatternComprehension
import org.neo4j.cypher.internal.expressions.PatternExpression
import org.neo4j.cypher.internal.expressions.PrefixedPatternPart
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNodesOfType
import org.neo4j.cypher.internal.rewriting.conditions.NoUnnamedNodesAndRelationships
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.factories.ASTRewriterFactory
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.DefaultPostCondition
import org.neo4j.cypher.internal.util.StepSequencer.Step
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo

trait AddPathPredicates[NC] extends Step with DefaultPostCondition with ASTRewriterFactory {

  override def preConditions: Set[StepSequencer.Condition] = Set(
    NoUnnamedNodesAndRelationships,
    AddQuantifiedPathAnonymousVariableGroupings.completed,
    // We cannot add such predicates in PatternExpression/PatternComprehension,
    // so they should have been rewritten at this point
    ContainsNoNodesOfType[PatternExpression](),
    ContainsNoNodesOfType[PatternComprehension]()
  )

  override def invalidatedConditions: Set[StepSequencer.Condition] = SemanticInfoAvailable

  val rewriter: Rewriter

  override def getRewriter(
    semanticState: SemanticState,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator,
    cancellationChecker: CancellationChecker,
    version: CypherVersion
  ): Rewriter = rewriter

  protected def withPredicates(pattern: ASTNode, nodeConnections: Seq[NC], where: Option[Where]): Option[Where] = {
    val pos = pattern.position
    val maybePredicate: Option[Expression] = createPredicateFor(nodeConnections, pos)
    Where.combineOrCreateBeforeCnf(where, maybePredicate)(pos)
  }

  protected def createPredicateFor(nodeConnections: Seq[NC], pos: InputPosition): Option[Expression] = {
    createPredicatesFor(nodeConnections, pos).reduceOption(expressions.And(_, _)(pos))
  }

  protected def rewriteSelectivePatternPart(
    part: PrefixedPatternPart,
    maybePredicate: Option[Expression]
  ): PrefixedPatternPart = {
    part.element match {
      case path: ParenthesizedPath =>
        val pos = path.optionalWhereClause.map(_.position).getOrElse(InputPosition.NONE)
        val newPredicate = Where.combineOrCreateExpressionBeforeCnf(
          path.optionalWhereClause,
          maybePredicate
        )(Some(pos))
        val newElement = path.copy(optionalWhereClause = newPredicate)(path.position)
        part.replaceElement(newElement)

      case otherElement =>
        maybePredicate match {
          case None => part
          case Some(predicate) =>
            val syntheticPatternPart = PathPatternPart(otherElement)
            val newElement = ParenthesizedPath(syntheticPatternPart, Some(predicate))(part.position)
            part.replaceElement(newElement)
        }
    }
  }

  def createPredicatesFor(nodeConnections: Seq[NC], pos: InputPosition): Seq[Expression]

  def collectNodeConnections(pattern: ASTNode): Seq[NC]
}

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
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.ast.semantics.SemanticAnalysisTooling
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NamedPatternPart
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternElement
import org.neo4j.cypher.internal.expressions.PatternPart.AllPaths
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.VectorSearchPredicate
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTNumber
import org.neo4j.cypher.internal.util.symbols.CTVector

case class Search(
  bindingVariable: LogicalVariable,
  score: Option[LogicalVariable],
  indexName: Either[String, Parameter],
  embedding: Expression,
  limit: Limit
)(val position: InputPosition)
    extends ASTNode with SemanticCheckable with SemanticAnalysisTooling {

  def asExpression: Expression = {
    indexName match {
      case Right(_) =>
        // We currently only support String, update this when we allow Parameter.
        throw new IllegalArgumentException(
          s"Index name as Parameter is not supported in the expression form of SEARCH at position $position"
        )
      case Left(indexName) =>
        score match {
          case Some(score) => // TODO: Support Score variable in planning https://linear.app/neo4j/issue/PLAN-2844/plan-vector-search-where-score-is-returned
            throw new IllegalArgumentException(
              s"Score is not supported in the expression form of SEARCH at position $position"
            )
          case None =>
            VectorSearchPredicate(
              bindingVariable,
              indexName,
              embedding,
              limit.expression
            )(position)
        }
    }

  }

  def semanticCheck: SemanticCheck = {
    checkFeatureFlag() ifOkChain
      checkBindingVariable() chain
      checkIndexName() chain
      checkEmbedding() chain
      checkLimit() chain
      checkScore()
  }

  private def checkFeatureFlag(): SemanticCheck =
    requireFeatureSupport(
      "The SEARCH keyword",
      SemanticFeature.VectorSearch,
      position
    )

  private def checkIndexName(): SemanticCheck = {
    // This is a restriction for the MVP which we intend to lift later
    if (indexName.isRight) {
      SemanticError.invalidIndexParameter(indexName.toOption.get.position)
    } else {
      SemanticCheck.success
    }
  }

  private def checkEmbedding(): SemanticCheck = {
    SemanticExpressionCheck.simple(embedding) chain
      expectType(CTVector.union(CTList(CTNumber).covariant), embedding)
  }

  private def checkLimit(): SemanticCheck = limit.semanticCheckWithUpperBound(Int.MaxValue)

  private def checkBindingVariable(): SemanticCheck =
    SemanticExpressionCheck.check(Expression.SemanticContext.Simple, bindingVariable)

  private def checkScore(): SemanticCheck = {
    if (score.isDefined) {
      declareVariable(score.get, CTAny)
    } else {
      SemanticCheck.success
    }
  }

  /*
   * Semantic checks that verify the pattern from the MATCH statement.
   * Most of the limitations here are expected to be lifted in later iterations of the feature
   */
  def patternChecks(pattern: Pattern.ForMatch): SemanticCheck = {
    // We only want to do these checks once, because later rewriters can have made the pattern more complex
    SemanticCheck.fromState { state =>
      if (state.semanticCheckHasRunOnce) {
        SemanticCheck.success
      } else {
        val patternParts = pattern.patternParts
        val patternVariables = patternParts.collect(p => p.allVariables).flatten
        val patternPart = patternParts.head.part
        val selectors = patternParts.collect(p => p.selector).filterNot(s => s.isInstanceOf[AllPaths])

        if (!patternVariables.contains(bindingVariable)) {
          SemanticError.searchWithVariableFromOutsideMatch(bindingVariable)
        } else if (patternParts.size > 1) {
          SemanticError.searchWithTooComplexMatch(patternParts(1).position)
        } else if (!patternPart.isInstanceOf[NamedPatternPart] && patternVariables.size > 1) {
          val firstUnrelatedVar = patternVariables.filterNot(v => v == bindingVariable).head
          SemanticError.searchWithMultipleBoundVariables(firstUnrelatedVar.position)
        } else if (patternPart.isInstanceOf[NamedPatternPart] && patternVariables.size > 2) {
          val firstUnrelatedVar = patternVariables.filterNot(v =>
            v == bindingVariable || v == patternPart.asInstanceOf[NamedPatternPart].variable
          ).head
          SemanticError.searchWithMultipleBoundVariables(firstUnrelatedVar.position)
        } else if (selectors.nonEmpty) {
          SemanticError.searchWithTooComplexMatch(selectors.head.position)
        } else {
          checkPatternElement(patternPart.element)
        }
      }
    }
  }

  private def checkPatternElement(element: PatternElement): SemanticCheck = {
    element match {
      case n: NodePattern => checkNodePattern(n)
      case RelationshipChain(p, r, n) =>
        checkPatternElement(p) chain checkNodePattern(n) chain checkRelationshipPattern(r)
      case _ => SemanticError.searchWithTooComplexMatch(element.position)
    }
  }

  private def checkNodePattern(nodePattern: NodePattern): SemanticCheck = {
    nodePattern match {
      case NodePattern(None, Some(pred), _, _) => SemanticError.searchWithInvalidPredicates(pred.position)
      case NodePattern(None, _, Some(pred), _) => SemanticError.searchWithInvalidPredicates(pred.position)
      case NodePattern(None, _, _, Some(pred)) => SemanticError.searchWithInvalidPredicates(pred.position)
      case _                                   => SemanticCheck.success
    }
  }

  private def checkRelationshipPattern(relationshipPattern: RelationshipPattern): SemanticCheck = {
    relationshipPattern match {
      case RelationshipPattern(None, Some(pred), _, _, _, _) =>
        SemanticError.searchWithInvalidPredicates(pred.position)
      case RelationshipPattern(None, _, _, Some(pred), _, _) =>
        SemanticError.searchWithInvalidPredicates(pred.position)
      case RelationshipPattern(None, _, _, _, Some(pred), _) =>
        SemanticError.searchWithInvalidPredicates(pred.position)
      // Variable length relationship
      case r @ RelationshipPattern(None, _, Some(_), _, _, _) =>
        SemanticError.searchWithTooComplexMatch(r.position)
      case _ => SemanticCheck.success
    }
  }
}

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
import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.GreaterThan
import org.neo4j.cypher.internal.expressions.GreaterThanOrEqual
import org.neo4j.cypher.internal.expressions.LessThan
import org.neo4j.cypher.internal.expressions.LessThanOrEqual
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NamedPatternPart
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternElement
import org.neo4j.cypher.internal.expressions.PatternPart.AllPaths
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.VectorSearchPredicate
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTBoolean
import org.neo4j.cypher.internal.util.symbols.CTDate
import org.neo4j.cypher.internal.util.symbols.CTDateTime
import org.neo4j.cypher.internal.util.symbols.CTDuration
import org.neo4j.cypher.internal.util.symbols.CTFloat
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTLocalDateTime
import org.neo4j.cypher.internal.util.symbols.CTLocalTime
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTNumber
import org.neo4j.cypher.internal.util.symbols.CTRelationship
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.CTTime
import org.neo4j.cypher.internal.util.symbols.CTVector

case class Search(
  bindingVariable: LogicalVariable,
  score: Option[LogicalVariable],
  indexName: Either[String, Parameter],
  embedding: Expression,
  where: Option[Where],
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
    checkSearchFeatureFlag() ifOkChain
      checkBindingVariable() chain
      checkIndexName() chain
      checkEmbedding() chain
      checkLimit() chain
      checkScore() chain
      checkWhere()
  }

  private def checkSearchFeatureFlag(): SemanticCheck =
    requireFeatureSupport(
      "The SEARCH keyword",
      SemanticFeature.VectorSearch,
      position
    )

  private def checkSingleStageFeatureFlag(): SemanticCheck =
    requireFeatureSupport(
      "Single-stage filtering for vector search",
      SemanticFeature.VectorSingleStageFilteringEnabled,
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
    SemanticExpressionCheck.check(Expression.SemanticContext.Simple, bindingVariable) chain
      expectType(CTNode.covariant | CTRelationship.covariant, bindingVariable)

  private def checkScore(): SemanticCheck = {
    if (score.isDefined) {
      declareVariable(score.get, CTAny)
    } else {
      SemanticCheck.success
    }
  }

  private def checkWhere(): SemanticCheck = {
    SemanticCheck.fromState { state =>
      /*
       * It is only possible to verify the restriction on the WHERE clause in the first pass of semantic checking.
       * Later, rewriters will have modified the predicates e.g. by turning m.prop = 5 to m.prop IN [$`  AUTOINT1`].
       * This also means that type checking for e.g. unresolved functions like date() needs to happen at runtime.
       */
      if (where.isDefined && !state.semanticCheckHasRunOnce) {
        checkSingleStageFeatureFlag() ifOkChain
          checkWhereExpression(where.get.expression)
      } else {
        SemanticCheck.success
      }
    }
  }

  private def checkWhereExpression(expression: Expression): SemanticCheck = {
    expression match {
      case GreaterThan(Property(variable: LogicalVariable, _), rhs) =>
        checkWhereVariable(variable) chain checkRhsType(rhs)
      case GreaterThanOrEqual(Property(variable: LogicalVariable, _), rhs) =>
        checkWhereVariable(variable) chain checkRhsType(rhs)
      case LessThan(Property(variable: LogicalVariable, _), rhs) => checkWhereVariable(variable) chain checkRhsType(rhs)
      case LessThanOrEqual(Property(variable: LogicalVariable, _), rhs) =>
        checkWhereVariable(variable) chain checkRhsType(rhs)
      case Equals(Property(variable: LogicalVariable, _), rhs) =>
        checkWhereVariable(variable) chain checkRhsType(rhs, equality = true)
      case And(lhs, rhs) => checkWhereExpression(lhs) chain checkWhereExpression(rhs)
      case expr          => SemanticError.singleStageWithInvalidPredicate(expr, expr.position)
    }
  }

  private def checkWhereVariable(variable: LogicalVariable): SemanticCheck = {
    if (bindingVariable.equals(variable)) {
      SemanticCheck.success
    } else {
      SemanticError.singleStageWithInvalidVariable(variable.name, bindingVariable.name, variable.position)
    }
  }

  private def checkRhsType(rhs: Expression, equality: Boolean = false): SemanticCheck = {
    val validTypes = CTInteger
      .union(CTFloat)
      .union(CTBoolean)
      .union(CTString)
      .union(CTDate)
      .union(CTDateTime)
      .union(CTLocalDateTime)
      .union(CTLocalTime)
      .union(CTTime)

    SemanticExpressionCheck.simple(rhs) chain {
      if (equality) {
        expectTypeWithoutCoercion(validTypes.union(CTDuration), rhs)
      } else {
        expectTypeWithoutCoercion(validTypes, rhs)
      }
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
        val patternVariables = patternParts.flatMap(p => p.allVariables).toSet
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

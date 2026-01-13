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
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.when
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.Ands
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
import org.neo4j.cypher.internal.expressions.VectorFilterExpression
import org.neo4j.cypher.internal.expressions.VectorFilterExpression.VectorFilterExpressionRange
import org.neo4j.cypher.internal.notification.IdentifierShadowsVariableNotification
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
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

  def semanticCheck: SemanticCheck = {
    checkSearchFeatureFlag() ifOkChain
      checkBindingVariable() chain
      checkScore() chain
      checkIndexName() chain
      checkEmbedding() chain
      checkLimit() chain
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
      "Vector search filtering",
      SemanticFeature.VectorSingleStageFilteringEnabled,
      position
    )

  private def checkIndexName(): SemanticCheck = {
    indexName match {
      case Left(name) =>
        notifyIfIndexNameShadowsVariable(name)
      case Right(parameter) =>
        // This is a restriction for the MVP which we intend to lift later
        SemanticError.invalidIndexParameter(parameter.position)
    }
  }

  private def notifyIfIndexNameShadowsVariable(name: String): SemanticState => Either[SemanticError, SemanticState] = {
    (s: SemanticState) =>
      s.symbol(name) match {
        case None => Right(s)
        case Some(symbol) =>
          Right(s.addNotification(IdentifierShadowsVariableNotification(
            symbol.definition.asVariable.position,
            name,
            "VECTOR INDEX"
          )))
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
      declareVariable(score.get, CTFloat)
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
          checkExpressionsRangeOrExact(where.get.expression)
      } else {
        SemanticCheck.success
      }
    }
  }

  private def checkExpressionsRangeOrExact(expression: Expression): SemanticCheck = {
    val CheckedVectorFilterExpression(check, expressions) = asFilterExpressions(expression)
    check ifOkChain {
      val expressionsByProperty = expressions.groupBy(_.propertyName).values
      // TODO: Improve error reporting to show offending expressions (see SURF-489)
      val invalid = expressionsByProperty.exists {
        case Seq(_)                            => false
        case VectorFilterExpressionRange(_, _) => false
        case _                                 => true
      }
      when(invalid) {
        SemanticError.singleStageWithInvalidPredicate(expression, expression.position)
      }
    }
  }

  private case class CheckedVectorFilterExpression(
    check: SemanticCheck,
    expressions: Seq[VectorFilterExpression]
  ) {

    def ++(other: CheckedVectorFilterExpression): CheckedVectorFilterExpression =
      CheckedVectorFilterExpression(
        this.check chain other.check,
        this.expressions ++ other.expressions
      )
  }

  private object CheckedVectorFilterExpression {

    val empty: CheckedVectorFilterExpression =
      CheckedVectorFilterExpression(SemanticCheck.success, Seq.empty)

    def apply(
      variable: LogicalVariable,
      rhs: Expression,
      filterExpression: VectorFilterExpression
    ): CheckedVectorFilterExpression = {
      val isEquality = filterExpression.isInstanceOf[VectorFilterExpression.Equality]
      CheckedVectorFilterExpression(
        checkWhereVariable(variable) chain checkRhs(rhs, isEquality),
        Seq(filterExpression)
      )
    }

    def apply(error: SemanticError): CheckedVectorFilterExpression =
      CheckedVectorFilterExpression(error, Seq.empty)
  }

  private def asFilterExpressions(expression: Expression): CheckedVectorFilterExpression =
    expression match {
      case VectorFilterExpression(variable: LogicalVariable, rhs: Expression, operator: VectorFilterExpression) =>
        CheckedVectorFilterExpression(variable, rhs, operator)
      case And(lhs, rhs) => asFilterExpressions(lhs) ++ asFilterExpressions(rhs)
      case Ands(exprs) =>
        exprs.map(asFilterExpressions).foldLeft(CheckedVectorFilterExpression.empty)(_ ++ _)
      case expr =>
        CheckedVectorFilterExpression(SemanticError.singleStageWithInvalidPredicate(expr, expr.position))
    }

  private def checkWhereVariable(variable: LogicalVariable): SemanticCheck =
    when(!bindingVariable.equals(variable)) {
      SemanticError.singleStageWithInvalidVariable(variable.name, bindingVariable.name, variable.position)
    }

  private def checkRhs(rhs: Expression, equality: Boolean): SemanticCheck =
    checkRhsType(rhs, equality)

  private def checkRhsType(rhs: Expression, equality: Boolean): SemanticCheck = {
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
      case relChain @ RelationshipChain(_: RelationshipChain, _, _) =>
        SemanticError.searchWithTooComplexMatch(relChain.position)
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

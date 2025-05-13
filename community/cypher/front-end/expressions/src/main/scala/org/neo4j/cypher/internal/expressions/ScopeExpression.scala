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
package org.neo4j.cypher.internal.expressions

import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp

/**
 * Scope expressions bundle together variables of a new scope
 * together with any child expressions that get evaluated in a context where
 * these variables are bound
 *
 * This is a hard contract(!): All child expressions of a scope expression must be:
 * - either an introduced variable
 * - or child expressions in a scope where those variables are bound
 */
trait ScopeExpression extends Expression {
  def introducedVariables: Set[LogicalVariable]
  def scopeDependencies: Set[LogicalVariable]

  // We need to override dependencies because the default implementation relies on scope Expressions computing the dependencies manually,
  // so that it does not need to recurse into them.
  final override def dependencies: Set[LogicalVariable] = scopeDependencies

  override def isConstantForQuery: Boolean = false
}

/**
 * A generic expression for scoped anonymous variables.
 *
 * Binds an anonymous variable (scope.scopeVariable) to the result of `scopeVariableExpression`.
 * Then evaluates the inner expression (scope.innerExpression) with the anonymous variable in scope.
 *
 * Note! Do you have a new use case for this class or plan to make changes?
 * Then, make sure the implementation of isolateAggregation still holds.
 */
case class AnonymousScopeExpression(
  scope: AnonymousScopeExpression.Scope,
  scopeVariableExpression: Expression // The expression to bind the anon expression variable to.
) extends Expression {
  override def position: InputPosition = scope.innerExpression.position
  override def isConstantForQuery: Boolean = scope.isConstantForQuery && scopeVariableExpression.isConstantForQuery

  /**
   * Returns a representation of the inner expression that do not include the anonymous scope variable.
   * This is needed since this expression is not possible to express in Cypher.
   */
  def deAnonymisedInnerExpression: Expression = scope.innerExpression.endoRewrite(bottomUp(Rewriter.lift {
    case variable: LogicalVariable if variable.name == scope.scopeVariable.name => scopeVariableExpression
  }))
}

object AnonymousScopeExpression {

  def apply(
    anonVariable: LogicalVariable,
    scopeVariableExpression: Expression,
    innerExpression: Expression
  ): AnonymousScopeExpression =
    AnonymousScopeExpression(Scope(anonVariable, innerExpression), scopeVariableExpression)

  /** The scope of an AnonymousScopeExpression, needs to be defined separately to adhere to the contract. */
  case class Scope(
    scopeVariable: LogicalVariable, // Anonymous expression variable.
    innerExpression: Expression // Expression that reads the scoped variable and produces the result of this expression.
  ) extends ScopeExpression {
    override def position: InputPosition = innerExpression.position
    override def introducedVariables: Set[LogicalVariable] = Set(scopeVariable)
    override def scopeDependencies: Set[LogicalVariable] = innerExpression.dependencies -- introducedVariables
  }
}

case class FilterScope(variable: LogicalVariable, innerPredicate: Option[Expression])(val position: InputPosition)
    extends ScopeExpression {
  val introducedVariables: Set[LogicalVariable] = Set(variable)

  override def scopeDependencies: Set[LogicalVariable] =
    innerPredicate.fold(Set.empty[LogicalVariable])(_.dependencies) -- introducedVariables
}

case class ExtractScope(
  variable: LogicalVariable,
  innerPredicate: Option[Expression],
  extractExpression: Option[Expression]
)(val position: InputPosition) extends ScopeExpression {
  val introducedVariables: Set[LogicalVariable] = Set(variable)

  override def scopeDependencies: Set[LogicalVariable] =
    innerPredicate.fold(Set.empty[LogicalVariable])(_.dependencies) ++
      extractExpression.fold(Set.empty[LogicalVariable])(_.dependencies) --
      introducedVariables
}

case class ReduceScope(accumulator: LogicalVariable, variable: LogicalVariable, expression: Expression)(
  val position: InputPosition
) extends ScopeExpression {
  val introducedVariables: Set[LogicalVariable] = Set(accumulator, variable)

  override def scopeDependencies: Set[LogicalVariable] = expression.dependencies -- introducedVariables
}

/**
 * A scope expression which holds pre-computed dependencies from RecordScope.
 * introducedVariables: Variables introduced by this scope
 * scopeDependencies: Variables that are referencing outer scope variables
 * subqueryAstNode: Refers to the inner ASTNode that can used to compute the dependencies
 */
trait ExpressionWithComputedDependencies extends Expression {
  self: ScopeExpression =>

  val computedIntroducedVariables: Option[Set[LogicalVariable]]
  val computedScopeDependencies: Option[Set[LogicalVariable]]

  def subqueryAstNode: ASTNode

  def withComputedIntroducedVariables(computedIntroducedVariables: Set[LogicalVariable])
    : ExpressionWithComputedDependencies
  def withComputedScopeDependencies(computedScopeDependencies: Set[LogicalVariable]): ExpressionWithComputedDependencies

  final override def introducedVariables: Set[LogicalVariable] = computedIntroducedVariables.getOrElse(
    throw new IllegalStateException("Introduced variables have not been computed yet")
  )

  final override def scopeDependencies: Set[LogicalVariable] = computedScopeDependencies.getOrElse(
    throw new IllegalStateException("Scope dependencies have not been computed yet")
  )

}

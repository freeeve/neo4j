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

import org.neo4j.cypher.internal.expressions.AllReducePredicate.AllReduceScope
import org.neo4j.cypher.internal.util.InputPosition

/**
 * AST node directly from the parser, before group variable is known.
 */
case class AllReducePredicateUnchecked(
  accumulator: LogicalVariable,
  init: Expression,
  reductionStep: Expression,
  predicate: Expression
)(val position: InputPosition) extends BooleanExpression {

  override def isConstantForQuery: Boolean =
    init.isConstantForQuery &&
      reductionStep.isConstantForQuery &&
      predicate.isConstantForQuery
}

/**
 * AST node after the group variable has been resolved.
 */
case class AllReducePredicate(
  scope: AllReduceScope,
  groupVariable: LogicalVariable,
  init: Expression
)(val position: InputPosition) extends BooleanExpression {

  def accumulator: LogicalVariable = scope.accumulator
  def reductionStep: Expression = scope.reductionStepScope.reductionStep
  def predicate: Expression = scope.predicate
  def singletonVariable: LogicalVariable = scope.reductionStepScope.singletonVariable

  override def isConstantForQuery: Boolean =
    init.isConstantForQuery && scope.isConstantForQuery
}

object AllReducePredicate {

  def unchecked(
    accumulator: LogicalVariable,
    init: Expression,
    reductionStep: Expression,
    predicate: Expression
  )(position: InputPosition): AllReducePredicateUnchecked = {
    AllReducePredicateUnchecked(accumulator, init, reductionStep, predicate)(position)
  }

  case class AllReduceScope(
    accumulator: LogicalVariable,
    reductionStepScope: ReductionStepScope,
    predicate: Expression
  )(val position: InputPosition) extends ScopeExpression {
    override def introducedVariables: Set[LogicalVariable] = Set(accumulator)

    override def scopeDependencies: Set[LogicalVariable] =
      (reductionStepScope.dependencies ++ predicate.dependencies) -- introducedVariables
  }

  case class ReductionStepScope(
    singletonVariable: LogicalVariable,
    reductionStep: Expression
  )(val position: InputPosition) extends ScopeExpression {
    override def introducedVariables: Set[LogicalVariable] = Set(singletonVariable)

    override def scopeDependencies: Set[LogicalVariable] = reductionStep.dependencies -- introducedVariables
  }
}

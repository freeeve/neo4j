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
 * AST node after the group variable has been resolved.
 */
case class AllReducePredicate(
  scope: AllReduceScope,
  init: Expression,
  list: Expression
)(val position: InputPosition) extends BooleanExpression {

  def accumulator: LogicalVariable = scope.accumulator
  def reductionStep: Expression = scope.reductionStepScope.reductionStep
  def predicate: Expression = scope.predicate
  def reductionStepVariable: LogicalVariable = scope.reductionStepScope.reductionStepVariable

  override def isConstantForQuery: Boolean =
    init.isConstantForQuery && scope.isConstantForQuery
}

object AllReducePredicate {

  val AccumulatorReductionTypeMismatchMessageGenerator: (String, String) => String =
    (expected: String, existing: String) => s"accumulator is $expected but reduction has type $existing"

  case class AllReduceScope(
    accumulator: LogicalVariable,
    reductionStepScope: ReductionStepScope,
    predicate: Expression
  )(val position: InputPosition) extends ScopeExpression {
    override def introducedVariables: Set[LogicalVariable] = Set(accumulator)

    override def scopeDependencies: Set[LogicalVariable] =
      (reductionStepScope.dependencies ++ predicate.dependencies) -- introducedVariables
  }

  /**
   * Represents the second part of an allReduce predicate: How to calculate the next iteration of the accumulator.
   * @param reductionStepVariable the variable used in `reductionStep` representing the singleton from  a list, likely from the QPP.
   * @param reductionStep expression to calculate the next iteration of the accumulator
   */
  case class ReductionStepScope(
    reductionStepVariable: LogicalVariable,
    reductionStep: Expression
  )(val position: InputPosition) extends ScopeExpression {
    override def introducedVariables: Set[LogicalVariable] = Set(reductionStepVariable)

    override def scopeDependencies: Set[LogicalVariable] =
      reductionStep.dependencies -- introducedVariables
  }
}

/**
 * This is a placeholder to be planned on the RHS of a Repeat, to be rewritten later on.
 */
case class AllReduceSingletonPredicate(
  accumulator: LogicalVariable,
  reductionStep: Expression,
  predicate: Expression
)(val position: InputPosition) extends BooleanExpression {

  override def dependencies: Set[LogicalVariable] =
    reductionStep.dependencies ++ predicate.dependencies + accumulator

  override def isConstantForQuery: Boolean = reductionStep.isConstantForQuery && predicate.isConstantForQuery
}

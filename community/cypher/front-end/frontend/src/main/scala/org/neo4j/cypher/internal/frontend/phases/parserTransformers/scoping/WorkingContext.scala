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
package org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping

import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeSurveyor.unitVariables
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition

sealed trait WorkingContext {
  def allSymbols: Set[LogicalVariable]
}

case class RegularContext(constants: Set[LogicalVariable], variables: Set[LogicalVariable]) extends WorkingContext {
  lazy val constantsAndVariables: Set[LogicalVariable] = constants union variables
  override def allSymbols: Set[LogicalVariable] = constantsAndVariables

  @inline def isEmpty: Boolean = isVariablesEmpty && isConstantsEmpty
  @inline def isConstantsEmpty: Boolean = constants.isEmpty
  @inline def isVariablesEmpty: Boolean = variables.isEmpty

  @inline def amendedWithConstant(amendment: LogicalVariable): RegularContext =
    RegularContext(constants + amendment, variables)

  @inline def amendedWithConstant(amendment: Set[LogicalVariable]): RegularContext =
    RegularContext(constants union amendment, variables)

  @inline def amendedWith(amendment: LogicalVariable): RegularContext = RegularContext(constants, variables + amendment)

  @inline def amendedWith(amendment: Set[LogicalVariable]): RegularContext =
    RegularContext(constants, variables union amendment)

  @inline def constantChildContext(): RegularContext = RegularContext(constants union variables, unitVariables)

  @inline def replaceWith(replacement: Set[LogicalVariable]): RegularContext = RegularContext(constants, replacement)

  @inline def checkIfVariablesAreAlreadyDeclaredAsConstant(newVariables: Iterable[LogicalVariable])
    : Seq[SemanticError] = {
    checkIfVariablesAreAlreadyDeclaredIn(constants, newVariables, SemanticError.variableAlreadyDeclaredInOuterScope)
  }

  @inline def checkIfVariablesAreAlreadyDeclaredAsVariable(
    newVariables: Iterable[LogicalVariable],
    errorFunc: (String, InputPosition) => SemanticError = (n, p) => SemanticError.variableAlreadyDeclared(n, p)
  )
    : Seq[SemanticError] = {
    checkIfVariablesAreAlreadyDeclaredIn(variables, newVariables, errorFunc)
  }

  @inline private def checkIfVariablesAreAlreadyDeclaredIn(
    existingVariables: Set[LogicalVariable],
    newVariables: Iterable[LogicalVariable],
    errorFunc: (String, InputPosition) => SemanticError
  ): Seq[SemanticError] = {
    if (existingVariables.nonEmpty) {
      newVariables.collect {
        case variable if existingVariables contains variable =>
          errorFunc.apply(variable.name, variable.position)
      }.toSeq
    } else {
      Seq.empty
    }
  }

  @inline def resultScope(
    outgoing: RegularContext,
    result: Result,
    children: Seq[WorkingScope],
    referencedOpt: Option[Set[LogicalVariable]] = None,
    declared: Declarations = Declarations.noDeclarations
  )(
    implicit astNode: ASTNode
  ): StatementScope = {
    val referenced = referencedOpt.getOrElse(
      WorkingScope.referencedInChildren(children)
    )
    StatementScope(astNode, this, referenced, declared, outgoing, result, children)
  }

  @inline def forwardWithNoResultForward(children: Seq[WorkingScope] = WorkingScope.noChildren)(
    implicit astNode: ASTNode
  ): StatementScope = {
    StatementScope(
      astNode,
      incoming = this,
      referenced = WorkingScope.referencedInChildren(children),
      declared = Declarations.noDeclarations,
      outgoing = this,
      NoResult,
      children
    )
  }

  @inline def forwardWithOmittedResult(children: Seq[WorkingScope] = WorkingScope.noChildren)(
    implicit astNode: ASTNode
  ): StatementScope = {
    StatementScope(
      astNode,
      incoming = this,
      referenced = WorkingScope.referencedInChildren(children),
      declared = Declarations.noDeclarations,
      outgoing = this,
      OmittedResult,
      children
    )
  }

  @inline def noResultScope(
    outgoing: RegularContext,
    children: Seq[WorkingScope],
    referencedOpt: Option[Set[LogicalVariable]] = None,
    declared: Declarations = Declarations.noDeclarations
  )(
    implicit astNode: ASTNode
  ): StatementScope = {
    val referenced = referencedOpt.getOrElse(
      WorkingScope.referencedInChildren(children)
    )
    StatementScope(astNode, this, referenced, declared, outgoing, NoResult, children)
  }

  @inline def omittedResultScope(
    outgoing: RegularContext,
    children: Seq[WorkingScope],
    referencedOpt: Option[Set[LogicalVariable]] = None,
    declared: Declarations = Declarations.noDeclarations
  )(
    implicit astNode: ASTNode
  ): StatementScope = {
    val referenced = referencedOpt.getOrElse(
      WorkingScope.referencedInChildren(children)
    )
    StatementScope(astNode, this, referenced, declared, outgoing, OmittedResult, children)
  }

  @inline def expressionResultScope(
    astNode: ASTNode,
    children: Seq[WorkingScope],
    referencedOpt: Option[Set[LogicalVariable]] = None,
    declared: Declarations = Declarations.noDeclarations
  ): ExpressionScope = {
    val referenced = referencedOpt.getOrElse(
      WorkingScope.referencedInChildren(children)
    )
    ExpressionScope(astNode, this, referenced, declared, children)
  }
}

object RegularContext {
  def unit: RegularContext = RegularContext(unitVariables, unitVariables)
  def unitWithConstants(constants: Set[LogicalVariable]): RegularContext = RegularContext(constants, unitVariables)
}

case class PatternIncomingContext(
  topologicalConstants: Set[LogicalVariable],
  predicateConstants: Set[LogicalVariable],
  pathConstants: Set[LogicalVariable]
) extends WorkingContext {
  override def allSymbols: Set[LogicalVariable] = topologicalConstants union predicateConstants union pathConstants

  @inline def amendedWithTopologicalConstant(amendment: LogicalVariable): PatternIncomingContext =
    PatternIncomingContext(topologicalConstants + amendment, predicateConstants, pathConstants)

  @inline def amendedWithTopologicalConstant(amendment: Set[LogicalVariable]): PatternIncomingContext =
    PatternIncomingContext(topologicalConstants union amendment, predicateConstants, pathConstants)

  @inline def amendedWithPredicateConstant(amendment: LogicalVariable): PatternIncomingContext =
    PatternIncomingContext(topologicalConstants + amendment, predicateConstants, pathConstants)

  @inline def removePathConstants(): PatternIncomingContext =
    PatternIncomingContext(topologicalConstants, predicateConstants, unitVariables)

  @inline def resultScope(
    result: TableResult,
    children: Seq[WorkingScope],
    declared: Declarations,
    referencedInTopology: Iterable[LogicalVariable] = None
  )(
    implicit astNode: ASTNode
  ): PatternScope = {
    val referenced = (WorkingScope.referencedInChildren(children) diff declared.variables.toSet) ++ referencedInTopology
    PatternScope(astNode, this, referenced, declared, result, children)
  }

  def toRegularContext: RegularContext =
    RegularContext(
      constants = topologicalConstants union predicateConstants union pathConstants,
      variables = unitVariables
    )
}

object PatternIncomingContext {
  def unit: PatternIncomingContext = PatternIncomingContext(unitVariables, unitVariables, unitVariables)
}

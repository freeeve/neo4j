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

import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.Foldable
import org.neo4j.cypher.internal.util.InputPosition

case object DummyASTNode extends ASTNode {
  self =>
  override def position: InputPosition = InputPosition.NONE
  override def canEqual(that: Any): Boolean = false
  override def productArity: Int = 0
  override def productElement(n: Int): Any = ()
}

sealed trait WorkingScope extends Product with Foldable {
  def astNode: ASTNode
  def incoming: WorkingContext
  def referenced: Set[LogicalVariable]
  def declared: Declarations
  def outgoing: RegularContext
  def result: Result
  def children: Seq[WorkingScope]

  def withChildren(children: Seq[WorkingScope]): WorkingScope
  def withReferenced(referenced: Set[LogicalVariable]): WorkingScope
  def withDeclared(declared: Declarations): WorkingScope
}

object WorkingScope {
  val noChildren = Seq.empty[WorkingScope]

  @inline def apriori(outgoing: RegularContext): WorkingScope =
    AprioriScope(RegularContext.unit, outgoing)

  @inline def apriori(incoming: RegularContext, outgoing: RegularContext): WorkingScope =
    AprioriScope(incoming, outgoing)

  @inline def aprioriPattern(incoming: PatternIncomingContext, outgoing: RegularContext): PatternScope =
    PatternScope(
      astNode = DummyASTNode,
      patternIncoming = incoming,
      referenced = ScopeSurveyor.unitVariables,
      declared = Declarations.noDeclarations,
      // outgoing = outgoing,
      result = TableResult(Seq.empty),
      children = WorkingScope.noChildren
    )

  def referencedInChildren(children: Seq[WorkingScope]): Set[LogicalVariable] =
    children.foldLeft(Set.empty[LogicalVariable]) {
      (referenced, c2) => referenced union c2.referenced
    }
}

case class AprioriScope(incoming: RegularContext, outgoing: RegularContext) extends WorkingScope {
  override def astNode: ASTNode = DummyASTNode
  override def referenced: Set[LogicalVariable] = ScopeSurveyor.unitVariables
  override def declared: Declarations = Declarations.noDeclarations
  override def result: Result = NoResult
  override def children: Seq[WorkingScope] = WorkingScope.noChildren

  override def withChildren(children: Seq[WorkingScope]): AprioriScope = this
  override def withReferenced(referenced: Set[LogicalVariable]): AprioriScope = this
  override def withDeclared(declared: Declarations): AprioriScope = this
}

case class StatementScope(
  astNode: ASTNode,
  incoming: RegularContext,
  referenced: Set[LogicalVariable],
  declared: Declarations,
  outgoing: RegularContext,
  result: Result = NoResult,
  children: Seq[WorkingScope] = WorkingScope.noChildren
) extends WorkingScope {
  override def withChildren(children: Seq[WorkingScope]): StatementScope = copy(children = children)
  override def withReferenced(referenced: Set[LogicalVariable]): StatementScope = copy(referenced = referenced)
  override def withDeclared(declared: Declarations): StatementScope = copy(declared = declared)
}

case class ExpressionScope(
  astNode: ASTNode,
  incoming: RegularContext,
  referenced: Set[LogicalVariable],
  declared: Declarations,
  children: Seq[WorkingScope] = WorkingScope.noChildren
) extends WorkingScope {
  override def result: Result = ExpressionResult
  override def outgoing: RegularContext = RegularContext.unit
  def withAstNode(astNode: ASTNode): ExpressionScope = copy(astNode = astNode)
  override def withChildren(children: Seq[WorkingScope]): ExpressionScope = copy(children = children)
  override def withReferenced(referenced: Set[LogicalVariable]): ExpressionScope = copy(referenced = referenced)
  override def withDeclared(declared: Declarations): ExpressionScope = copy(declared = declared)
}

case class PatternScope(
  astNode: ASTNode,
  patternIncoming: PatternIncomingContext,
  referenced: Set[LogicalVariable],
  declared: Declarations,
  result: TableResult,
  children: Seq[WorkingScope] = WorkingScope.noChildren
) extends WorkingScope {
  override def incoming: RegularContext = patternIncoming.toRegularContext
  override def outgoing: RegularContext = RegularContext(ScopeSurveyor.unitVariables, result.columns.toSet)
  def withAstNode(astNode: ASTNode): PatternScope = copy(astNode = astNode)
  override def withChildren(children: Seq[WorkingScope]): PatternScope = copy(children = children)
  override def withReferenced(referenced: Set[LogicalVariable]): PatternScope = copy(referenced = referenced)
  override def withDeclared(declared: Declarations): PatternScope = copy(declared = declared)
}

case class UnexpectedAstNodeScopingError(astNode: ASTNode, incoming: RegularContext) extends WorkingScope {
  override def referenced: Set[LogicalVariable] = Set.empty
  override def declared: Declarations = Declarations.noDeclarations
  override def outgoing: RegularContext = incoming
  override def result: Result = NoResult
  override def children: Seq[WorkingScope] = WorkingScope.noChildren

  override def withChildren(children: Seq[WorkingScope]): UnexpectedAstNodeScopingError = this
  override def withReferenced(referenced: Set[LogicalVariable]): UnexpectedAstNodeScopingError = this
  override def withDeclared(declared: Declarations): UnexpectedAstNodeScopingError = this
}

sealed trait Result
case class TableResult(columns: Seq[LogicalVariable]) extends Result
case object TableResultWithNotYetKnownColumns extends Result
case object OmittedResult extends Result
case object NoResult extends Result
case object ExpressionResult extends Result

case class Declarations(constants: Seq[LogicalVariable], variables: Seq[LogicalVariable]) {
  @inline def isEmpty: Boolean = isVariablesEmpty && isConstantsEmpty
  @inline def isConstantsEmpty: Boolean = constants.isEmpty
  @inline def isVariablesEmpty: Boolean = variables.isEmpty
}

object Declarations {
  def noDeclarations: Declarations = Declarations(Seq.empty[LogicalVariable], Seq.empty[LogicalVariable])
}

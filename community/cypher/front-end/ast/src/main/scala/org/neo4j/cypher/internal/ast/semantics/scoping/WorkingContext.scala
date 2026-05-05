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
package org.neo4j.cypher.internal.ast.semantics.scoping

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.scoping.WorkingScope.noLocalCallables
import org.neo4j.cypher.internal.ast.semantics.scoping.WorkingScope.unitVariables
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition

sealed trait WorkingContext {
  def allSymbols: Set[LogicalVariable]
  val localCallables: Set[LocalCallableScopeSignature]
}

sealed trait RegularContext extends WorkingContext {
  val constants: Set[LogicalVariable]
  def constantSymbols: Set[LogicalVariable] = constants
  val variables: Set[LogicalVariable]
  override val localCallables: Set[LocalCallableScopeSignature]
  def projectionPart: ProjectionPart
  def getProjectionSpecification: Option[ProjectionSpecification]

  lazy val constantsAndVariables: Set[LogicalVariable] = constants union variables
  override def allSymbols: Set[LogicalVariable] = constantsAndVariables

  @inline def isEmpty: Boolean = isVariablesEmpty && isConstantsEmpty
  @inline def isConstantsEmpty: Boolean = constants.isEmpty
  @inline def isVariablesEmpty: Boolean = variables.isEmpty

  def isConstantForPart(expr: Expression, part: ProjectionPart): Boolean
  def isSubclauseAggregation: RegularContext

  def recognizeExpression(expr: Expression, isSubExpression: Boolean): Option[ProjectionItem]

  @inline def amendedWithLocalCallable(amendment: LocalCallableScopeSignature): RegularContext =
    RegularContext(constants, variables, localCallables + amendment)

  @inline def amendedWithLocalCallables(amendment: Set[LocalCallableScopeSignature]): RegularContext =
    RegularContext(constants, variables, localCallables union amendment)

  @inline def amendedWithConstant(amendment: LogicalVariable): RegularContext =
    RegularContext(constants + amendment, variables, localCallables)

  @inline def amendedWithConstant(amendment: Set[LogicalVariable]): RegularContext =
    RegularContext(constants union amendment, variables, localCallables)

  @inline def amendedWith(amendment: LogicalVariable): RegularContext =
    RegularContext(constants, variables + amendment, localCallables)

  @inline def amendedWith(amendment: Set[LogicalVariable]): RegularContext =
    RegularContext(constants, variables union amendment, localCallables)

  @inline def amendedWithProjectionSpecification(
    projectionSpecification: ProjectionSpecification,
    projectionPart: ProjectionPart
  ): ProjectionExpressionContext =
    ProjectionExpressionContext(constants, variables, localCallables, projectionSpecification, projectionPart)

  @inline def keepOnlyLocalCallable(): RegularContext = RegularContext(unitVariables, unitVariables, localCallables)

  @inline def constantChildContext(): RegularContext =
    RegularContext(constants union variables, unitVariables, localCallables)

  @inline def aggregatingConstantChildContext: RegularContext

  @inline def replaceWith(replacement: Set[LogicalVariable]): RegularContext =
    RegularContext(constants, replacement, localCallables)

  @inline def checkIfVariablesAreAlreadyDeclaredAsConstant(newVariables: Iterable[LogicalVariable])
    : Seq[SemanticError] = {
    checkIfVariablesAreAlreadyDeclaredIn(
      constants,
      newVariables,
      SemanticError.variableShadowingOuterScope
    )
  }

  @inline def checkIfVariablesAreAlreadyDeclaredAsVariable(
    newVariables: Iterable[LogicalVariable],
    errorFunc: (String, InputPosition) => SemanticError = (n, p) => SemanticError.variableAlreadyDeclared(n, p)
  )
    : Seq[SemanticError] = {
    checkIfVariablesAreAlreadyDeclaredIn(variables, newVariables, errorFunc)
  }

  @inline def checkIfVariablesHaveMultipleDeclarations(
    newVariables: Iterable[LogicalVariable],
    errorFunc: (String, InputPosition) => SemanticError = (n, p) => SemanticError.variableAlreadyDeclared(n, p)
  )
    : Seq[SemanticError] = {
    checkIfVariablesAreAlreadyDeclaredIn(variables, newVariables, errorFunc) ++
      checkIfNameOccursTwiceInDeclared(newVariables.toSeq, errorFunc)

  }

  @inline private def checkIfVariablesAreAlreadyDeclaredIn(
    existingVariables: Set[LogicalVariable],
    newVariables: Iterable[LogicalVariable],
    errorFunc: (String, InputPosition) => SemanticError
  ): Seq[SemanticError] = {
    if (existingVariables.nonEmpty) {
      newVariables.collect {
        case variable if existingVariables contains variable =>
          errorFunc(variable.name, variable.position)
      }.toSeq
    } else {
      Seq.empty
    }
  }

  @inline private def checkIfNameOccursTwiceInDeclared(
    newVariables: Seq[LogicalVariable],
    errorFunc: (String, InputPosition) => SemanticError
  ): Seq[SemanticError] = {
    if (newVariables.nonEmpty) {
      newVariables.collect {
        case variable if newVariables.exists(v => v.name == variable.name && v.position != variable.position) =>
          errorFunc(variable.name, variable.position)
      }
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

  def apply(
    constants: Set[LogicalVariable],
    variables: Set[LogicalVariable],
    localCallables: Set[LocalCallableScopeSignature]
  ): CommonContext =
    CommonContext(constants, variables, localCallables)

  def unit: RegularContext = RegularContext(unitVariables, unitVariables, noLocalCallables)
}

case class CommonContext(
  constants: Set[LogicalVariable],
  variables: Set[LogicalVariable],
  localCallables: Set[LocalCallableScopeSignature]
) extends RegularContext {

  override def isConstantForPart(expr: Expression, part: ProjectionPart): Boolean = expr match {
    case lv: LogicalVariable => constants(lv)
    case _                   => false
  }

  def isSubclauseAggregation: RegularContext = this

  def recognizeExpression(expr: Expression, isSubExpression: Boolean): Option[ProjectionItem] = None

  def projectionPart: ProjectionPart = NonAggregatingPart

  def getProjectionSpecification: Option[ProjectionSpecification] = None

  override def aggregatingConstantChildContext: RegularContext = constantChildContext()
}

sealed trait ProjectionPart {

  def isSubclause: Boolean = this match {
    case _: SubclausePart => true
    case _                => false
  }
}
trait ItemPart extends ProjectionPart
object AggregatingPart extends ItemPart
object NonAggregatingPart extends ItemPart
object GroupByPart extends ProjectionPart
trait SubclausePart extends ProjectionPart
object NonAggregatingSubclausePart extends SubclausePart
object AggregatingSubclausePart extends SubclausePart

case class ProjectionExpressionContext(
  constants: Set[LogicalVariable],
  variables: Set[LogicalVariable],
  localCallables: Set[LocalCallableScopeSignature],
  projectionSpecification: ProjectionSpecification,
  projectionPart: ProjectionPart
) extends RegularContext {

  override def isConstantForPart(expr: Expression, part: ProjectionPart): Boolean = {
    val partCheck: Expression => Boolean = part match {
      case _: SubclausePart   => x => projectionSpecification.isSubclauseRecognizable(x)
      case AggregatingPart    => x => projectionSpecification.isAggregationRecognizable(x)
      case NonAggregatingPart => x => projectionSpecification.isNonAggregatingRecognizable(x)
      case _                  => _ => false
    }

    expr match {
      case lv: LogicalVariable => constants(lv) || partCheck(lv)
      case expr                => partCheck(expr)
    }
  }

  def isSubclauseAggregation: RegularContext = copy(projectionPart = AggregatingSubclausePart)

  def getProjectionSpecification: Option[ProjectionSpecification] = Some(projectionSpecification)

  def recognizeExpression(that: Expression, isSubExpression: Boolean): Option[ProjectionItem] = {
    projectionPart match {
      case _ if !projectionSpecification.isAggregating => None
      case AggregatingPart =>
        projectionSpecification.recognizeInAggregation(that)
      case _: SubclausePart =>
        projectionSpecification.recognizeInSubclause(that, isSubExpression)
      case NonAggregatingPart =>
        projectionSpecification.recognizeInNonAggregatingItem(that, isSubExpression)
      case _ => None
    }

  }

  def groupByContext(): ProjectionExpressionContext = {
    val visibleSymbols = constants ++ variables ++ projectionSpecification.nonAggregatingItems.flatMap(_.alias)
    ProjectionExpressionContext(visibleSymbols, Set.empty, localCallables, projectionSpecification, projectionPart)
  }

  def nonAggregatingChildContext(): ProjectionExpressionContext = {
    val hasExplicitGroupingKeys = projectionSpecification.hasExplicitKeys
    ProjectionExpressionContext(
      if (hasExplicitGroupingKeys) constants else constants union variables,
      unitVariables,
      localCallables,
      projectionSpecification,
      NonAggregatingPart
    )
  }

  def projectionChildContext(): ProjectionExpressionContext = {
    val (childConstants, childVariables) =
      (projectionSpecification.isAggregating, projectionPart.isSubclause) match {
        case (false, false) =>
          (constants union variables, unitVariables)
        case (false, true) =>
          (constants union variables union projectionSpecification.subclauseScopeSymbols, unitVariables)
        case (true, false) =>
          (constants, variables)
        case (true, true) =>
          (constants union projectionSpecification.subclauseScopeSymbols, unitVariables)
      }

    ProjectionExpressionContext(
      childConstants,
      childVariables,
      localCallables,
      projectionSpecification,
      projectionPart
    )
  }

  override def amendedWithConstant(amendment: LogicalVariable): RegularContext =
    ProjectionExpressionContext(
      constants + amendment,
      variables,
      localCallables,
      projectionSpecification,
      projectionPart
    )

  override def amendedWithConstant(amendment: Set[LogicalVariable]): RegularContext =
    ProjectionExpressionContext(
      constants union amendment,
      variables,
      localCallables,
      projectionSpecification,
      projectionPart
    )

  override def amendedWith(amendment: LogicalVariable): RegularContext =
    ProjectionExpressionContext(
      constants,
      variables + amendment,
      localCallables,
      projectionSpecification,
      projectionPart
    )

  override def amendedWith(amendment: Set[LogicalVariable]): RegularContext =
    ProjectionExpressionContext(
      constants,
      variables union amendment,
      localCallables,
      projectionSpecification,
      projectionPart
    )

  override def aggregatingConstantChildContext: RegularContext = {
    val newConstants = projectionPart match {
      case NonAggregatingSubclausePart =>
        constants union projectionSpecification.aliases
      case AggregatingSubclausePart =>
        constants union variables union projectionSpecification.aliases
      case _ =>
        constants union variables
    }

    val newPart = if (projectionPart == AggregatingPart) NonAggregatingPart else projectionPart

    ProjectionExpressionContext(newConstants, unitVariables, localCallables, projectionSpecification, newPart)
  }

}

case class PatternIncomingContext(
  topologicalConstants: Set[LogicalVariable],
  predicateConstants: Set[LogicalVariable],
  pathConstants: Set[LogicalVariable],
  groupConstants: Set[LogicalVariable],
  override val localCallables: Set[LocalCallableScopeSignature]
) extends WorkingContext {

  override def allSymbols: Set[LogicalVariable] =
    topologicalConstants union predicateConstants union pathConstants union groupConstants

  @inline def amendedWithTopologicalConstant(amendment: LogicalVariable): PatternIncomingContext =
    PatternIncomingContext(
      topologicalConstants + amendment,
      predicateConstants,
      pathConstants,
      groupConstants,
      localCallables
    )

  @inline def amendedWithTopologicalConstants(amendment: Set[LogicalVariable]): PatternIncomingContext =
    PatternIncomingContext(
      topologicalConstants union amendment,
      predicateConstants,
      pathConstants,
      groupConstants,
      localCallables
    )

  @inline def amendedWithConstantAccordingToVersion(
    amendment: Set[LogicalVariable],
    version: CypherVersion
  ): PatternIncomingContext =
    PatternIncomingContext(
      topologicalConstants union amendment,
      // CREATE/MERGE pattern in Cypher5 where predicates of pattern part can refer to variables introduce in earlier pattern parts
      if (version == CypherVersion.Cypher5) predicateConstants union amendment else predicateConstants,
      pathConstants,
      groupConstants,
      localCallables
    )

  @inline def removePathConstants(): PatternIncomingContext =
    PatternIncomingContext(topologicalConstants, predicateConstants, unitVariables, groupConstants, localCallables)

  @inline def addPathConstants(amendment: Set[LogicalVariable]): PatternIncomingContext =
    PatternIncomingContext(
      topologicalConstants,
      predicateConstants,
      pathConstants ++ amendment,
      groupConstants,
      localCallables
    )

  @inline def addGroupConstants(amendment: Set[LogicalVariable]): PatternIncomingContext =
    PatternIncomingContext(
      topologicalConstants,
      predicateConstants,
      pathConstants,
      groupConstants ++ amendment,
      localCallables
    )

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
      variables = unitVariables,
      localCallables = localCallables
    )
}

object PatternIncomingContext {

  def unit: PatternIncomingContext =
    PatternIncomingContext(unitVariables, unitVariables, unitVariables, unitVariables, noLocalCallables)

}

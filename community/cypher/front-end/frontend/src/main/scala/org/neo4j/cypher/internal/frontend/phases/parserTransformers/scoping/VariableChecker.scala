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

import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.StrictlyAdditiveProjection
import org.neo4j.cypher.internal.ast.Unwind
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.ScopeQueries
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.util.Foldable.FoldingBehavior
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.StepSequencer

case object VariableChecker extends Phase[BaseContext, BaseState, BaseState] with StepSequencer.Step {

  override def process(from: BaseState, context: BaseContext): BaseState = {
    if (context.semanticFeatures contains ScopeQueries) {
      val semanticsErrors = if (1 == 1) {
        from.maybeWorkingScope.map(collectAll)
      } else {
        from.maybeWorkingScope.map(collectFirst)
      }
      semanticsErrors.foreach(errors => context.errorHandler(errors.toSeq))
    }
    from
  }

  private val checks: Seq[PartialFunction[WorkingScope, Seq[SemanticError]]] = Seq(
    // variable not defined
    {
      case ExpressionScope(variable: LogicalVariable, incoming, _, _, _)
        if !(incoming.constants contains variable) =>
        Seq(SemanticError.variableNotDefined(variable.name, variable.position))
    },
    // variable already declared
    {
      case StatementScope(astNode, incoming, _, d @ Declarations(constants, variables), _, _, _)
        if !d.isEmpty &&
          // expressions only declare for inner operands and allow shadowing
          !astNode.isInstanceOf[Expression] =>
        // redeclaration of constants
        val redeclarationOfConstants =
          incoming.checkIfVariablesAreAlreadyDeclaredAsConstant((constants ++ variables).toSet)
        // redeclaration of variables
        val redeclarationOfVariables = astNode match {
          case pc: ProjectionClause if pc.returnItems.projectionType == StrictlyAdditiveProjection =>
            incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(variables)
          case _: Unwind => incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(variables)
          case _         => Seq.empty
        }
        // multiple return columns in WITH
        val multipleDeclarations = {
          astNode match {
            case w: With => findMultipleDeclarationsIn(variables, w)
            case _       => Seq.empty
          }
        }
        redeclarationOfConstants ++ redeclarationOfVariables ++ multipleDeclarations
    },
    // multiple return columns
    {
      case StatementScope(w: With, _, _, Declarations(_, variables), _, _, _) =>
        findMultipleDeclarationsIn(variables, w)
      case StatementScope(r: Return, _, _, _, _, TableResult(columns), _) =>
        findMultipleDeclarationsIn(columns, r)
    }
  )

  // this collects all errors it can find
  private def collectAll(workingScope: WorkingScope): Iterable[SemanticError] = {
    workingScope.folder.treeCollect {
      Function.unlift {
        case ws: WorkingScope =>
          val errors = checks.foldLeft(Seq.empty[SemanticError]) {
            case (errors, check) =>
              errors ++ check.applyOrElse(ws, (_: WorkingScope) => Seq.empty[SemanticError])
          }
          Option.when(errors.nonEmpty)(errors)
        case _ => None
      }
    }.flatten
  }

  // this collects the first errors it can find
  private def collectFirst(workingScope: WorkingScope): Iterable[SemanticError] = {
    def perform(workingScope: WorkingScope)(previousError: Option[SemanticError])
      : FoldingBehavior[Option[SemanticError]] =
      previousError match {
        case None =>
          val errorOpt = checks.collectFirst(check => check(workingScope)).flatMap(_.headOption)
          if (errorOpt.isEmpty) {
            TraverseChildren(errorOpt)
          } else {
            SkipChildren(errorOpt)
          }
        case s @ Some(_) => SkipChildren(s)
      }

    workingScope.folder.treeFold(Option.empty[SemanticError]) {
      case ws: StatementScope => perform(ws)
    }
  }

  private def findMultipleDeclarationsIn(names: Seq[LogicalVariable], pc: ProjectionClause): Seq[SemanticError] =
    names.groupMapReduce(identity)(_ => 1)(_ + _).filter(_._2 > 1).map {
      _ => SemanticError.multipleReturnColumnsWithSameName(pc.returnItems.items.head.position)
    }.toSeq

  override val phase = CompilationPhase.VARIABLE_CHECK

  override def preConditions: Set[StepSequencer.Condition] = Set(BaseContains[WorkingScope]())

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def postConditions: Set[StepSequencer.Condition] = Set.empty
}

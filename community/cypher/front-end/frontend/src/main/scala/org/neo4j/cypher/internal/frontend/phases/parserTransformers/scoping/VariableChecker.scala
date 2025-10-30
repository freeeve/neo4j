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

import org.neo4j.cypher.internal.ast.CommandClause
import org.neo4j.cypher.internal.ast.ConditionalQueryBranch
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.CreateOrInsert
import org.neo4j.cypher.internal.ast.FullSubqueryExpression
import org.neo4j.cypher.internal.ast.ImportingWithSubqueryCall
import org.neo4j.cypher.internal.ast.Merge
import org.neo4j.cypher.internal.ast.NextStatement
import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.ScopeClauseSubqueryCall
import org.neo4j.cypher.internal.ast.StrictlyAdditiveProjection
import org.neo4j.cypher.internal.ast.SubqueryCall
import org.neo4j.cypher.internal.ast.TopLevelBraces
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.UnresolvedCall
import org.neo4j.cypher.internal.ast.Unwind
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.ScopeQueries
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NamedPatternPart
import org.neo4j.cypher.internal.expressions.QuantifiedPath
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.Foldable.FoldingBehavior
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildrenNewAccForSiblings
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
      case StatementScope(ScopeClauseSubqueryCall(_, false, imports, _, _), incoming, _, _, _, _, _)
        if imports.nonEmpty =>
        imports.filter(v => !incoming.allSymbols.exists(_.name == v.name))
          .flatMap(v => Seq(SemanticError.variableNotDefined(v.name, v.position)))
    },
    // variable already declared
    {
      case StatementScope(_: CreateOrInsert | _: Merge, _, ref, declared, _, _, _) if declared.isEmpty =>
        ref.map(v => SemanticError.variableAlreadyDeclared(v.name, v.position)).toSeq
      case StatementScope(_: CreateOrInsert | _: Merge, _, _, _, _, _, children) => children.flatMap { workingScope =>
          workingScope.folder.treeCollect {
            case PatternScope(RelationshipPattern(Some(variable), _, _, _, _, _), _, referenced, _, _, _)
              if referenced.exists(_.name == variable.name) =>
              SemanticError.variableAlreadyDeclared(variable.name, variable.position)
          }
        }
    },
    {
      case StatementScope(astNode, incoming, ref, d @ Declarations(constants, variables), _, _, children)
        if !d.isEmpty &&
          // expressions only declare for inner operands and allow shadowing
          !astNode.isInstanceOf[Expression] =>
        // redeclaration of constants
        val redeclarationOfConstants =
          incoming.checkIfVariablesAreAlreadyDeclaredAsConstant((constants ++ variables).toSet)
        // redeclaration of variables
        val redeclarationOfVariables = astNode match {
          case _: CommandClause =>
            incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(variables)
          case pc: ProjectionClause if pc.returnItems.projectionType == StrictlyAdditiveProjection =>
            incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(variables)
          case _: UnresolvedCall => incoming.checkIfVariablesHaveMultipleDeclarations(variables)
          case _: Unwind         => incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(variables)
          case _: SubqueryCall =>
            incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(
              variables,
              (n, p) => {
                if (children.head.outgoing.variables.exists(_.name == n))
                  SemanticError.variableAlreadyDeclaredInOuterScope(n, p) // Inner query throws 42N07
                else SemanticError.variableAlreadyDeclared(n, p) // IN TRANSACTIONS throw 42N59
              }
            )
          case _ => Seq.empty
        }
        // multiple return columns in WITH
        val multipleDeclarations = {
          astNode match {
            case w: With => findMultipleDeclarationsIn(variables, w)
            case _       => Seq.empty
          }
        }
        redeclarationOfConstants ++ redeclarationOfVariables ++ multipleDeclarations
      case PatternScope(NamedPatternPart(path, _), PatternScope.Topo(topo), _, Declarations(_, variables), _, _) =>
        (topo.filter(_.name == path.name) ++ variables.filter(x => x.name == path.name && x.position != path.position))
          .map(_ => SemanticError.variableAlreadyDeclared(path.name, path.position)).toSeq
      case PatternScope(QuantifiedPath(_, _, _, groupings), _, ref, _, _, _)
        if ref.intersect(groupings.map(_.group)).nonEmpty =>
        ref.intersect(groupings.map(_.group)).map(v =>
          SemanticError.variableAlreadyDeclared(v.name, v.position)
        ).toSeq
      case PatternScope(PatternScope.PatternVariable(v), PatternScope.Group(group), ref, _, _, _)
        if ref.intersect(group).exists(_.name == v.name) =>
        Seq(SemanticError.variableAlreadyDeclared(v.name, v.position))
    },

    // multiple return columns
    {
      case StatementScope(w: With, _, _, Declarations(_, variables), _, _, _) =>
        findMultipleDeclarationsIn(variables, w)
      case StatementScope(r: Return, _, _, _, _, TableResult(columns), _) =>
        findMultipleDeclarationsIn(columns, r)
    },
    // relationship variable already bound
    {
      case PatternScope(sppp @ ShortestPathsPatternPart(element, _), incoming, _, _, _, _) => element match {
          case RelationshipChain(_, rel, _) if rel.variable.exists(incoming.topologicalConstants.contains) =>
            Seq(SemanticError.relationshipVariableAlreadyBound(sppp.name, rel.position))
          case _ => Seq.empty
        }
    },
    // incompatible return columns
    {
      case StatementScope(u: Union, _, _, _, _, result, children) =>
        children.tail
          .filter(_.result != result)
          .flatMap { x =>
            (x.result, result) match {
              case (TableResult(tailCols), TableResult(headCols)) =>
                if (tailCols.map(_.name).toSet != headCols.map(_.name).toSet)
                  Seq(SemanticError.incompatibleReturnColumns(Union.errorParam, u.position))
                else
                  Seq.empty
              case _ =>
                Seq(SemanticError.incompatibleReturnColumns(Union.errorParam, u.position))
            }
          }
      case StatementScope(_: ConditionalQueryWhen, _, _, _, _, result, children) =>
        children.tail
          .filter(_.result != result)
          .flatMap { x =>
            (x.result, result) match {
              case (TableResult(tailCols), TableResult(headCols)) if tailCols.size != headCols.size =>
                Seq(SemanticError.incompatibleNumberOfReturnColumns(ConditionalQueryWhen.name, x.astNode.position))
              case (TableResult(tailCols), TableResult(headCols))
                if tailCols.map(_.name).toSet != headCols.map(_.name).toSet =>
                Seq(SemanticError.incompatibleWhenReturnColumns(ConditionalQueryWhen.name, x.astNode.position))
              case _ =>
                Seq(SemanticError.incompatibleSubqueryType(
                  ConditionalQueryWhen.name,
                  x.astNode.asInstanceOf[ConditionalQueryBranch].query.position
                ))
            }
          }
    }
  )

  case class Acc(scopeContext: String, errors: Seq[SemanticError]) {
    def apply(errors: Seq[SemanticError]): Acc = copy(errors = this.errors ++ errors)
    def apply(errors: SemanticError): Acc = copy(errors = this.errors :+ errors)
    def inContext(context: String): Acc = copy(scopeContext = context)
    def resetToIncoming(incoming: Acc): Acc = copy(scopeContext = incoming.scopeContext)
  }

  case object Acc {
    def init: Acc = Acc("SingleQuery", Seq.empty)
  }

  private val checksUsingAcc: Seq[PartialFunction[(Acc, WorkingScope), Acc]] = Seq(
    // invalid use of RETURN *
    {
      case (acc, ExpressionScope(_: FullSubqueryExpression, _, _, _, _)) =>
        acc.inContext("SubqueryExpression")
      case (acc @ Acc("SubqueryExpression", _), StatementScope(Return.WithStar(r), in, _, _, _, _, _)) =>
        if (in.constantsAndVariables.isEmpty) acc(SemanticError.invalidUseOfReturnStar(r.position))
        else acc
      case (acc, StatementScope(Return.WithStar(r), in, _, _, _, _, _))
        if in.isVariablesEmpty =>
        acc(SemanticError.invalidUseOfReturnStar(r.position))
    }
  )

  private def collectSemanticErrorsWithAcc(workingScope: WorkingScope) = workingScope.folder.treeFold(Acc.init) {
    case ws: WorkingScope => acc =>
        val updatedAcc = checksUsingAcc.foldLeft(acc) { case (acc, check) =>
          check.applyOrElse((acc, ws), (_: (Acc, WorkingScope)) => acc)
        }
        TraverseChildrenNewAccForSiblings(updatedAcc, siblingAcc => siblingAcc.resetToIncoming(acc))
  }

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
    }.flatten ++ collectSemanticErrorsWithAcc(workingScope).errors
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

case object VariableASTChecker extends Phase[BaseContext, BaseState, BaseState] with StepSequencer.Step {

  override def process(from: BaseState, context: BaseContext): BaseState = {
    val semanticsErrors = from.statement().folder.treeCollect {
      Function.unlift {
        case ast: ASTNode =>
          val errors = checks.foldLeft(Seq.empty[SemanticError]) {
            case (errors, check) =>
              errors ++ check.applyOrElse(ast, (_: ASTNode) => Seq.empty[SemanticError])
          }
          Option.when(errors.nonEmpty)(errors)
        case _ => None
      }
    }.flatten
    semanticsErrors.foreach(errors => context.errorHandler(Seq(errors)))

    from
  }

  val checks: Seq[PartialFunction[ASTNode, Seq[SemanticError]]] = Seq(
    {
      case ImportingWithSubqueryCall(ns: NextStatement, _, _) =>
        Seq(SemanticError.invalidUseOfOldCall("NEXT", ns.position))
      case ImportingWithSubqueryCall(tlb: TopLevelBraces, _, _) =>
        Seq(SemanticError.invalidUseOfOldCall("{ ... }", tlb.position))
      case ImportingWithSubqueryCall(cqw: ConditionalQueryWhen, _, _) =>
        Seq(SemanticError.invalidUseOfOldCall("WHEN ... THEN ...", cqw.position))
    },
    {
      case li: ReturnItems =>
        li.items.filter(item => item.alias.isEmpty).map(i => {
          SemanticError.unaliasedReturnItem("WITH OR MAYBE RETURN", i.position)
        })
    }
  )

  override val phase = CompilationPhase.VARIABLE_CHECK

  override def preConditions: Set[StepSequencer.Condition] = Set(BaseContains[WorkingScope]())

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def postConditions: Set[StepSequencer.Condition] = Set.empty
}

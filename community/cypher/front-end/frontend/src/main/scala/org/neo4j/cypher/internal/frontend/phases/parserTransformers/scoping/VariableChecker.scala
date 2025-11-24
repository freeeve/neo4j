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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.CommandClause
import org.neo4j.cypher.internal.ast.ConditionalQueryBranch
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.CreateOrInsert
import org.neo4j.cypher.internal.ast.FullSubqueryExpression
import org.neo4j.cypher.internal.ast.Merge
import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItem
import org.neo4j.cypher.internal.ast.ScopeClauseSubqueryCall
import org.neo4j.cypher.internal.ast.Search
import org.neo4j.cypher.internal.ast.StrictlyAdditiveProjection
import org.neo4j.cypher.internal.ast.SubqueryCall
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.UnresolvedCall
import org.neo4j.cypher.internal.ast.Unwind
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.ScopeQueries
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NamedPatternPart
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.PatternExpression
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.QuantifiedPath
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.util.Foldable
import org.neo4j.cypher.internal.util.Foldable.FoldingBehavior
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildrenNewAccForSiblings
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlParams.StringParam

case class VariableChecker(version: CypherVersion) {

  private type VariableCheck = PartialFunction[WorkingScope, Seq[SemanticError]]

  private val variableAlreadyDeclared: VariableCheck = {
    case StatementScope(_: CreateOrInsert | _: Merge, _, ref, declared, _, _, _) if declared.isEmpty =>
      ref.map(v => SemanticError.variableAlreadyDeclared(v.name, v.position)).toSeq
    case StatementScope(_: CreateOrInsert | _: Merge, _, _, _, _, _, children) => children.flatMap { workingScope =>
        val fold = workingScope.folder.treeFold((Seq.empty[SemanticError], false)) {
          case ExpressionScope(_: FullSubqueryExpression, _, _, _, _) => acc => SkipChildren(acc)
          case PatternScope(RelationshipPattern(Some(variable), _, _, _, _, _), _, referenced, _, _, _)
            if referenced.exists(_.name == variable.name) =>
            acc =>
              TraverseChildren((
                acc._1 ++ Seq(SemanticError.variableAlreadyDeclared(variable.name, variable.position)),
                acc._2
              ))
          case PatternScope(_: RelationshipChain, _, _, _, _, _) =>
            acc => TraverseChildren((acc._1, true))
          case PatternScope(NodePattern(Some(variable), _, _, _), _, referenced, _, _, _)
            if referenced.exists(_.name == variable.name) =>
            acc =>
              if (!acc._2)
                TraverseChildren((
                  acc._1 ++ Seq(SemanticError.variableAlreadyDeclared(variable.name, variable.position)),
                  acc._2
                ))
              else TraverseChildren(acc)
        }
        fold._1
      }
  }

  private val redeclarationOfVariable: VariableCheck = {
    case StatementScope(astNode, incoming, _, d @ Declarations(constants, variables), _, _, children)
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
        case _: Search         => incoming.checkIfVariablesAreAlreadyDeclaredAsVariable(variables)
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
    case StatementScope(_: CommandClause, incoming, _, _, _, _, children) =>
      val innerResult = children.last.result match {
        case TableResult(columns) => columns
        case _                    => Seq.empty
      }
      innerResult.filter(x => incoming.allSymbols.exists(_.name == x.name)).map(v =>
        SemanticError.variableAlreadyDeclared(v.name, v.position)
      )

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
  }

  private val multipleReturnColumns: VariableCheck = {
    case StatementScope(w: With, _, _, Declarations(_, variables), _, _, _) =>
      findMultipleDeclarationsIn(variables, w)
    case StatementScope(y: Yield, _, _, _, _, TableResult(columns), _) =>
      findMultipleDeclarationsIn(columns, y)
    case StatementScope(r: Return, _, _, _, _, TableResult(columns), _) =>
      findMultipleDeclarationsIn(columns, r)
  }

  private val relationshipVariableAlreadyBound: VariableCheck = {
    case PatternScope(sppp @ ShortestPathsPatternPart(element, _), incoming, _, _, _, _) => element match {
        case RelationshipChain(_, rel, _) if rel.variable.exists(incoming.topologicalConstants.contains) =>
          Seq(SemanticError.relationshipVariableAlreadyBound(sppp.name, rel.position))
        case _ => Seq.empty
      }
  }

  private val incompatibleReturnColumns: VariableCheck = {
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
            case (res1, res2) if res1.getClass == res2.getClass => Seq()
            case _ =>
              Seq(SemanticError.incompatibleSubqueryType(
                ConditionalQueryWhen.name,
                x.astNode.asInstanceOf[ConditionalQueryBranch].query.position
              ))
          }
        }
  }

  private val unboundVariablesInPatternExpression: VariableCheck = {
    case ExpressionScope(_: PatternExpression, incoming, ref, _, _) =>
      ref.filter(!incoming.constants.contains(_)).map(v =>
        SemanticError.unboundVariablesInPatternExpression(v.name, v.position)
      ).toSeq
  }

  private val checks: Seq[VariableCheck] = Seq(
    variableAlreadyDeclared,
    redeclarationOfVariable,
    multipleReturnColumns,
    relationshipVariableAlreadyBound,
    incompatibleReturnColumns,
    unboundVariablesInPatternExpression
  )

  sealed trait ReturnContext
  private case object Unopinionated extends ReturnContext

  sealed trait Opinionated extends ReturnContext {
    val constants: Set[LogicalVariable]
  }
  case class SubqueryExpression(override val constants: Set[LogicalVariable]) extends Opinionated
  case class NextStatement(override val constants: Set[LogicalVariable]) extends Opinionated

  sealed trait VariableContext
  case object Default extends VariableContext

  private case class UpdatingPattern(
    topology: Set[LogicalVariable],
    patternVariables: Set[LogicalVariable],
    ast: Clause
  ) extends VariableContext

  sealed trait ProjectionContext
  case class Aggregating(clause: String) extends ProjectionContext
  case object NonAggregating extends ProjectionContext

  case class Acc(
    scopeContext: ReturnContext,
    variableContext: VariableContext,
    projectionContext: ProjectionContext,
    errors: Seq[SemanticError]
  ) {
    def apply(errors: Seq[SemanticError]): Acc = copy(errors = this.errors ++ errors)
    def apply(errors: SemanticError): Acc = copy(errors = this.errors :+ errors)
    def inReturnContext(context: ReturnContext): Acc = copy(scopeContext = context)
    def inVariableContext(context: VariableContext): Acc = copy(variableContext = context)
    def inProjectionContext(context: ProjectionContext): Acc = copy(projectionContext = context)

    def withPatternVariables(vars: Set[LogicalVariable]): Acc = variableContext match {
      case u: UpdatingPattern => copy(variableContext = u.copy(patternVariables = vars))
      case Default            => this
    }

    def hasPatternVariables: Boolean = variableContext match {
      case UpdatingPattern(_, vars, _) if vars.nonEmpty => true
      case _                                            => false
    }
    def resetToIncoming(incoming: Acc): Acc = copy(scopeContext = incoming.scopeContext)
  }

  case object Acc {
    def init: Acc = Acc(Unopinionated, Default, NonAggregating, Seq.empty)
  }

  private type VariableCheckWithAcc = PartialFunction[(Acc, WorkingScope), Acc]

  private val invalidUseOfReturnStar: VariableCheckWithAcc = {
    case (acc @ Acc(SubqueryExpression(_), _, _, _), StatementScope(Return.WithStar(r), in, _, _, _, _, _))
      if in.constantsAndVariables.isEmpty => acc(SemanticError.invalidUseOfReturnStar(r.position))
    case (acc @ Acc(o: Opinionated, _, _, _), StatementScope(r @ Return(_, ri, _, _, _, _, _, _), _, _, _, _, _, _)) =>
      acc(getAliasesShadowingConstants(ri.items, o.constants, r.position))
    case (acc, StatementScope(Return.WithStar(r), in, _, _, _, _, _))
      if in.isVariablesEmpty =>
      acc(SemanticError.invalidUseOfReturnStar(r.position))
  }

  private val variableNotDefined: VariableCheckWithAcc = {
    case (
        acc @ Acc(_, UpdatingPattern(topo, patternVariables, c: CreateOrInsert), _, _),
        ExpressionScope(variable: LogicalVariable, incoming, _, _, _)
      )
      if !(incoming.constants contains variable) =>
      if (topo contains variable) {
        if (!(patternVariables contains variable) && version == CypherVersion.Cypher5) acc
        else
          acc(SemanticError.invalidEntityReference(variable.name, c.name, variable.position))
      } else {
        acc(SemanticError.variableNotDefined(variable.name, variable.position))
      }
    case (
        acc @ Acc(_, UpdatingPattern(topo, _, m: Merge), _, _),
        ExpressionScope(variable: LogicalVariable, incoming, _, _, _)
      ) if !(incoming.constants contains variable) =>
      if (topo contains variable) {
        if (version == CypherVersion.Cypher5) acc
        else
          acc(SemanticError.invalidEntityReference(variable.name, m.name, variable.position))
      } else {
        acc(SemanticError.variableNotDefined(variable.name, variable.position))
      }
    case (
        acc @ Acc(_, _, Aggregating(clause), _),
        ExpressionScope(property: Property, AggregatingExpressionContext(_, _, groupingKeys, _), _, _, Seq())
      ) =>
      if (!(groupingKeys contains property)) {
        val stringifier = ExpressionStringifier()
        acc(SemanticError.inaccessibleVariable(stringifier(property.map), clause, property.position))
      } else acc
    case (
        acc @ Acc(_, _, Aggregating(clause), _),
        ExpressionScope(
          variable: LogicalVariable,
          AggregatingExpressionContext(constants, incoming, groupingKeys, _),
          _,
          _,
          _
        )
      ) =>
      if (!(constants contains variable) && !(groupingKeys contains variable)) {
        if (incoming contains variable)
          acc(SemanticError.inaccessibleVariable(variable.name, clause, variable.position))
        else
          acc(SemanticError.variableNotDefined(variable.name, variable.position))
      } else acc
    case (
        acc @ Acc(_, _, Aggregating(clause), _),
        ExpressionScope(variable: LogicalVariable, in, _, _, _)
      ) =>
      if (!(in.constants contains variable)) {
        if (in.variables contains variable)
          acc(SemanticError.inaccessibleVariable(variable.name, clause, variable.position))
        else
          acc(SemanticError.variableNotDefined(variable.name, variable.position))
      } else acc
    case (acc, ExpressionScope(variable: LogicalVariable, incoming, _, _, _))
      if !(incoming.constants contains variable) =>
      acc(SemanticError.variableNotDefined(variable.name, variable.position))
    case (acc, StatementScope(ScopeClauseSubqueryCall(_, false, imports, _, _), incoming, _, _, _, _, _))
      if imports.nonEmpty =>
      acc(imports.filter(v => !incoming.allSymbols.exists(_.name == v.name))
        .flatMap(v => Seq(SemanticError.variableNotDefined(v.name, v.position))))
  }

  private val checksUsingAcc: Seq[VariableCheckWithAcc] = Seq(
    invalidUseOfReturnStar,
    variableNotDefined
  )

  private def getAliasesShadowingConstants(
    items: Seq[ReturnItem],
    constants: Set[LogicalVariable],
    pos: InputPosition
  ): Seq[SemanticError] =
    items
      .filter(i => !i.isPassThrough && i.alias.isDefined && constants.contains(i.alias.get))
      .map(i => SemanticError.variableAlreadyDeclaredInOuterScope(i.name, pos))

  private def collectSemanticErrorsWithAcc(workingScope: WorkingScope) = workingScope.folder.treeFold(Acc.init) {
    case ExpressionScope(_: FullSubqueryExpression, in, _, _, _) => acc =>
        TraverseChildren(acc.inReturnContext(SubqueryExpression(in.allSymbols)))
    case StatementScope(_: NextStatement, in, _, _, _, _, children) => acc =>
        val trunkAcc = folderWorkingScopes(children.dropRight(1), acc.inReturnContext(NextStatement(in.constants)))
        val tailAcc = folderWorkingScopes(children.tail, acc)
        SkipChildren(Acc(
          tailAcc.scopeContext,
          tailAcc.variableContext,
          tailAcc.projectionContext,
          trunkAcc.errors ++ tailAcc.errors
        ))
    case StatementScope(c: CreateOrInsert, _, _, declared, _, _, _) => acc =>
        TraverseChildrenNewAccForSiblings(
          acc.inVariableContext(UpdatingPattern(declared.variables.toSet, Set.empty, c)),
          _acc => _acc.inVariableContext(acc.variableContext)
        )
    case StatementScope(m: Merge, _, _, declared, _, _, _) => acc =>
        TraverseChildrenNewAccForSiblings(
          acc.inVariableContext(UpdatingPattern(declared.variables.toSet, Set.empty, m)),
          _acc => _acc.inVariableContext(acc.variableContext)
        )
    case s @ StatementScope(p: ProjectionClause, _, _, _, _, _, children) => acc =>
        val checkSelf = checksUsingAcc.foldLeft(acc) { case (acc, check) =>
          check.applyOrElse((acc, s), (_: (Acc, WorkingScope)) => acc)
        }

        if (p.isAggregating) {
          val context = Aggregating(p.name)

          // Partition children into groupingItems, aggregatingItems and subclauseItems
          val (groupingItems, other) = children.partition(_.incoming.isInstanceOf[CommonContext])
          val (subclauses, aggregatingItems) =
            other.partition(_.incoming.asInstanceOf[AggregatingExpressionContext].inSubclause)

          val groupingItemAcc = folderWorkingScopes(groupingItems, checkSelf)
          val aggregatingItemAcc = folderWorkingScopes(aggregatingItems, checkSelf.inProjectionContext(context))
          val subclauseAcc = folderWorkingScopes(subclauses, checkSelf.inProjectionContext(context))

          val (inaccessibleVariable, otherErrors) =
            aggregatingItemAcc.errors.partition(_.gqlStatusObject.cause().get().gqlStatus() == "42N44")

          val invalidReference = Option.when(inaccessibleVariable.nonEmpty) {
            val variableNames: Seq[String] =
              inaccessibleVariable.map(
                _.gqlStatusObject.cause().get()
                  .asInstanceOf[ErrorGqlStatusObjectImplementation]
                  .getParamValue(StringParam.variable)
                  .asInstanceOf[String]
              )
            SemanticError.invalidReferenceToGroupingExpression(variableNames, inaccessibleVariable.head.position)
          }

          val aggErrors = otherErrors ++ invalidReference

          SkipChildren(checkSelf(groupingItemAcc.errors ++ aggErrors ++ subclauseAcc.errors))
        } else {
          TraverseChildrenNewAccForSiblings(
            checkSelf.inProjectionContext(NonAggregating),
            _acc => {
              _acc.inProjectionContext(acc.projectionContext)
            }
          )
        }

    case PatternScope(_: RelationshipChain, _, _, Declarations(_, variables), _, _) => acc =>
        TraverseChildrenNewAccForSiblings(
          if (acc.hasPatternVariables) acc else acc.withPatternVariables(variables.toSet),
          _acc => _acc.inVariableContext(acc.variableContext)
        )
    case PatternScope(_: NodePattern, _, _, Declarations(_, variables), _, _) => acc =>
        TraverseChildrenNewAccForSiblings(
          if (acc.hasPatternVariables) acc else acc.withPatternVariables(variables.toSet),
          _acc => _acc.inVariableContext(acc.variableContext)
        )
    case ws: WorkingScope => _folderWorkingScopes(ws)
  }

  private def folderWorkingScopes(target: Foldable, acc: Acc) =
    target.folder.treeFold(acc) {
      case ws: WorkingScope => _folderWorkingScopes(ws)
    }

  private def _folderWorkingScopes(ws: WorkingScope): Acc => FoldingBehavior[Acc] = {
    acc =>
      TraverseChildrenNewAccForSiblings[Acc](
        checksUsingAcc.foldLeft(acc) { case (acc, check) =>
          check.applyOrElse((acc, ws), (_: (Acc, WorkingScope)) => acc)
        },
        siblingAcc => siblingAcc.resetToIncoming(acc)
      )
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
}

case object VariableChecker extends Phase[BaseContext, BaseState, BaseState] with StepSequencer.Step {

  override def process(from: BaseState, context: BaseContext): BaseState = {
    if (context.semanticFeatures contains ScopeQueries) {
      val semanticsErrors = if (1 == 1) {
        from.maybeScopeState.map(s =>
          VariableChecker(context.cypherVersion).collectAll(s.workingScope)
        )
      } else {
        from.maybeScopeState.map(s =>
          VariableChecker(context.cypherVersion).collectFirst(s.workingScope)
        )
      }
      semanticsErrors.foreach(errors => context.errorHandler(errors.toSeq))
    }
    from
  }

  override val phase = CompilationPhase.VARIABLE_CHECK

  override def preConditions: Set[StepSequencer.Condition] = Set(BaseContains[WorkingScope]())

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def postConditions: Set[StepSequencer.Condition] = Set.empty
}

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
import org.neo4j.cypher.internal.ast.CommandClause
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.CreateOrInsert
import org.neo4j.cypher.internal.ast.FullSubqueryExpression
import org.neo4j.cypher.internal.ast.Merge
import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.Return
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
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.PatternExpression
import org.neo4j.cypher.internal.expressions.RelationshipChain
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
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlParams.StringParam

case class VariableChecker(version: CypherVersion) extends VariableCheckerUtil {

  private val redeclarationOfVariable: VariableCheck = {
    case (acc, Scope.Clause.Declaring(astNode, incoming, Declarations(constants, variables, _), children))
      if !(constants.isEmpty && variables.isEmpty) =>
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
      acc(redeclarationOfConstants ++ redeclarationOfVariables)
    case (acc, Scope.Clause.Command(incoming, children)) =>
      val innerResult = children.last.result match {
        case TableResult(columns) => columns
        case _                    => Seq.empty
      }
      acc(innerResult.filter(x => incoming.allSymbols.exists(_.name == x.name)).map(v =>
        SemanticError.variableAlreadyDeclared(v.name, v.position)
      ))
    case (acc, Scope.Clause.Updating(referenced, declared, _)) if declared.isEmpty =>
      acc(referenced.map(v => SemanticError.variableAlreadyDeclared(v.name, v.position)).toSeq)
  }

  private val multipleReturnColumns: VariableCheck = {
    case (acc, StatementScope(w: With, _, _, Declarations(_, variables, _), _, _, _)) =>
      acc(findMultipleDeclarationsIn(variables, w))
    case (acc, StatementScope(y: Yield, _, _, _, _, TableResult(columns), _)) =>
      acc(findMultipleDeclarationsIn(columns, y))
    case (acc, StatementScope(r: Return, _, _, _, _, TableResult(columns), _)) =>
      acc(findMultipleDeclarationsIn(columns, r))
  }

  private val incompatibleReturnColumns: VariableCheck = {
    case (acc, StatementScope(u: Union, _, _, _, _, result, children)) =>
      acc(getIncompatibleReturnColumnsForUnion(u.position, result, children))
    case (acc, StatementScope(_: ConditionalQueryWhen, _, _, _, _, result, children)) =>
      acc(getIncompatibleReturnColumnsForConditionalQuery(result, children))
  }

  private val invalidUseOfReturnStar: VariableCheck = {
    case (Acc.SubqueryExpr(acc), Scope.Clause.ReturnStar(incoming, position))
      if incoming.constantsAndVariables.isEmpty => acc(SemanticError.invalidUseOfReturnStar(position))
    case (Acc.Opinionated(acc, constants), Scope.Clause.ReturnItems(items, position)) =>
      acc(getAliasesShadowingConstants(items, constants, position))
    case (acc, Scope.Clause.ReturnStar(incoming, position)) if incoming.isVariablesEmpty =>
      acc(SemanticError.invalidUseOfReturnStar(position))
  }

  private val variableNotDefinedInScopeClause: VariableCheck = {
    case (acc, Scope.Clause.SubqueryCall(imports, incoming)) if imports.nonEmpty =>
      acc(imports.filter(v => !incoming.allSymbols.exists(_.name == v.name))
        .flatMap(v => Seq(SemanticError.variableNotDefined(v.name, v.position))))
  }

  private val statementChecks: Seq[VariableCheck] = Seq(
    redeclarationOfVariable,
    multipleReturnColumns,
    incompatibleReturnColumns,
    invalidUseOfReturnStar,
    variableNotDefinedInScopeClause
  )

  private val unboundVariablesInPatternExpression: VariableCheck = {
    case (acc, ExpressionScope(_: PatternExpression, incoming, ref, _, _)) =>
      acc(ref.filter(!incoming.constants.contains(_)).map(v =>
        SemanticError.unboundVariablesInPatternExpression(v.name, v.position)
      ).toSeq)
  }

  private val variableNotDefined: VariableCheck = {
    case (
        Acc.CreatePattern(acc, topo, patternVariables, create, inScalarSubquery),
        Scope.Expr.Variable(variable, incoming)
      ) =>
      val isIncoming = incoming.constants contains variable
      val declaredInSameGraphPattern = topo contains variable
      val declaredInSamePathPattern = patternVariables contains variable
      (isIncoming, declaredInSameGraphPattern, inScalarSubquery, declaredInSamePathPattern, version) match {
        case (true, false, _, _, _)                         => acc
        case (_, true, false, false, CypherVersion.Cypher5) => acc
        case (_, false, _, _, _) => acc(SemanticError.variableNotDefined(variable.name, variable.position))
        case _ => acc(SemanticError.invalidEntityReference(variable.name, create.name, variable.position))
      }
    case (
        Acc.MergePattern(acc, topo, merge),
        Scope.Expr.Variable(variable, incoming)
      ) if !(incoming.constants contains variable) =>
      if (topo contains variable) {
        if (version == CypherVersion.Cypher5) acc
        else
          acc(SemanticError.invalidEntityReference(variable.name, merge.name, variable.position))
      } else {
        acc(SemanticError.variableNotDefined(variable.name, variable.position))
      }
    case (
        Acc.Aggregation(acc, clause),
        Scope.Expr.PropertyAggregation(property, groupingKeys)
      ) if !(groupingKeys contains property) =>
      val stringifier = ExpressionStringifier()
      acc(SemanticError.inaccessibleVariable(stringifier(property.map), clause, property.position))
    case (
        Acc.Aggregation(acc, clause),
        Scope.Expr.VariableAggregation(variable, constants, incoming, groupingKeys)
      ) =>
      if (!(constants contains variable) && !(groupingKeys contains variable)) {
        if (incoming contains variable)
          acc(SemanticError.inaccessibleVariable(variable.name, clause, variable.position))
        else
          acc(SemanticError.variableNotDefined(variable.name, variable.position))
      } else acc
    case (Acc.Aggregation(acc, clause), Scope.Expr.Variable(variable, in)) if !(in.constants contains variable) =>
      if (in.variables contains variable)
        acc(SemanticError.inaccessibleVariable(variable.name, clause, variable.position))
      else
        acc(SemanticError.variableNotDefined(variable.name, variable.position))
    case (acc, Scope.Expr.Variable(variable, incoming)) if !(incoming.constants contains variable) =>
      acc(SemanticError.variableNotDefined(variable.name, variable.position))
  }

  private val expressionChecks: Seq[VariableCheck] =
    Seq(
      unboundVariablesInPatternExpression,
      variableNotDefined
    )

  private val redeclarationOfVariablesInPatterns: VariableCheck = {
    case (acc, Scope.Pattern.NamedPath(path, topo, Declarations(_, variables, _))) =>
      acc((topo.filter(_.name == path.name) ++ variables.filter(x =>
        x.name == path.name && x.position != path.position
      ))
        .map(_ => SemanticError.variableAlreadyDeclared(path.name, path.position)).toSeq)
    case (acc, Scope.Pattern.Quantified(groupings, referenced))
      if referenced.intersect(groupings.map(_.group)).nonEmpty =>
      acc(referenced.intersect(groupings.map(_.group)).map(v =>
        SemanticError.variableAlreadyDeclared(v.name, v.position)
      ).toSeq)
    case Scope.Pattern.VariableInPatternAlreadyDeclared(acc, name, position) =>
      acc(SemanticError.variableAlreadyDeclared(name, position))
    case (acc, Scope.Pattern.Element(variable, group, referenced))
      if referenced.intersect(group).exists(_.name == variable.name) =>
      acc(SemanticError.variableAlreadyDeclared(variable.name, variable.position))
  }

  private val relationshipVariableAlreadyBound: VariableCheck = {
    case (acc, Scope.Pattern.ShortestPath(name, element, topologicalConstants)) => element match {
        case RelationshipChain(_, rel, _) if rel.variable.exists(topologicalConstants.contains) =>
          acc(SemanticError.relationshipVariableAlreadyBound(name, rel.position))
        case _ => acc
      }
  }

  private val patternChecks: Seq[VariableCheck] =
    Seq(
      redeclarationOfVariablesInPatterns,
      relationshipVariableAlreadyBound
    )

  private def checkFold(s: WorkingScope, acc: Acc, checks: Seq[VariableCheck]): Acc =
    checks.foldLeft(acc) { case (acc, check) =>
      check.applyOrElse((acc, s), (_: (Acc, WorkingScope)) => acc)
    }

  private def checkWorkingScope(acc: Acc, workingScope: WorkingScope): Acc = workingScope match {
    case ss: StatementScope  => checkFold(ss, acc, statementChecks)
    case es: ExpressionScope => checkFold(es, acc, expressionChecks)
    case ps: PatternScope    => checkFold(ps, acc, patternChecks)
    case _                   => acc
  }

  private def updateAccAndTraverse(
    acc: Acc,
    scope: WorkingScope
  )(foldingBehavior: Acc => FoldingBehavior[Acc]): FoldingBehavior[Acc] = {
    val checkSelf = checkWorkingScope(acc, scope)
    foldingBehavior(checkSelf)
  }

  private def folderWorkingScopes(acc: Acc, target: Foldable): Acc =
    target.folder.treeFold(acc) {
      case ws: WorkingScope => acc => TraverseChildren(checkWorkingScope(acc, ws))
    }

  private def collectSemanticErrors(workingScope: WorkingScope) = workingScope.folder.treeFold(Acc.init) {
    case s @ ExpressionScope(_: FullSubqueryExpression, in, _, _, _) => acc =>
        updateAccAndTraverse(acc, s)(_acc =>
          TraverseChildrenNewAccForSiblings(
            _acc.inReturnContext(SubqueryExpression(in.allSymbols)),
            acc => acc.inReturnContext(_acc.scopeContext)
          )
        )
    case s @ StatementScope(_: NextStatement, in, _, _, _, _, children) => acc =>
        updateAccAndTraverse(acc, s)(_acc => {
          val trunkAcc = folderWorkingScopes(_acc.inReturnContext(NextStatement(in.constants)), children.dropRight(1))
          val tailAcc = folderWorkingScopes(_acc, children.tail)
          SkipChildren(Acc(
            tailAcc.scopeContext,
            tailAcc.variableContext,
            tailAcc.projectionContext,
            trunkAcc.errors ++ tailAcc.errors
          ))
        })
    case s @ StatementScope(c: CreateOrInsert, _, _, declared, _, _, _) => acc =>
        updateAccAndTraverse(acc, s)(_acc =>
          TraverseChildrenNewAccForSiblings(
            _acc.inVariableContext(UpdatingPattern(declared.variables.toSet, Set.empty, c)),
            acc => acc.inVariableContext(_acc.variableContext)
          )
        )
    case s @ StatementScope(m: Merge, _, _, declared, _, _, _) => acc =>
        updateAccAndTraverse(acc, s)(_acc =>
          TraverseChildrenNewAccForSiblings(
            _acc.inVariableContext(UpdatingPattern(declared.variables.toSet, Set.empty, m)),
            acc => acc.inVariableContext(_acc.variableContext)
          )
        )
    case s @ StatementScope(p: ProjectionClause, _, _, _, _, _, children) => acc =>
        updateAccAndTraverse(acc, s)(_acc =>
          if (p.isAggregating) {
            val context = Aggregating(p.name)

            // Partition children into groupingItems, aggregatingItems and subclauseItems
            val (groupingItems, other) = children.partition(_.incoming.isInstanceOf[CommonContext])
            val (subclauses, aggregatingItems) =
              other.partition(_.incoming.asInstanceOf[AggregatingExpressionContext].inSubclause)

            val groupingItemAcc = folderWorkingScopes(_acc, groupingItems)
            val aggregatingItemAcc = folderWorkingScopes(_acc.inProjectionContext(context), aggregatingItems)
            val subclauseAcc = folderWorkingScopes(_acc.inProjectionContext(context), subclauses)

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

            SkipChildren(_acc(groupingItemAcc.errors ++ aggErrors ++ subclauseAcc.errors))
          } else {
            TraverseChildrenNewAccForSiblings(
              _acc.inProjectionContext(NonAggregating),
              acc => {
                acc.inProjectionContext(_acc.projectionContext)
              }
            )
          }
        )

    case s @ PatternScope(_: RelationshipChain, _, _, Declarations(_, variables, _), _, _) => acc =>
        updateAccAndTraverse(acc, s)(_acc =>
          TraverseChildrenNewAccForSiblings(
            if (_acc.hasPatternVariables) _acc else _acc.withPatternVariables(variables.toSet, inRelationship = true),
            acc => acc.inVariableContext(_acc.variableContext)
          )
        )
    case s @ PatternScope(_: NodePattern, _, _, Declarations(_, variables, _), _, _) => acc =>
        updateAccAndTraverse(acc, s)(_acc =>
          TraverseChildrenNewAccForSiblings(
            if (_acc.hasPatternVariables) _acc else _acc.withPatternVariables(variables.toSet),
            acc => acc.inVariableContext(_acc.variableContext)
          )
        )
    case ws: WorkingScope => acc => TraverseChildren(checkWorkingScope(acc, ws))
  }

  // this collects all errors it can find
  private def collectAll(workingScope: WorkingScope): Iterable[SemanticError] = {
    collectSemanticErrors(workingScope).errors
  }

  // this collects the first errors it can find
  private def collectFirst(workingScope: WorkingScope): Iterable[SemanticError] = {
    val errors = collectSemanticErrors(workingScope).errors
    Option.when(errors.nonEmpty)(errors.head)
  }
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

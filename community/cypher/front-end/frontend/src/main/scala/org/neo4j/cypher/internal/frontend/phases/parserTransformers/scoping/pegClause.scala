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

import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.CommandClause
import org.neo4j.cypher.internal.ast.Create
import org.neo4j.cypher.internal.ast.DefaultWith
import org.neo4j.cypher.internal.ast.Delete
import org.neo4j.cypher.internal.ast.Finish
import org.neo4j.cypher.internal.ast.Foreach
import org.neo4j.cypher.internal.ast.FreeProjection
import org.neo4j.cypher.internal.ast.ImportingWithSubqueryCall
import org.neo4j.cypher.internal.ast.InputDataStream
import org.neo4j.cypher.internal.ast.Insert
import org.neo4j.cypher.internal.ast.LoadCSV
import org.neo4j.cypher.internal.ast.Match
import org.neo4j.cypher.internal.ast.Merge
import org.neo4j.cypher.internal.ast.ParsedAsFilter
import org.neo4j.cypher.internal.ast.ParsedAsLet
import org.neo4j.cypher.internal.ast.ParsedAsLimit
import org.neo4j.cypher.internal.ast.ParsedAsOrderBy
import org.neo4j.cypher.internal.ast.ParsedAsSkip
import org.neo4j.cypher.internal.ast.ParsedAsYield
import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Remove
import org.neo4j.cypher.internal.ast.RemoveDynamicPropertyItem
import org.neo4j.cypher.internal.ast.RemoveLabelItem
import org.neo4j.cypher.internal.ast.RemovePropertyItem
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItem
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.ScopeClauseSubqueryCall
import org.neo4j.cypher.internal.ast.SetClause
import org.neo4j.cypher.internal.ast.SetDynamicPropertyItem
import org.neo4j.cypher.internal.ast.SetExactPropertiesFromMapItem
import org.neo4j.cypher.internal.ast.SetIncludingPropertiesFromMapItem
import org.neo4j.cypher.internal.ast.SetLabelItem
import org.neo4j.cypher.internal.ast.SetPropertyItem
import org.neo4j.cypher.internal.ast.SetPropertyItems
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.SubqueryCall
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsBatchParameters
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsConcurrencyParameters
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsErrorParameters
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsParameters
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsReportParameters
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsRetryParameters
import org.neo4j.cypher.internal.ast.UnresolvedCall
import org.neo4j.cypher.internal.ast.Unwind
import org.neo4j.cypher.internal.ast.UseGraph
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.UnPositionedVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeSurveyor.unitVariables
import org.neo4j.cypher.internal.util.ASTNode

object pegClause {

  def apply(clause: Clause, incoming: RegularContext)(implicit c: PegContext): WorkingScope = {
    implicit val astNode: ASTNode = clause
    clause match {

      /**
       * Clause
       */
      // composition
      // Importing With is already deprecated
      case call @ ImportingWithSubqueryCall(query, inTransactionsParameters, _) =>
        val importedVariableSet: Set[LogicalVariable] =
          if (query.isCorrelated && query.importColumns.isEmpty) incoming.variables else query.importColumns.toSet
        val graphSelectionScopes = query.getGraphSelections.map(gs =>
          pegExpression(gs.graphReference, incoming.constantChildContext())
        )
        val innerQueryIncoming = RegularContext(constants = unitVariables, variables = importedVariableSet)
        val innerQuery = query.withoutImportingWithAndGraphSelection
        val scope = if (innerQuery.isDefined) scopeInlineSubquery(
          call,
          incoming,
          importedVariableSet,
          innerQueryIncoming,
          innerQuery.get,
          inTransactionsParameters
        )
        else StatementScope(call, incoming, Set.empty, Declarations.noDeclarations, RegularContext.unit)
        scope.withChildren(scope.children ++ graphSelectionScopes)

      // TODO is this taking the USE clause in to consideration correctly
      case call @ ScopeClauseSubqueryCall(innerQuery, isImportingAll, importedVariables, inTransactionsParameters, _) =>
        val importedVariableSet: Set[LogicalVariable] = importedVariables.toSet
        val innerQueryIncoming =
          if (isImportingAll) incoming.constantChildContext()
          else RegularContext.unitWithConstants(importedVariableSet)
        scopeInlineSubquery(
          call,
          incoming,
          importedVariableSet,
          innerQueryIncoming,
          innerQuery,
          inTransactionsParameters
        )

      // named call
      // Todo we need to handle resolved call as well
      case UnresolvedCall(_, _, declaredArguments, declaredResult, isStandalone, _, _) =>
        val children =
          declaredArguments.map(
            _.map(arg => pegExpression(arg, incoming.constantChildContext()))
          ).getOrElse(Seq.empty)
        val referenced = Some(WorkingScope.referencedInChildren(children))

        /**
         *
         *  In standalone context        | result                             | outgoing     |
         *  -----------------------------|------------------------------------|--------------|
         *  CALL db.labels()             | TableResultWithNotYetKnownColumns  | Set()        |
         *  CALL db.labels() YIELD *     | TableResultWithNotYetKnownColumns  | Set()        |
         *  CALL db.labels() YIELD label | TableResult(Seq(var("label")))     | Set("label") |
         *
         *  In inlined context           | result                             | outgoing     |
         *  -----------------------------|------------------------------------|--------------|
         *  CALL db.labels()             | OmittedResult                      | Set()        |
         *  CALL db.labels() YIELD label | NoResult                           | Set("label") |
         *
         */
        (isStandalone, declaredResult) match {
          case (true, None) =>
            incoming.resultScope(incoming, TableResultWithNotYetKnownColumns, children, referenced)
          case (true, Some(procedureResult)) =>
            val resultColumns = procedureResult.items.map(_.variable)
            incoming.resultScope(
              incoming.amendedWith(resultColumns.toSet),
              TableResult(resultColumns),
              children,
              referenced,
              Declarations(Seq.empty, resultColumns)
            )
          case (false, Some(procedureResult)) =>
            val resultColumns = procedureResult.items.map(_.variable)
            incoming.noResultScope(
              incoming.amendedWith(resultColumns.toSet),
              children,
              referenced,
              Declarations(Seq.empty, resultColumns)
            )
          case (false, _) =>
            incoming.omittedResultScope(incoming, children, referenced)
        }
      // query clauses
      case Unwind(expression, variable) =>
        val children = Seq(pegExpression(expression, incoming.constantChildContext()))
        val declared = Declarations(Seq.empty, Seq(variable))
        incoming.noResultScope(outgoing = incoming.amendedWith(variable), children, declared = declared)

      case Match(_, _, pattern, hints, whereOpt, _) => // TODO implement checking for search here
        val patternScope = pegPattern(pattern, incoming.constantChildContext())
        val subclauseIncoming = incoming.amendedWith(patternScope.outgoing.variables).constantChildContext()
        val hintsScopes = hints.flatMap(h => h.variables.map(pegExpression(_, subclauseIncoming)))
        val whereScopeOpt = whereOpt.map(where =>
          pegExpression(where.expression, subclauseIncoming)
        )
        val children = Seq(Some(patternScope), hintsScopes, whereScopeOpt).flatten
        val referenced = Some(WorkingScope.referencedInChildren(children) intersect incoming.constantsAndVariables)
        val declared = patternScope.declared
        val outgoing = incoming.amendedWith(patternScope.outgoing.variables diff incoming.constants)
        incoming.noResultScope(outgoing, children, referenced, declared)

      // load clause
      case LoadCSV(_, expression, variable, _) =>
        val children = Seq(pegExpression(expression, incoming.constantChildContext()))
        val declared = Declarations(Seq.empty, Seq(variable))
        incoming.noResultScope(outgoing = incoming.amendedWith(variable), children, declared = declared)

      // update clauses
      case Create(pattern) =>
        val patternScope = pegPattern(pattern, incoming.constantChildContext())
        val children = Seq(patternScope)
        val declared = patternScope.declared
        val outgoing = incoming.amendedWith(patternScope.outgoing.variables)
        incoming.omittedResultScope(outgoing, children, declared = declared)
      case Insert(pattern) =>
        val patternScope = pegPattern(pattern, incoming.constantChildContext())
        val children = Seq(patternScope)
        val declared = patternScope.declared
        val outgoing = incoming.amendedWith(patternScope.outgoing.variables)
        incoming.omittedResultScope(outgoing, children, declared = declared)

      case Merge(pattern, actions, whereOpt) =>
        val patternScope = pegPattern(pattern, incoming.constantChildContext())
        // note that the `where` attribute of merge is only populated by rewriters
        // but is populated with predicate that see the variable bound by the pattern
        val inner = incoming.amendedWith(patternScope.outgoing.variables)
        val whereScopeOpt = whereOpt.map(where =>
          pegExpression(where.expression, inner.constantChildContext())
        )
        val actionsScoped = actions.map(ma => apply(ma.action, inner))
        val children = Seq(Some(patternScope), actionsScoped, whereScopeOpt).flatten
        val declared = patternScope.declared
        incoming.omittedResultScope(
          incoming.amendedWith(patternScope.outgoing.variables),
          children,
          declared = declared
        )

      case SetClause(items) =>
        val expressionIncoming = incoming.constantChildContext()
        val children = items.flatMap {
          case SetLabelItem(variable, _, _, _) => Seq(pegExpression(variable, expressionIncoming))
          case SetPropertyItem(property, expression) =>
            Seq(
              pegExpression(property, expressionIncoming),
              pegExpression(expression, expressionIncoming)
            )
          case SetPropertyItems(container, items) =>
            pegExpression(container, expressionIncoming) +: items.map { case (_, expression) =>
              pegExpression(expression, expressionIncoming)
            }
          case SetExactPropertiesFromMapItem(variable, expression, _) =>
            Seq(
              pegExpression(variable, expressionIncoming),
              pegExpression(expression, expressionIncoming)
            )
          case SetIncludingPropertiesFromMapItem(variable, expression, _) =>
            Seq(
              pegExpression(variable, expressionIncoming),
              pegExpression(expression, expressionIncoming)
            )
          case SetDynamicPropertyItem(dynamicPropertyLookup, expression) =>
            Seq(
              pegExpression(dynamicPropertyLookup, expressionIncoming),
              pegExpression(expression, expressionIncoming)
            )
        }
        incoming.forwardWithOmittedResult(children)

      case Remove(items) =>
        val expressionIncoming = incoming.constantChildContext()
        val children = items.flatMap {
          case RemoveLabelItem(variable, _, _, _) =>
            Seq(pegExpression(variable, expressionIncoming))
          case RemovePropertyItem(property) => Seq(pegExpression(property, expressionIncoming))
          case RemoveDynamicPropertyItem(dynamicPropertyLookup) =>
            Seq(pegExpression(dynamicPropertyLookup, expressionIncoming))
        }
        incoming.forwardWithOmittedResult(children)

      case Delete(items, _) =>
        val expressionIncoming = incoming.constantChildContext()
        val children = items.map(item => pegExpression(item, expressionIncoming))
        incoming.forwardWithOmittedResult(children)

      case Foreach(variable, expression, updates) =>
        val expressionIncoming = incoming.constantChildContext()
        val expressionScope = pegExpression(expression, expressionIncoming)
        val subqueryScope =
          pegStatement(
            SingleQuery(updates)(updates.head.position),
            expressionIncoming.amendedWithConstant(variable)
          )
        val children = Seq(expressionScope, subqueryScope)
        val referenced = {
          val subqueryReferenced = subqueryScope.referenced excl variable
          val expressionReferenced = expressionScope.referenced
          Some(subqueryReferenced union expressionReferenced)
        }
        val declared = Declarations(Seq(variable), Seq.empty)
        incoming.omittedResultScope(outgoing = incoming, children, referenced, declared)

      // result/projection clauses
      case Finish() =>
        val children = WorkingScope.noChildren
        incoming.omittedResultScope(RegularContext.unit, children)

      // InputDataStream is not implemented in the parser
      case InputDataStream(variables) =>
        val vars = variables.toSet[LogicalVariable]
        incoming.noResultScope(incoming.amendedWith(vars), Seq.empty, declared = Declarations(Seq.empty, variables))

      case UseGraph(graphReference) =>
        incoming.noResultScope(incoming, Seq(pegExpression(graphReference, incoming.constantChildContext())))

      case projectionClause: ProjectionClause => scopeProjectionClause(projectionClause, incoming)

      // TODO other clause, specifically admin clauses

      case command: CommandClause => pegCommand(command, incoming)

      /**
       * To make match exhaustive
       */
      case _ => UnexpectedAstNodeScopingError(astNode, incoming)
    }
  }

  private def scopeProjectionClause(
    projectionClause: ProjectionClause,
    incoming: RegularContext
  )(implicit c: PegContext): WorkingScope = {
    implicit val astNode: ASTNode = projectionClause

    val (isWith, distinct, projectionType, items, orderByOpt, skipOpt, limitOpt, whereOpt, withTypeOpt) =
      projectionClause match {
        case With(distinct, ReturnItems(projectionType, items, _), orderByOpt, skipOpt, limitOpt, whereOpt, withType) =>
          (
            withType != ParsedAsYield,
            distinct,
            projectionType,
            items,
            orderByOpt,
            skipOpt,
            limitOpt,
            whereOpt,
            Some(withType)
          )
        case Return(distinct, ReturnItems(projectionType, items, _), orderByOpt, skipOpt, limitOpt, _, _, _) =>
          (false, distinct, projectionType, items, orderByOpt, skipOpt, limitOpt, None, None)
        case Yield(ReturnItems(projectionType, items, _), orderByOpt, skipOpt, limitOpt, whereOpt) =>
          (false, false, projectionType, items, orderByOpt, skipOpt, limitOpt, whereOpt, None)
      }
    val variableItems = withTypeOpt match {
      case Some(DefaultWith) => items.filterNot(ri =>
          // removes item "c" and "c AS c" where c is a constant — a special case Cypher historically allows
          ri.alias.exists(v => ri.isPassThrough && (incoming.constants contains v))
        )
      case _ => items
    }
    val newVariables = returnItemAliases(variableItems)
    val declared = Declarations(Seq.empty, newVariables)
    val notShadowed = incoming.variables.filterNot(v => newVariables contains v)
    val includedIncomingVariables =
      if (projectionType == FreeProjection) {
        Set.empty[LogicalVariable]
      } else {
        notShadowed
      }

    val (aggregationItems, groupingItems) = items.partition(pi => pi.expression.containsAggregate)
    if (aggregationItems.isEmpty && !distinct) {
      /*
       * without aggregation
       */
      val projectionItemIncoming = incoming.constantChildContext()
      // NOTE: this could also be "variableItems.map(...)" depending on how we like to think about constants pass through
      val projectionItemScopes =
        items.map(item => pegExpression(item.expression, projectionItemIncoming))
      val subclauseIncoming = projectionClause match {
        case _: Yield => newVariables.toSet union includedIncomingVariables
        case _        => notShadowed union newVariables.toSet
      }
      val sortItemIncoming = incoming.replaceWith(subclauseIncoming).constantChildContext()
      val sortItemScopes = orderByOpt.map(_.sortItems).getOrElse(Seq.empty).map(item =>
        pegExpression(item.expression, sortItemIncoming)
      )
      val whereExpIncoming = incoming.replaceWith(subclauseIncoming).constantChildContext()
      val whereExpScopes = whereOpt.map(w => Seq(w.expression)).getOrElse(Seq.empty).map(expression =>
        pegExpression(expression, whereExpIncoming)
      )

      val skipExpIncoming = incoming.replaceWith(subclauseIncoming).constantChildContext()
      val skipExpScopes = skipOpt.map(s => Seq(s.expression)).getOrElse(Seq.empty).map(expression =>
        pegExpression(expression, skipExpIncoming)
      )

      val limitExpIncoming = incoming.replaceWith(subclauseIncoming).constantChildContext()
      val limitExpScopes = limitOpt.map(s => Seq(s.expression)).getOrElse(Seq.empty).map(expression =>
        pegExpression(expression, limitExpIncoming)
      )

      val children = projectionItemScopes ++ sortItemScopes ++ whereExpScopes ++ skipExpScopes ++ limitExpScopes
      val resultColumns = includedIncomingVariables.toSeq ++ newVariables
      val referencedInChildren = WorkingScope.referencedInChildren(
        projectionItemScopes ++ whereExpScopes ++ skipExpScopes ++ limitExpScopes
      ) union
        (WorkingScope.referencedInChildren(sortItemScopes) intersect notShadowed)
      val starNotReferencing =
        (withTypeOpt contains ParsedAsOrderBy) ||
          (withTypeOpt contains ParsedAsSkip) ||
          (withTypeOpt contains ParsedAsLimit) ||
          (withTypeOpt contains ParsedAsFilter) ||
          (withTypeOpt contains ParsedAsLet)
      val referenced = Some(referencedInChildren union (
        if (starNotReferencing) Set.empty
        else includedIncomingVariables
      ))
      if (isWith) {
        // WITH
        val outgoing = RegularContext(constants = incoming.constants, variables = resultColumns.toSet)
        incoming.noResultScope(outgoing, children, referenced, declared = declared)
      } else {
        // RETURN
        val outgoing = RegularContext(constants = unitVariables, variables = resultColumns.toSet)
        incoming.resultScope(outgoing, TableResult(resultColumns), children, referenced)
      }
    } else {
      /*
       * with aggregation
       */
      // grouping key expressions see all symbols as constants
      val groupingItemIncoming = incoming.constantChildContext()
      // NOTE: this could also be "variableItems.map(...)" depending on how we like to think about constants pass through
      val groupingItemScopes =
        groupingItems.map(item => pegExpression(item.expression, groupingItemIncoming))
      val referencedInGroupingItems = WorkingScope.referencedInChildren(groupingItemScopes)
      val groupingKeyVariables = groupingItems.map(_.expression).collect {
        case v: Variable => v
      }.toSet[LogicalVariable] union includedIncomingVariables
      val newVariablesFromGrouping = returnItemAliases(groupingItems)
      val notShadowedGroupingKeyVariables = groupingKeyVariables.filterNot(v => newVariablesFromGrouping contains v)

      // aggregation expressions see
      val aggregationItemIncoming = {
        // as constants: incoming constants and grouping key variables
        val constants = incoming.constants union groupingKeyVariables
        // as variables: the remaining variables
        val variables = incoming.variables diff constants
        RegularContext(constants, variables)
      }
      val aggregationItemScopes =
        aggregationItems.map(item =>
          pegExpression(item.expression, aggregationItemIncoming.constantChildContext())
        )
      val referencedInAggregationItems = WorkingScope.referencedInChildren(aggregationItemScopes)
      val newVariablesFromAggregation = returnItemAliases(aggregationItems)
      val notShadowedAggregationVariables =
        aggregationItemIncoming.variables.filterNot(v => newVariablesFromAggregation contains v)

      // sort key expressions see
      val sortItemIncoming = {
        // as constants: incoming constants and grouping key variables (potentially shadowed) and grouping key aliases and aggregation aliases
        val constants = incoming.constants union notShadowedGroupingKeyVariables union
          newVariablesFromGrouping.toSet union newVariablesFromAggregation.toSet
        // as variables: the remaining variables (potentially shadowed)
        val variables = notShadowedAggregationVariables
        RegularContext(constants, variables)
      }
      val sortItemScopes =
        orderByOpt.map(_.sortItems).getOrElse(Seq.empty).map(item =>
          pegExpression(item.expression, sortItemIncoming.constantChildContext())
        )
      val referencedInSortItems = WorkingScope.referencedInChildren(sortItemScopes)

      // where expression sees
      val whereExpIncoming = {
        // as constants: incoming constants and grouping key variables (potentially shadowed) and grouping key aliases and aggregation aliases
        val constants = incoming.constants union notShadowedGroupingKeyVariables union
          newVariablesFromGrouping.toSet union newVariablesFromAggregation.toSet
        // as variables: the remaining variables (potentially shadowed)
        val variables = notShadowedAggregationVariables
        RegularContext(constants, variables)
      }
      val whereExpScopes = whereOpt.map(w => Seq(w.expression)).getOrElse(Seq.empty).map(expression =>
        pegExpression(expression, whereExpIncoming.constantChildContext())
      )

      val skipExpScopes = skipOpt.map(s => Seq(s.expression)).getOrElse(Seq.empty).map(expression =>
        pegExpression(expression, whereExpIncoming.constantChildContext())
      )

      val limitExpScopes = limitOpt.map(s => Seq(s.expression)).getOrElse(Seq.empty).map(expression =>
        pegExpression(expression, whereExpIncoming.constantChildContext())
      )

      val referencedInSubclauseExps =
        WorkingScope.referencedInChildren(whereExpScopes ++ skipExpScopes ++ limitExpScopes)

      val referenced = Some(referencedInGroupingItems union referencedInAggregationItems union (
        referencedInSortItems intersect (notShadowedGroupingKeyVariables union notShadowedAggregationVariables)
      ) union (
        referencedInSubclauseExps intersect (notShadowedGroupingKeyVariables union notShadowedAggregationVariables)
      ))
      val resultColumns = includedIncomingVariables.toSeq ++ returnItemAliases(items) // to maintain order
      val children =
        groupingItemScopes ++ aggregationItemScopes ++ sortItemScopes ++ whereExpScopes ++ skipExpScopes ++ limitExpScopes

      if (isWith) {
        // WITH
        val outgoing = RegularContext(constants = incoming.constants, variables = resultColumns.toSet)
        incoming.noResultScope(outgoing, children, referenced, declared = declared)
      } else {
        // RETURN
        val outgoing = RegularContext(constants = unitVariables, variables = resultColumns.toSet)
        incoming.resultScope(outgoing, TableResult(resultColumns), children, referenced)
      }
    }
  }

  @inline private def returnItemAliases(items: Seq[ReturnItem]): Seq[LogicalVariable] =
    items.map(item => item.alias.getOrElse(UnPositionedVariable.varFor(item.name)))

  @inline private def scopeInlineSubquery(
    callClause: SubqueryCall,
    incoming: RegularContext,
    importedVariables: Set[LogicalVariable],
    innerQueryIncoming: RegularContext,
    innerQuery: Query,
    inTransactionsParameters: Option[InTransactionsParameters]
  )(implicit c: PegContext) = {
    implicit val astNode: ASTNode = callClause

    val innerQueryScope = pegStatement(innerQuery, innerQueryIncoming)
    val (inTransactionsChildren, declaredInTransactionsVariables) =
      scopeInTransactionParameters(inTransactionsParameters, incoming.constantChildContext())
    val (outgoing, declaredVariables) = innerQueryScope.result match {
      case TableResult(columns) => (incoming.amendedWith((columns ++ declaredInTransactionsVariables).toSet), columns)
      case TableResultWithNotYetKnownColumns => (incoming.amendedWith(declaredInTransactionsVariables.toSet), Seq.empty)
      case OmittedResult                     => (incoming.amendedWith(declaredInTransactionsVariables.toSet), Seq.empty)
      case NoResult                          =>
        // subquery does not end with the right clause,
        // so this is a best effort:
        (incoming.amendedWith(declaredInTransactionsVariables.toSet), Seq.empty)
      case ExpressionResult =>
        throw new IllegalStateException("inner query cannot have an expression result")
    }
    val children = innerQueryScope +: inTransactionsChildren
    val referenced = Some(innerQueryScope.referenced intersect importedVariables)
    val declared = Declarations(Seq.empty, declaredVariables ++ declaredInTransactionsVariables)
    incoming.noResultScope(outgoing, children, referenced, declared)
  }

  @inline private def scopeInTransactionParameters(
    inTransactionsParameters: Option[InTransactionsParameters],
    incoming: RegularContext
  )(implicit c: PegContext)
    : (Seq[WorkingScope], Seq[LogicalVariable]) = {
    inTransactionsParameters match {
      case None => (Seq.empty, Seq.empty)
      case Some(InTransactionsParameters(batchParams, concurrencyParams, errorParams, reportParams)) =>
        val batchParamsChild = batchParams.toSeq.map {
          case InTransactionsBatchParameters(batchSize) =>
            pegExpression(batchSize, incoming)
        }
        val concurrencyParamsChild = concurrencyParams.toSeq.flatMap {
          case InTransactionsConcurrencyParameters(Some(concurrency)) =>
            Some(pegExpression(concurrency, incoming))
          case _ => None
        }
        val errorParamsChild = errorParams.toSeq.flatMap {
          case InTransactionsErrorParameters(_, Some(InTransactionsRetryParameters(Some(timeout)))) =>
            Some(pegExpression(timeout, incoming))
          case _ => None
        }
        val reportVariable = reportParams.toSeq.map {
          case InTransactionsReportParameters(reportAs) => reportAs
        }
        (batchParamsChild ++ concurrencyParamsChild ++ errorParamsChild, reportVariable)
    }
  }
}

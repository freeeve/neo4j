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
package org.neo4j.cypher.internal.frontend.phases.parserTransformers

import org.neo4j.cypher.internal.ast.AddedInRewriteGeneral
import org.neo4j.cypher.internal.ast.AliasedReturnItem
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.Finish
import org.neo4j.cypher.internal.ast.FreeProjection
import org.neo4j.cypher.internal.ast.FullSubqueryExpression
import org.neo4j.cypher.internal.ast.NextStatement
import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.ScopeClauseSubqueryCall
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.SubqueryCall
import org.neo4j.cypher.internal.ast.TopLevelBraces
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.Unwind
import org.neo4j.cypher.internal.ast.UpdateClause
import org.neo4j.cypher.internal.ast.UseGraph
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.DisableReworkedRewriters
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.CountStar
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Null
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.expressions.Subtract
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.containsAggregate
import org.neo4j.cypher.internal.expressions.functions.Count
import org.neo4j.cypher.internal.expressions.functions.Range
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.StatementRewriter
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.IsolateSubqueriesInMutatingPatterns.SubqueriesInMutatingPatternsIsolated
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.UpToDateScopes
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNextStatements
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoReturnAll
import org.neo4j.cypher.internal.rewriting.conditions.ProjectionClausesHaveSemanticInfo
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

/**
 * ExpandNext rewrites NEXT statements to Cypher which is supported by the planner and runtime.
 * A Next statement is expanded using primarily WITH and CALL subqueries. Observe that statements
 * that require wrapping in CALL have their return names variables anonymized and moved to outside
 * the call to not cause scope issues. Anonymizing the returns also allows for returning unaliased
 * expressions in the last statement.
 *
 * From query:
 * MATCH (n)
 * RETURN n
 *
 * NEXT
 *
 * WHEN n.x > 2 THEN
 * RETURN "large number" AS msg
 * WHEN n.x > 1 THEN
 * RETURN "small number" AS msg
 * ELSE
 * RETURN "tiny number" AS msg
 *
 * NEXT
 *
 * RETURN collect(msg) AS messages
 *
 * To query:
 * MATCH (n)
 * WITH n AS n
 * CALL (*) {
 * WHEN n.x > 2 THEN
 * RETURN "large number" AS `  UNNAMED0`
 * WHEN n.x > 1 THEN
 * RETURN "small number" AS `  UNNAMED0`
 * ELSE
 * RETURN "tiny number" AS `  UNNAMED0`
 * }
 * WITH `  UNNAMED0` AS msg
 * RETURN collect(msg) AS messages*
 *
 */
case object ExpandNext extends StatementRewriter with StepSequencer.Step with ParsePipelineTransformerFactory {

  override def instance(from: BaseState, context: BaseContext): Rewriter = {
    if (context.semanticFeatures.contains(DisableReworkedRewriters))
      getRewriter(from.semantics(), from.anonymousVariableNameGenerator, Set.empty)
    else Rewriter.noop
  }

  override def preConditions: Set[Condition] =
    Set(ProjectionClausesHaveSemanticInfo, SubqueriesInMutatingPatternsIsolated)

  override def postConditions: Set[Condition] = Set(ContainsNoNextStatements)

  override def invalidatedConditions: Set[Condition] =
    Set(ProjectionClausesHaveSemanticInfo, ContainsNoReturnAll, UpToDateScopes)

  def getRewriter(
    state: SemanticState,
    anonVarNameGen: AnonymousVariableNameGenerator,
    imports: Set[String]
  ): Rewriter = {

    case class Acc(
      clauses: Seq[Clause],
      exclude: Set[String],
      incomingColumns: Map[LogicalVariable, LogicalVariable],
      incomingCnt: Option[Variable],
      outgoingColumns: Map[LogicalVariable, LogicalVariable],
      outgoingCnt: Option[Variable]
    ) {
      def addAndShift(cs: Seq[Clause]): Acc =
        copy(clauses ++ cs, incomingColumns = outgoingColumns, incomingCnt = outgoingCnt)

      def setExclude(ex: Set[String]): Acc = copy(exclude = ex)

      def setOutgoing(cols: Map[LogicalVariable, LogicalVariable], cntVar: Option[Variable]): Acc =
        copy(outgoingColumns = cols, outgoingCnt = cntVar)
    }
    case object Acc {
      def empty: Acc = Acc(Seq.empty, Set.empty, Map.empty, None, Map.empty, None)
    }

    def clearScope(acc: Acc, pos: InputPosition): (Acc, Seq[Clause]) = {
      val varName = anonVarNameGen.nextName
      val clearingWith = With(
        ReturnItems(
          FreeProjection,
          Seq(AliasedReturnItem(
            Count(Null()(pos.zeroLength))(pos),
            Variable(varName)(pos, isIsolated = false)
          )(pos))
        )(pos),
        AddedInRewriteGeneral
      )(pos)
      (acc.setExclude(Set(varName)), Seq(clearingWith))
    }

    def calculateOutgoingColumns(acc: Acc, query: Query, needsCollecting: Boolean): Acc = {
      if (!query.isReturning) acc.setOutgoing(Map.empty, None)
      else {
        val calculatedCols = query.getLastSingleQuery.clauses.foldLeft(acc.incomingColumns.keys.map(_.name)) {
          case (_, With(_, ReturnItems(FreeProjection, _, _), _, _, _, _, _)) => Set.empty
          case (cols, _)                                                      => cols
        }

        val explicitReturns = query.returnVariables.explicitVariables
        val expandedReturns = if (query.returnVariables.includeExisting)
          (calculatedCols ++ (query.finalScope(state.scope(query).get).symbolNames -- imports))
            .filter(v => !explicitReturns.exists(_.name == v))
            .map(v => Variable(v)(query.position, isIsolated = false))
            .toSeq
        else Set.empty

        val willBeWrapped = query match {
          case sq: SingleQuery => sq.partitionedClauses.initialGraphSelection.isDefined
          case _               => true
        }
        val anonymize = needsCollecting || willBeWrapped

        val originalAndAnonymized =
          (explicitReturns ++ expandedReturns).map(ev =>
            (
              ev.copyId,
              if (anonymize) Variable(anonVarNameGen.nextName)(ev.position, isIsolated = false) else ev.copyId
            )
          ).toMap

        val cntVar =
          Option.when(needsCollecting)(Variable(anonVarNameGen.nextName)(query.position, isIsolated = false))
        acc.setOutgoing(originalAndAnonymized, cntVar)
      }
    }

    def buildCollectItems(acc: Acc): Option[ReturnItems] = {
      acc.outgoingCnt.map { c =>
        val pos = c.position
        val countStar = AliasedReturnItem(CountStar()(pos), c.copyId)(pos)
        val collectItems = acc.outgoingColumns.values.map(v => {
          AliasedReturnItem(
            FunctionInvocation(FunctionName("collect")(pos), ListLiteral(Seq(v.copyId))(pos))(pos),
            v.copyId
          )(pos)
        }).toSeq
        ReturnItems(FreeProjection, countStar +: collectItems)(pos)
      }
    }

    def anonymizeReturn(r: Return, outgoingColumns: Map[LogicalVariable, LogicalVariable]): Return = {
      val updatedExplicitItems =
        r.returnItems.mapItems(seq => seq.map(ri => ri.withName(outgoingColumns(ri.alias.get).copyId)(ri.position)))
      val expandedItems =
        outgoingColumns
          .filter { case (original, _) => !r.returnItems.items.exists(_.name == original.name) }
          .map { case (original, anonymized) =>
            AliasedReturnItem(original.copyId, anonymized.copyId)(anonymized.position)
          }

      val updatedOrderBy = r.orderBy.map(ord =>
        ord.copy(
          ord.sortItems.map(_.mapExpression(exp =>
            outgoingColumns.foldLeft(exp)((accExpr, pair) =>
              accExpr.replaceAllOccurrencesBy(pair._1, pair._2.copyId)
            )
          ))
        )(ord.position)
      )
      r.copy(
        returnItems = ReturnItems(FreeProjection, updatedExplicitItems.items ++ expandedItems)(r.position),
        orderBy = updatedOrderBy
      )(r.position)
    }

    def anonymizeAndExpandNonWrappedReturn(
      sq: SingleQuery,
      acc: Acc,
      isLast: Boolean
    ): (Acc, Seq[Clause]) =
      (sq.clauses.last, isLast, acc.outgoingCnt.isDefined) match {
        case (r: Return, true, _) =>
          (acc, Seq(r.copy(excludedNames = r.excludedNames ++ acc.exclude)(r.position)))
        case (c, true, _) =>
          (acc, Seq(c))
        case (r: Return, _, false) =>
          (acc, Seq(r.convertToWith))
        case (r: Return, _, true) =>
          (acc, Seq(anonymizeReturn(r, acc.outgoingColumns).convertToWith))
        case (f: Finish, _, _) =>
          clearScope(acc, f.position)
        case (u: UpdateClause, _, _) =>
          val (updatedAcc, clauses) = clearScope(acc, u.position)
          (updatedAcc, u +: clauses)
        case (c, _, _) =>
          (acc, Seq(c))
      }

    def anonymizeAndExpandReturn(sq: SingleQuery, acc: Acc): Clause =
      sq.clauses.last match {
        case r: Return =>
          anonymizeReturn(r, acc.outgoingColumns)
        case c => c
      }

    /**
     * UNWIND range(0, cnt - 1) AS i
     * WITH list_a[i] AS a, ..., list_c[i] AS c
     */
    def insertUnwind(acc: Acc, query: SingleQuery): Seq[Clause] =
      acc.incomingCnt.fold(Seq.empty[Clause])(incomingCnt => {
        val pos = query.position
        val index = Variable(anonVarNameGen.nextName)(pos, isIsolated = false)

        val unwind = Unwind(
          FunctionInvocation(
            FunctionName(Range.name)(pos),
            distinct = false,
            IndexedSeq(
              SignedDecimalIntegerLiteral("0")(pos.zeroLength),
              Subtract(incomingCnt.copyId, SignedDecimalIntegerLiteral("1")(pos.zeroLength))(pos)
            )
          )(pos),
          index.copyId
        )(pos)

        val accessedItems = {
          acc.incomingColumns.map { case (original, anonymized) =>
            AliasedReturnItem(
              ContainerIndex(
                ContainerIndex(anonymized.copyId, index.copyId)(pos),
                SignedDecimalIntegerLiteral("0")(pos.zeroLength)
              )(pos),
              original.copyId
            )(pos)
          }
        }.toSeq

        val unrollingWith = With(ReturnItems(FreeProjection, accessedItems)(pos), AddedInRewriteGeneral)(pos)
        Seq(unwind, unrollingWith)
      })

    def buildSubquery(
      innerQuery: Query,
      acc: Acc,
      isLast: Boolean
    ): (Acc, Seq[Clause]) = {
      val pos = innerQuery.position
      val subQuery =
        ScopeClauseSubqueryCall(innerQuery, isImportingAll = true, Seq.empty, None, optional = false)(pos)

      val returnItems = buildCollectItems(acc).getOrElse(
        ReturnItems(
          FreeProjection,
          acc.outgoingColumns.map { case (o, anon) =>
            AliasedReturnItem(anon.copyId, Variable(o.name)(o.position, isIsolated = false).copyId)(o.position)
          }.toSeq
        )(pos)
      )
      val tailClause = (isLast, innerQuery.isReturning) match {
        case (true, true)  => Seq(Return(returnItems)(pos))
        case (false, true) => Seq(With(returnItems, withType = AddedInRewriteGeneral)(pos))
        case (_, _)        => Seq()
      }

      (acc, subQuery +: tailClause)

    }

    def nestQuery(acc: Acc, query: Query, isLast: Boolean, needsCollecting: Boolean): Acc = {
      val accWithOutgoing = calculateOutgoingColumns(acc, query, needsCollecting)

      val (accWithExclude, subqueryAndReturn) = query match {
        case sq: SingleQuery if sq.partitionedClauses.initialGraphSelection.isEmpty =>
          val unwind = insertUnwind(accWithOutgoing, sq)
          val (updatedAcc, lastClause) =
            anonymizeAndExpandNonWrappedReturn(sq, accWithOutgoing, isLast)
          val collectClause = buildCollectItems(updatedAcc).map(With(_)(sq.position))
          (updatedAcc, unwind ++ sq.clauses.dropRight(1) ++ lastClause ++ collectClause)
        case sq @ SingleQuery(clauses) =>
          val rewrittenQuery =
            sq.copy(
              clauses.head +:
                (insertUnwind(accWithOutgoing, sq) ++ clauses.tail.dropRight(1))
                :+ anonymizeAndExpandReturn(sq, accWithOutgoing)
            )(sq.position)
          buildSubquery(rewrittenQuery, accWithOutgoing, isLast)
        case q: Query =>
          val rewrittenQuery = q.mapEachSingleQuery(
            {
              case sq if sq.partitionedClauses.initialGraphSelection.isDefined =>
                SingleQuery(Seq(sq.clauses.head) ++ insertUnwind(accWithOutgoing, sq) ++ sq.clauses.tail)(sq.position)
              case sq =>
                SingleQuery(insertUnwind(accWithOutgoing, sq) ++ sq.clauses)(sq.position)
            },
            nextFirst = true
          )
            .mapEachSingleQuery(sq =>
              SingleQuery(sq.clauses.dropRight(1) :+ anonymizeAndExpandReturn(sq, accWithOutgoing))(sq.position)
            )
          buildSubquery(rewrittenQuery, accWithOutgoing, isLast)
      }

      accWithExclude.addAndShift(subqueryAndReturn)
    }

    sealed trait QuerySemantics
    case object ByRow extends QuerySemantics
    case object ByTable extends QuerySemantics
    case object RequiresCollecting extends QuerySemantics

    /**
     * Identifies if a query uses By-table semantics when in a NEXT statement
     * If an aggregation, pagination or an update occurs in a ByTable context, then the rewriter
     * must collect and unwind the incoming rows to emulate by-table semantics.
     */
    def requiresCollecting(query: Query): Boolean =
      query.folder.treeFold[QuerySemantics](ByRow) {
        case _: SubqueryCall         => _ => SkipChildren(ByRow)
        case _: ConditionalQueryWhen => _ => SkipChildren(ByRow)
        case _: Union                => _ => SkipChildren(RequiresCollecting)
        case tlb: TopLevelBraces => {
          case _ if tlb.containsUpdates => SkipChildren(RequiresCollecting)
          case _                        => TraverseChildren(ByTable)
        }
        case _: UseGraph => _ => TraverseChildren(ByTable)
        case p: ProjectionClause => {
          case ByTable if p.distinct =>
            SkipChildren(RequiresCollecting)
          case ByTable if p.limit.isDefined || p.skip.isDefined =>
            SkipChildren(RequiresCollecting)
          case acc => TraverseChildren(acc)
        }
        case _: UpdateClause => {
          case ByTable =>
            SkipChildren(RequiresCollecting)
          case acc => TraverseChildren(acc)
        }
        case _: FullSubqueryExpression => acc => SkipChildren(acc)
        case exp: Expression => {
          case ByTable if containsAggregate(exp) =>
            SkipChildren(RequiresCollecting)
          case acc => TraverseChildren(acc)
        }
      } match {
        case RequiresCollecting => true
        case _                  => false
      }

    /**
     * Checks if the current query needs to be collected and then unwrapped in the following query.
     * This is needed when the following query is aggregating or updating and
     * if the current query returns more than one row.
     *
     * This check could be further refined to limit needing to materialize the full working table
     * by expanding this check. Avenues of optimization is exploring SKIP/LIMIT and aggregations.
     */
    def needsCollecting(currentQuery: Query, nextQuery: Query): Boolean =
      currentQuery.isReturning && requiresCollecting(nextQuery)

    def rewriteNextQueries(queries: Seq[Query]): Acc =
      (None +: queries.map(Some(_)) :+ None).sliding(2).foldLeft(Acc.empty) {
        case (acc, Seq(Some(q1), Some(q2))) =>
          nestQuery(acc, q1, isLast = false, needsCollecting(q1, q2))
        case (acc, Seq(Some(q1), None)) =>
          nestQuery(acc, q1, isLast = true, needsCollecting = false)
        case (acc, _) => acc
      }

    def importVariables(call: SubqueryCall): Seq[Variable] =
      state.recordedScopes.get(call)
        .fold(Set.empty[String])(_.scope.symbolNames)
        .map { id => Variable(id)(call.position, Variable.isIsolatedDefault) }
        .toSeq

    topDown(Rewriter.lift {
      // Pass on outer scope to rewriter, leave subquery call as is for future processing
      case clause @ ScopeClauseSubqueryCall(innerQuery, importAll, imports, _, _) =>
        clause.copy(
          innerQuery.endoRewrite(getRewriter(
            state,
            anonVarNameGen,
            (if (importAll) importVariables(clause) else imports).map(_.name).toSet
          )),
          importAll,
          imports
        )(clause.position)
      case ns @ NextStatement(queries) =>
        SingleQuery(rewriteNextQueries(queries).clauses)(ns.position)
    })
  }

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = ExpandNext

}

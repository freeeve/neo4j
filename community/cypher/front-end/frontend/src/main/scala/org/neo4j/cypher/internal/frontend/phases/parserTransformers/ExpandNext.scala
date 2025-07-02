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
import org.neo4j.cypher.internal.ast.AdditiveProjection
import org.neo4j.cypher.internal.ast.AliasedReturnItem
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.Finish
import org.neo4j.cypher.internal.ast.FreeProjection
import org.neo4j.cypher.internal.ast.NextStatement
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.ScopeClauseSubqueryCall
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.expressions.Null
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.functions.Count
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.StatementCondition
import org.neo4j.cypher.internal.frontend.phases.StatementRewriter
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.IsolateSubqueriesInMutatingPatterns.SubqueriesInMutatingPatternsIsolated
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNextStatements
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoReturnAll
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ProjectionClausesHaveSemanticInfo
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

/**
 *  ExpandNext rewrites NEXT statements to Cypher which is supported by the planner and runtime.
 *  A Next statement is expanded using primarily WITH and CALL subqueries. Observe that statements
 *  that require wrapping in CALL have their return names variables anonymized and moved to outside
 *  the call to not cause scope issues. Anonymizing the returns also allows for returning unaliased
 *  expressions in the last statement.
 *
 *  From query:
 *     MATCH (n)
 *     RETURN n
 *
 *     NEXT
 *
 *     WHEN n.x > 2 THEN
 *       RETURN "large number" AS msg
 *     WHEN n.x > 1 THEN
 *       RETURN "small number" AS msg
 *     ELSE
 *       RETURN "tiny number" AS msg
 *
 *     NEXT
 *
 *     RETURN collect(msg) AS messages
 *
 *  To query:
 *     MATCH (n)
 *     WITH n AS n
 *     CALL (*) {
 *       WHEN n.x > 2 THEN
 *         RETURN "large number" AS `  UNNAMED0`
 *       WHEN n.x > 1 THEN
 *         RETURN "small number" AS `  UNNAMED0`
 *       ELSE
 *         RETURN "tiny number" AS `  UNNAMED0`
 *     }
 *     WITH `  UNNAMED0` AS msg
 *     RETURN collect(msg) AS messages*
 *
 */
case object ExpandNext extends StatementRewriter with StepSequencer.Step with ParsePipelineTransformerFactory {

  override def instance(from: BaseState, context: BaseContext): Rewriter =
    getRewriter(from.anonymousVariableNameGenerator)

  override def preConditions: Set[Condition] = Set(BaseContains[SemanticTable](), SubqueriesInMutatingPatternsIsolated)

  override def postConditions: Set[Condition] = Set(StatementCondition(ContainsNoNextStatements))

  override def invalidatedConditions: Set[Condition] = Set(ProjectionClausesHaveSemanticInfo, ContainsNoReturnAll)

  def getRewriter(anonymousVariableNameGenerator: AnonymousVariableNameGenerator): Rewriter = {

    def queryTransformer(query: Query, isLast: Boolean): Seq[Clause] = {
      val pos = query.position

      val returnVars = query.returnVariables
      val anonNames = returnVars.explicitVariables.map(ev =>
        Variable(anonymousVariableNameGenerator.nextName)(ev.position, isIsolated = false)
      )
      val returnsVarsWithAnon = returnVars.explicitVariables.zip(anonNames).toMap

      val updated =
        if (returnsVarsWithAnon.isEmpty) query
        else query.mapEachSingleQuery(sq =>
          sq.copy(clauses =

            sq.clauses.dropRight(1) ++ sq.getReturns.map(r => {
              val updatedItems = r.returnItems.mapItems(seq =>
                seq.map(ri => ri.withName(returnsVarsWithAnon(ri.alias.get).copyId)(pos))
              )
              val updatedOrderBy = r.orderBy.map(ord =>
                ord.copy(
                  ord.sortItems.map(_.mapExpression(exp =>
                    returnsVarsWithAnon.foldLeft(exp)((accExpr, pair) =>
                      accExpr.replaceAllOccurrencesBy(pair._1, pair._2)
                    )
                  ))
                )(ord.position)
              )

              r.copy(returnItems = updatedItems, orderBy = updatedOrderBy)(r.position)
            })
          )(sq.position)
        )
      val sq = Seq(ScopeClauseSubqueryCall(updated, isImportingAll = true, Seq.empty, None, optional = false)(pos))

      val returnItems = ReturnItems(
        if (returnVars.includeExisting) AdditiveProjection else FreeProjection,
        returnsVarsWithAnon.map { case (o, anon) =>
          AliasedReturnItem(anon.copyId, Variable(o.name)(o.position, isIsolated = false))(o.position)
        }.toSeq
      )(pos)

      val tailClause = (isLast, query.isReturning) match {
        case (true, true)  => Seq(Return(returnItems)(pos))
        case (false, true) => Seq(With(returnItems, withType = AddedInRewriteGeneral)(pos))
        case (_, _)        => Seq.empty
      }

      sq ++ tailClause
    }

    case class Acc(clauses: Seq[Clause], exclude: Set[String]) {
      def add(cs: Seq[Clause]): Acc = copy(clauses ++ cs)
      def add(cs: Seq[Clause], ex: Set[String]): Acc = copy(clauses ++ cs, ex)
    }
    case object Acc {
      def empty: Acc = Acc(Seq.empty, Set.empty)
    }

    def checkTrunk(queries: Seq[Query]): Acc = queries.foldLeft(Acc.empty) {
      case (acc, sq: SingleQuery) if sq.partitionedClauses.initialGraphSelection.isDefined =>
        acc.add(queryTransformer(sq, isLast = false))
      case (acc, sq @ SingleQuery(clauses)) if sq.endsWithFinish =>
        val varName = anonymousVariableNameGenerator.nextName
        val rewrittenFinish = clauses.last match {
          case fin: Finish =>
            val pos = fin.position
            val returnItems =
              Seq(AliasedReturnItem(Count(Null()(pos))(pos), Variable(varName)(pos, isIsolated = false))(pos))
            With(ReturnItems(FreeProjection, returnItems)(pos), AddedInRewriteGeneral)(pos)
          case x => x
        }
        acc.add(clauses.dropRight(1) :+ rewrittenFinish, Set(varName))
      case (acc, sq @ SingleQuery(clauses)) if sq.isReturning =>
        val rewrittenReturn = clauses.last match {
          case ret @ Return(distinct, returnItems, orderBy, skip, limit, _, _, _) =>
            With(distinct, returnItems, orderBy, skip, limit, None, AddedInRewriteGeneral)(ret.position)
          case x => x
        }
        acc.add(clauses.dropRight(1) :+ rewrittenReturn)
      case (acc, sq: SingleQuery) => acc.add(sq.clauses)
      case (acc, q)               => acc.add(queryTransformer(q, isLast = false))
    }

    def checkLast(query: Query, exclude: Set[String]): Seq[Clause] = {
      val clauses = query match {
        case sq: SingleQuery if sq.partitionedClauses.initialGraphSelection.isDefined =>
          queryTransformer(sq, isLast = true)
        case _ @SingleQuery(clauses) => clauses
        case q                       => queryTransformer(q, isLast = true)
      }

      val last = clauses.last match {
        case ret @ Return(_, _, _, _, _, excludedNames, _, _) =>
          ret.copy(excludedNames = excludedNames ++ exclude)(ret.position)
        case x => x
      }

      clauses.dropRight(1) :+ last
    }

    topDown(Rewriter.lift {
      case ns @ NextStatement(queries) =>
        val Acc(rewrittenTrunk, exclude) = checkTrunk(queries.dropRight(1))
        val rewrittenLast = checkLast(queries.last, exclude)
        SingleQuery(rewrittenTrunk ++ rewrittenLast)(ns.position)
    })
  }

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = ExpandNext

}

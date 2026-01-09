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
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.Finish
import org.neo4j.cypher.internal.ast.FreeProjection
import org.neo4j.cypher.internal.ast.PartQuery
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItem
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.ScopeClauseSubqueryCall
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.UnionAll
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.expressions.BooleanLiteral
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.False
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.expressions.True
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.StatementRewriter
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.UpToDateScopes
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoConditionalQueries
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNextStatements
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoReturnAll
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ProjectionClausesHaveSemanticInfo
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

/**
 * expandWhen rewrites Conditional Queries to Case Expressions
 * followed by a Scoped Call Subquery with union clauses representing
 * the branches of the WHEN ... THEN ... ELSE ...
 *
 * QUERY
 *    WHEN $param < 1 THEN RETURN 1 AS res
 *    WHEN $param > 1 THEN RETURN 2 AS res
 *    ELSE RETURN 3 AS res
 *
 * REWRITE
 *    WITH *, CASE WHEN $param < 1 THEN [true, false, false] WHEN $param > 1 THEN [false, true, false] ELSE [false, false, true] END AS `  UNNAMED0`
 *    CALL (*) {
 *       // WHEN #1
 *       WITH * WHERE `  UNNAMED0`[0]
 *       // THEN #1
 *       RETURN 1 AS res
 *       UNION ALL
 *       // WHEN #2
 *       WITH * WHERE `  UNNAMED0`[1]
 *       // THEN #2
 *       RETURN 2 AS res
 *       UNION ALL
 *       WITH * WHERE `  UNNAMED0`[2]
 *       // ELSE
 *       RETURN 3 AS res
 *    }
 *    RETURN res
 *
 */
case object ExpandWhen extends StatementRewriter with StepSequencer.Step with ParsePipelineTransformerFactory {

  override def instance(from: BaseState, context: BaseContext): Rewriter =
    getRewriter(from.semantics(), from.anonymousVariableNameGenerator)

  override def preConditions: Set[Condition] =
    Set(ProjectionClausesHaveSemanticInfo, ContainsNoNextStatements)

  override def postConditions: Set[Condition] = Set(ContainsNoConditionalQueries)

  override def invalidatedConditions: Set[Condition] =
    Set(ProjectionClausesHaveSemanticInfo, ContainsNoReturnAll, UpToDateScopes)

  def getRewriter(state: SemanticState, anonymousVariableNameGenerator: AnonymousVariableNameGenerator): Rewriter = {

    def returnItems(
      when: ConditionalQueryWhen,
      listedItems: Seq[ReturnItem],
      excludedNames: Set[String]
    ): ReturnItems = {
      val scope = state.scope(when).getOrElse {
        throw new IllegalStateException(s"When should note its Scope in the SemanticState")
      }

      val clausePos = when.position
      val symbolNames =
        when.finalScope(scope).symbolNames -- excludedNames -- listedItems.map(returnItem => returnItem.name)
      val expandedItems = symbolNames.toSeq.map { id =>
        val expr = Variable(id)(clausePos, Variable.isIsolatedDefault)
        val alias = expr.copyId
        AliasedReturnItem(expr, alias)(clausePos)
      }

      val newItems = expandedItems ++ listedItems
      ReturnItems(FreeProjection, newItems)(clausePos)
    }

    topDown(Rewriter.lift {
      case wh @ ConditionalQueryWhen(branches, default) =>
        val pos = wh.position

        val conditionalListName = anonymousVariableNameGenerator.nextName
        val falseList: List[BooleanLiteral] = List.fill(branches.length + 1)(False()(pos.zeroLength))

        /**
         *  WITH *, CASE
         *          WHEN $param < 1 THEN [true, false, false]
         *          WHEN $param > 1 THEN [false, true, false]
         *          ELSE [false, false, true]
         *  END AS condition
         */
        val selectionWith =
          With(
            ReturnItems(
              AdditiveProjection,
              Seq(AliasedReturnItem(
                CaseExpression(
                  expression = None,
                  alternatives = branches.zipWithIndex.map { case (branch, index) =>
                    (branch.predicate.get, ListLiteral(falseList.updated(index, True()(pos.zeroLength)))(pos))
                  }.toList,
                  default = Some(ListLiteral(falseList.updated(branches.size, True()(pos.zeroLength)))(pos))
                )(pos),
                Variable(conditionalListName)(pos, false)
              )(pos))
            )(pos),
            withType = AddedInRewriteGeneral
          )(pos)

        /**
         * THEN ... QUERY ... --> WITH * WHERE condition[i] ... QUERY ...
         */
        def branchQuery(query: PartQuery, index: Int, pos: InputPosition): SingleQuery = {
          SingleQuery(Seq(
            With(
              false,
              ReturnItems(
                AdditiveProjection,
                Seq.empty
              )(pos),
              None,
              None,
              None,
              Some(
                Where(ContainerIndex(
                  Variable(conditionalListName)(pos, false),
                  SignedDecimalIntegerLiteral(index.toString)(pos.zeroLength)
                )(pos))(pos)
              ),
              withType = AddedInRewriteGeneral
            )(pos)
          ) ++ query.clauses)(pos)
        }

        /**
         * ELSE-query, if no else exists use RETURN vars LIMIT 0 if returning or FINISH if unit query
         */
        val defaultQuery = default.map(d => branchQuery(d.query, branches.size, pos))

        val allBranches = branches.zipWithIndex.map {
          case (branch, idx) =>
            branchQuery(branch.query, idx, pos)
        } ++ defaultQuery

        val branchQueries = allBranches.map { case sq @ SingleQuery(clauses) =>
          val lastWithExclude = clauses.last match {
            case ret: Return => ret.copy(excludedNames = Set(conditionalListName))(ret.position)
            case x           => x
          }
          sq.copy(clauses.dropRight(1) :+ lastWithExclude)(sq.position)
        }

        val subquery = ScopeClauseSubqueryCall(
          branchQueries.tail.foldLeft[Query](branchQueries.head) { case (acc, query) =>
            UnionAll(acc, query)(pos)
          },
          isImportingAll = true,
          Seq.empty,
          None,
          optional = false
        )(pos)

        val tailClause =
          if (wh.isReturning) Return(returnItems(wh, Seq.empty, Set(conditionalListName)))(pos)
          else Finish()(pos)

        SingleQuery(Seq(selectionWith, subquery, tailClause))(pos)
    })
  }

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = ExpandWhen
}

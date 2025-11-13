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

import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.NextStatement
import org.neo4j.cypher.internal.ast.PartQuery
import org.neo4j.cypher.internal.ast.ProjectingUnion
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.QueryWithLocalDefinitions
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.TopLevelBraces
import org.neo4j.cypher.internal.ast.UnionAll
import org.neo4j.cypher.internal.ast.UnionDistinct
import org.neo4j.cypher.internal.ast.UseGraph
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.StatementCondition
import org.neo4j.cypher.internal.frontend.phases.StatementRewriter
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNextStatements
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoReturnAll
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoTopLevelBraces
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ProjectionClausesHaveSemanticInfo
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Step
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

/**
 * unwrapTopLevelBraces makes sure that all TopLevelBraces are either removed or
 * replaced in the AST with a CALL (*) { ... } RETURN * if used as a Union argument.
 *
 * This makes sure that a query such as:
 *    USE outerGraph {
 *          USE innerGraph {
 *             RETURN 1 AS x
 *          }
 *       UNION
 *          USE innerGraph {
 *             RETURN 2 AS x
 *          }
 *       UNION
 *          {
 *             RETURN 3 AS x
 *          }
 *       UNION
 *          RETURN 4 AS x
 *    }
 * Is rewritten to:
 *    CALL (*) {
 *       USE innerGraph
 *       RETURN 1 AS x
 *    }
 *    RETURN *
 *    UNION
 *    CALL (*) {
 *       USE innerGraph
 *       RETURN 2 AS x
 *    }
 *    RETURN *
 *    UNION
 *    CALL (*) {
 *       USE outerGraph
 *       RETURN 3 AS x
 *    }
 *    RETURN *
 *    UNION
 *    USE outerGraph
 *    RETURN 4 AS x  *
 *
 */
case object UnwrapTopLevelBraces extends StatementRewriter with ParsePipelineTransformerFactory with Step {

  override def preConditions: Set[StepSequencer.Condition] =
    Set(
      BaseContains[SemanticTable](),
      StatementCondition(ContainsNoNextStatements)
    )

  override def postConditions: Set[StepSequencer.Condition] = Set(StatementCondition(ContainsNoTopLevelBraces))

  override def invalidatedConditions: Set[StepSequencer.Condition] =
    Set(ContainsNoReturnAll, ProjectionClausesHaveSemanticInfo)

  private def pushDownUse(query: Query, use: Option[UseGraph]): Query = {
    query match {
      case ua: PartQuery               => pushDownUse(ua, use)
      case u @ UnionDistinct(lhs, rhs) => u.copy(pushDownUse(lhs, use), pushDownUse(rhs, use))(u.position)
      case u @ UnionAll(lhs, rhs)      => u.copy(pushDownUse(lhs, use), pushDownUse(rhs, use))(u.position)
      case wh @ ConditionalQueryWhen(branches, default) => wh.copy(
          branches = branches.map(b => b.copy(query = pushDownUse(b.query, use))(b.position)),
          default = default.map(d => d.copy(query = pushDownUse(d.query, use))(d.position))
        )(wh.position)
      case _: ProjectingUnion =>
        throw new IllegalStateException(
          "Didn't expect ProjectingUnion, only SingleQuery, TopLevelBraces, ConditionalWhen, UnionAll, or UnionDistinct."
        )
      case _: NextStatement =>
        throw new IllegalStateException(
          "Didn't expect Next, only SingleQuery, TopLevelBraces, ConditionalWhen, UnionAll, or UnionDistinct."
        )
      case d @ QueryWithLocalDefinitions(_, query) => d.copy(query = pushDownUse(query, use))(d.position)
    }
  }

  private def pushDownUse(query: PartQuery, use: Option[UseGraph]): PartQuery = {
    query match {
      case sq @ SingleQuery(clauses) =>
        if (sq.partitionedClauses.leadingGraphSelection.isDefined) sq else sq.copy(use.toSeq ++ clauses)(sq.position)
      case innerTlb @ TopLevelBraces(_, None)    => innerTlb.copy(use = use)(innerTlb.position)
      case innerTlb @ TopLevelBraces(_, Some(_)) => innerTlb
    }
  }

  private val propagateUse: Rewriter = topDown(Rewriter.lift {
    case tlb @ TopLevelBraces(query, use) => if (use.isDefined) {
        tlb.copy(pushDownUse(query, use), None)(tlb.position)
      } else tlb
  })

  private val rewriter: Rewriter = topDown(Rewriter.lift {
    case u @ UnionDistinct(lhs, rhs) =>
      u.copy(lhs.getQuery(true), rhs.singleQuery)(u.position)
    case u @ UnionAll(lhs, rhs) =>
      u.copy(lhs.getQuery(true), rhs.singleQuery)(u.position)
    case wh @ ConditionalQueryWhen(branches, default) =>
      wh.copy(
        branches = branches.map(b => b.copy(query = b.query.singleQuery)(b.position)),
        default = default.map(d => d.copy(query = d.query.singleQuery)(d.position))
      )(wh.position)
    case tlb: TopLevelBraces =>
      tlb.getQuery(false)
    case _: NextStatement =>
      throw new IllegalStateException(
        "Didn't expect Next, only SingleQuery, TopLevelBraces, ConditionalWhen, UnionAll, or UnionDistinct."
      )
  })

  override def instance(from: BaseState, context: BaseContext): Rewriter = propagateUse andThen rewriter

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = this
}

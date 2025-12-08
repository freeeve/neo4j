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

import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.expressions.PatternComprehension
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.AST_REWRITE
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.frontend.phases.StatementCondition
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.UpToDateScopes
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNodesOfType
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ProjectionClausesHaveSemanticInfo
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ReplacePatternComprehensionWithCollectSubquery
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions.ExpressionsHaveComputedDependencies
import org.neo4j.cypher.internal.rewriting.rewriters.preparatoryRewriters.ReturnItemsAreAliased
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo

case object ReplacePatternComprehensionWithCollectSubqueryRewriter extends Phase[BaseContext, BaseState, BaseState]
    with StepSequencer.Step
    with ParsePipelineTransformerFactory {

  def phase: CompilationPhaseTracer.CompilationPhase = AST_REWRITE

  def process(from: BaseState, context: BaseContext): BaseState =
    from.withStatement(
      from.statement().endoRewrite(ReplacePatternComprehensionWithCollectSubquery.getRewriter(
        from.semantics(),
        Map.empty,
        from.anonymousVariableNameGenerator,
        context.cancellationChecker,
        context.cypherVersion
      ))
    )

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = this

  override def preConditions: Set[StepSequencer.Condition] = Set(
    // When rewriting `RETURN [...]`, we need to have given the ReturnItem an alias before rewriting it to COLLECT
    ReturnItemsAreAliased,
    // The call into Expression.replaceAllOccurrencesBy needs scopeDependencies to be computed.
    ExpressionsHaveComputedDependencies
  ) ++ SemanticInfoAvailable

  override def postConditions: Set[StepSequencer.Condition] =
    Set(StatementCondition(ContainsNoNodesOfType[PatternComprehension]()))

  override def invalidatedConditions: Set[StepSequencer.Condition] =
    Set(
      // It can invalidate this condition by rewriting things inside WITH/RETURN.
      ProjectionClausesHaveSemanticInfo,
      UpToDateScopes
    ) ++ SemanticInfoAvailable

}

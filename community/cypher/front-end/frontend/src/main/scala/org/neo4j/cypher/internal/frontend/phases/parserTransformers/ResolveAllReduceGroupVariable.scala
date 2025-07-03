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
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.expressions.AllReducePredicate
import org.neo4j.cypher.internal.expressions.AllReducePredicate.AllReduceScope
import org.neo4j.cypher.internal.expressions.AllReducePredicate.ReductionStepScope
import org.neo4j.cypher.internal.expressions.AllReducePredicateUnchecked
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.AST_REWRITE
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.frontend.phases.StatementCondition
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNodesOfType
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

case object ResolveAllReduceGroupVariable extends Phase[BaseContext, BaseState, BaseState]
    with StepSequencer.Step
    with ParsePipelineTransformerFactory {

  def phase: CompilationPhaseTracer.CompilationPhase = AST_REWRITE

  override def preConditions: Set[StepSequencer.Condition] = {
    Set(BaseContains[SemanticState]) ++ SemanticInfoAvailable
  }

  override def postConditions: Set[StepSequencer.Condition] =
    Set(StatementCondition(ContainsNoNodesOfType[AllReducePredicateUnchecked]()))

  override def invalidatedConditions: Set[StepSequencer.Condition] =
    SemanticInfoAvailable

  private def rewriter(semanticState: SemanticState): Rewriter = topDown {
    Rewriter.lift {
      case arp: AllReducePredicateUnchecked =>
        val groupVarName = semanticState.resolvedGroupVariables(arp)
        val groupVar = Variable(groupVarName)(arp.position, isIsolated = false)
        AllReducePredicate(
          scope = AllReduceScope(
            accumulator = arp.accumulator,
            reductionStepScope = ReductionStepScope(
              singletonVariable = groupVar.withPosition(arp.reductionStep.position).copyId,
              reductionStep = arp.reductionStep
            )(arp.reductionStep.position),
            predicate = arp.predicate
          )(arp.position),
          groupVariable = groupVar.withPosition(arp.position).copyId,
          init = arp.init
        )(arp.position)
    }
  }

  def process(from: BaseState, context: BaseContext): BaseState =
    from.withStatement(
      from.statement().endoRewrite(rewriter(from.semantics()))
    )

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = this
}

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

import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.UnaliasedReturnItem
import org.neo4j.cypher.internal.ast.semantics.MapExtendedType
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckContext
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckResult
import org.neo4j.cypher.internal.ast.semantics.SemanticChecker
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VariableChecking
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.expressions.ExpressionWithComputedDependencies
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.SEMANTIC_CHECK
import org.neo4j.cypher.internal.frontend.phases.If
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerConfig
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.PreparatoryRewriting.SemanticAnalysisPossible
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeSurveyor
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.VariableChecker
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNodesOfType
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions.ExpressionsHaveComputedDependencies
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

/**
 * Do variable binding, typing, type checking and other semantic checks.
 */
case class SemanticAnalysis(warn: Option[Boolean])
    extends Phase[BaseContext, BaseState, BaseState] {

  override def process(from: BaseState, context: BaseContext): BaseState = {
    val startState =
      SemanticState.clean
        .withFeatures(context.semanticFeatures)
        .semanticCheckHasRunOnce(from.maybeSemanticTable.isDefined)

    val checkContext =
      SemanticCheckContext.Impl(
        context.cypherVersion,
        context.errorMessageProvider,
        Option(context.sessionDatabase)
      )

    val upToDateScopes = ScopeSurveyor.process(from, context)

    val SemanticCheckResult(state, errors) = SemanticChecker.check(from.statement(), startState, checkContext)
    if (warn.getOrElse(!from.maybeSemantics.exists(_.semanticCheckHasRunOnce)))
      state.notifications.foreach(context.notificationLogger.log)

    // feature flag here
    if (context.semanticFeatures.contains(VariableChecking)) {
      val saErrors = errors.filter(VariableChecker.isNotImplementedCode)
      val vcErrors = if (from.maybeSemantics.isEmpty) {
        // Until we remove the feature flag we won't be able to set the correct dependencies for the transformer
        //  without causing a lot of unnecessary rerunning of the ScopeSurveyor. Instead, we run it manually here.
        VariableChecker.gatherAllErrors(upToDateScopes, context)
      } else Seq.empty
      val allErrors = (vcErrors ++ saErrors).sortBy(e => VariableChecker.getErrorOrder(e))
      context.errorHandler(allErrors)
    } else {
      context.errorHandler(errors)
    }

    val cleanedTypeTable =
      state.typeTable
        .view.mapValues {
          _.rewrite {
            case MapExtendedType(outerType, _, _) =>
              outerType
            case otherType => otherType
          }
        }
        .toMap
    val table = from.maybeSemanticTable match {
      case Some(existingTable) =>
        // We might already have a SemanticTable from a previous run, and that might already have tokens.
        // We don't want to lose these
        existingTable.copy(
          types = cleanedTypeTable,
          recordedScopes = state.recordedScopes.view.mapValues(_.scope).toMap
        )
      case None =>
        SemanticTable(types = cleanedTypeTable, recordedScopes = state.recordedScopes.view.mapValues(_.scope).toMap)
    }

    val rewrittenStatement =
      if (errors.isEmpty) {
        // Some expressions record some semantic information in themselves.
        // This is done by the computeDependenciesForExpressions rewriter.
        // We need to apply it after each pass of SemanticAnalysis.
        // Disabled until Namespacer interactions can be solved.
        if (false && context.semanticFeatures.contains(VariableChecking)) {
          from.statement().endoRewrite(
            topDown(Rewriter.lift {
              case x: ExpressionWithComputedDependencies =>
                val scope = upToDateScopes.scopeState().recordedScopes(x)
                x.withComputedIntroducedVariables(scope.declared.allSymbols)
                  .withComputedScopeDependencies(scope.referenced)
            })
          )
        } else {
          from.statement().endoRewrite(computeDependenciesForExpressions(state))
        }
      } else {
        // If we have errors we should rather avoid running computeDependenciesForExpressions, since the state might be incomplete.
        from.statement()
      }

    ScopeSurveyor.process(
      from
        .withStatement(rewrittenStatement)
        .withSemanticState(state)
        .withSemanticTable(table)
        .withSemanticsUpToDate(true),
      context
    )
  }

  override def phase: CompilationPhaseTracer.CompilationPhase = SEMANTIC_CHECK

  override def postConditions: Set[StepSequencer.Condition] = SemanticAnalysis.postConditions
}

case object SemanticAnalysis extends StepSequencer.Step with ParsePipelineTransformerFactory
    with PlanPipelineTransformerFactory {

  override def preConditions: Set[StepSequencer.Condition] = Set(
    BaseContains[Statement](),
    SemanticAnalysisPossible
  )

  override def postConditions: Set[StepSequencer.Condition] = Set(
    BaseContains[SemanticState](),
    ContainsNoNodesOfType[UnaliasedReturnItem](),
    BaseContains[SemanticTable](),
    ExpressionsHaveComputedDependencies
  ) ++ SemanticInfoAvailable

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  /**
   * Transformer for the parse pipeline
   */
  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = ifSemanticsNotUpToDate(warn = None)

  /**
   * Transformer for the plan pipeline
   */
  override def getTransformer(
    planPipelineConfig: PlanPipelineTransformerConfig
  ): Transformer[BaseContext, BaseState, BaseState] = ifSemanticsNotUpToDate(warn = Some(false))

  def ifSemanticsNotUpToDate(warn: Option[Boolean]): Transformer[BaseContext, BaseState, BaseState] =
    If((s: BaseState) => !s.semanticsUpToDate)(SemanticAnalysis(warn))
}

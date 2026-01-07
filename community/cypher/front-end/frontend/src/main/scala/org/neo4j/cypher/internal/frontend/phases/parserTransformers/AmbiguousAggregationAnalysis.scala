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

import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.semantics.SemanticErrorDef
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VariableChecking
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.SEMANTIC_CHECK
import org.neo4j.cypher.internal.frontend.phases.VisitorPhase
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeSurveyor
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.VariableChecker
import org.neo4j.cypher.internal.rewriting.conditions.FunctionInvocationsResolved
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions.ExpressionsHaveComputedDependencies
import org.neo4j.cypher.internal.util.StepSequencer

/**
 * Verify aggregation expressions and make sure there are no ambiguous grouping keys.
 */
case object AmbiguousAggregationAnalysis extends VisitorPhase[BaseContext, BaseState] with StepSequencer.Step {

  override def visit(from: BaseState, context: BaseContext): Unit = {
    val errors = if (from.semantics().features.contains(VariableChecking)) {
      // Until we remove the feature flag we won't be able to set the correct dependencies for the transformer
      //  without causing a lot of unnecessary rerunning of the ScopeSurveyor. Instead, we run it manually here.
      from.statement().folder.fold(Seq.empty[SemanticErrorDef]) {
        // If we project '*', we don't need to check ambiguity since we group on all available variables.
        case projectionClause: ProjectionClause if !projectionClause.returnItems.includeExisting =>
          _ ++ projectionClause.orderBy.toSeq.flatMap(_.checkIllegalOrdering(projectionClause.returnItems))
      } ++ VariableChecker.checkForAmbiguousAggregation(ScopeSurveyor.process(from, context), context)
    } else {
      from.statement().folder.fold(Seq.empty[SemanticErrorDef]) {
        // If we project '*', we don't need to check ambiguity since we group on all available variables.
        case projectionClause: ProjectionClause if !projectionClause.returnItems.includeExisting =>
          _ ++ projectionClause.orderBy.toSeq.flatMap(_.checkIllegalOrdering(projectionClause.returnItems)) ++
            ReturnItems.checkAmbiguousGrouping(projectionClause.returnItems)
      }
    }

    context.errorHandler(errors)
  }

  override def phase: CompilationPhaseTracer.CompilationPhase = SEMANTIC_CHECK

  override def preConditions: Set[StepSequencer.Condition] = Set(
    BaseContains[Statement](),
    // This is needed, because ExpressionWithComputedDependencies will otherwise have an incorrect
    // `.introducedVariables`, which is called in this Phase.
    ExpressionsHaveComputedDependencies,
    FunctionInvocationsResolved
  )

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty
}

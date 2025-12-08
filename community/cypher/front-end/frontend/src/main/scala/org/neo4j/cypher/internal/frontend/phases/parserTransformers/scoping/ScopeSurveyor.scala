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
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.ScopeQueries
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternPart
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.BaseContains
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.cypher.internal.util.topDown

import scala.util.matching.Regex

case class SurveyorNameGenerator() extends AnonymousVariableNameGenerator {
  private var counter = 0
  private val inc = 1

  val generatorName = "SURVEYOR"
  private val prefix = s"  $generatorName"

  def anonymousVarName(counter: Int) =
    s"$prefix$counter"

  override def nextName: String = {
    val result = anonymousVarName(counter)
    counter += inc
    result
  }
}

case object UpToDateScopes extends StepSequencer.Condition

/**
 * Produce a WorkingScope tree that makes it easy to check variable availability for the whole query
 */
case object ScopeSurveyor extends Phase[BaseContext, BaseState, BaseState]
    with StepSequencer.Step
    with ParsePipelineTransformerFactory {

  val unitVariables: Set[LogicalVariable] = Set.empty[LogicalVariable]

  private val namespacing: Regex = """[ ]{2}(?<varName>.*)@\d+""".r

  private val removeNamespacing: Rewriter = Rewriter.lift {
    case v @ Variable(namespacing(varName)) => v.copy(name = varName)(v.position, v.isIsolated)
  }

  override def process(from: BaseState, context: BaseContext): BaseState =
    if (from.maybeScopeState.isEmpty || from.statement != from.scopeState().workingScope.astNode)
      run(from, context)
    else from

  private def run(from: BaseState, context: BaseContext) = {
    val anonVarGen = SurveyorNameGenerator()
    val statement = from.statement().endoRewrite(topDown(removeNamespacing))

    val workingContextOfStatement = scope(
      statement,
      RegularContext.unit,
      PegContext(
        anonVarGen,
        context.cypherVersion,
        context.semanticFeatures.toSet,
        from.maybeScopeState.map(_.recordedScopes).getOrElse(ScopeState.emptyRecordedScopes)
      )
    )
    val recordedScopes = workingContextOfStatement.getRecordedScopes

    val explainScope =
      if (context.semanticFeatures.contains(ScopeQueries) && from.maybeScopeState.isEmpty)
        Some(workingContextOfStatement)
      else from.maybeScopeState.flatMap(x => x.explainScope)

    from.withScopeState(ScopeState(workingContextOfStatement, recordedScopes, explainScope))
  }

  def getTransformerWithoutCheck: Transformer[BaseContext, BaseState, BaseState] =

    new Transformer[BaseContext, BaseState, BaseState] {

      override def transform(from: BaseState, context: BaseContext): BaseState = run(from, context)

      override def postConditions: Set[StepSequencer.Condition] = Set.empty

      override def name: String = "ScopeSurveyor without check statement"
    }

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = ScopeSurveyor

  override val phase = CompilationPhase.VARIABLE_SCOPING

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def preConditions: Set[StepSequencer.Condition] = Set(BaseContains[Statement])

  override def postConditions: Set[StepSequencer.Condition] = Set(BaseContains[WorkingScope](), UpToDateScopes)

  def scope(
    astNode: ASTNode,
    incoming: RegularContext,
    ctx: PegContext
  ): WorkingScope = {
    implicit val c: PegContext = ctx
    astNode match {

      /**
       * Statement
       */
      case statement: Statement => pegStatement(statement, incoming)

      /**
       * Clause
       */
      case clause: Clause => pegClause(clause, incoming)

      /**
       * Expression
       */
      case expression: Expression => pegExpression(expression, incoming)
      case labelExpression: LabelExpression =>
        pegExpression(labelExpression, incoming)

      /**
       * Pattern
       */
      case pattern: Pattern         => pegPattern(pattern, incoming)
      case patternPart: PatternPart => pegPattern(patternPart, incoming)

      /**
       * To make match exhaustive
       */
      case _ => UnexpectedAstNodeScopingError(astNode, incoming)
    }
  }

}

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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternPart
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
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo

/**
 * Produce a WorkingScope tree that makes it easy to check variable availability for the whole query
 */
case object ScopeSurveyor extends Phase[BaseContext, BaseState, BaseState] with StepSequencer.Step
    with ParsePipelineTransformerFactory {

  val unitVariables: Set[LogicalVariable] = Set.empty[LogicalVariable]

  override def process(from: BaseState, context: BaseContext): BaseState = {
    val workingContextOfStatement = scope(from.statement(), RegularContext.unit, context.cypherVersion)
    from.withWorkingScope(workingContextOfStatement)
  }

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] = ScopeSurveyor

  override val phase = CompilationPhase.VARIABLE_SCOPING

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def preConditions: Set[StepSequencer.Condition] = Set.empty

  override def postConditions: Set[StepSequencer.Condition] = Set(BaseContains[WorkingScope]())

  def scope(astNode: ASTNode, incoming: RegularContext, version: CypherVersion): WorkingScope = {
    astNode match {

      /**
       * Statement
       */
      case statement: Statement => pegStatement(statement, incoming, version)

      /**
       * Clause
       */
      case clause: Clause => pegClause(clause, incoming, version)

      /**
       * Expression
       */
      case expression: Expression           => pegExpression(expression, incoming, version)
      case labelExpression: LabelExpression => pegExpression(labelExpression, incoming, version)

      /**
       * Pattern
       */
      case pattern: Pattern         => pegPattern(pattern, incoming, version)
      case patternPart: PatternPart => pegPattern(patternPart, incoming, version)

      /**
       * To make match exhaustive
       */
      case _ => UnexpectedAstNodeScopingError(astNode, incoming)
    }
  }
}

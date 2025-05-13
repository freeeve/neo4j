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
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.UnscopedCaseExpressions
import org.neo4j.cypher.internal.expressions.AnonymousScopeExpression
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.Literal
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.Namespacer
import org.neo4j.cypher.internal.frontend.phases.StatementCondition
import org.neo4j.cypher.internal.frontend.phases.StatementRewriter
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.ParsePipelineTransformerFactory
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNodesOfType
import org.neo4j.cypher.internal.rewriting.conditions.DoNotAffectPrettifier
import org.neo4j.cypher.internal.rewriting.conditions.NoReferenceEqualityAmongVariables
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.LiteralExtractionStrategy
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions.ExpressionsHaveComputedDependencies
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.Foldable
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.symbols.ParameterTypeInfo
import org.neo4j.util.VisibleForTesting

case object CaseExpressionsAreIsolated extends StepSequencer.Condition

/**
 * Removes CaseExpression.Operand expressions from case expressions.
 *
 * CASE rand() WHEN < 0.5 THEN true ELSE false END ==>
 *   Bind expression variable anon1 = rand(): CASE anon1 WHEN < 0.5 THEN true ELSE false END
 *
 * CASE r WHEN < 0.5 THEN true ELSE false END ==>
 *   Not rewritten, except CaseExpression.Operand is replaced with `r`
 */
case class ScopeCaseExpressions(scoped: Boolean = true) extends StatementRewriter with StepSequencer.Step
    with ParsePipelineTransformerFactory {

  override def instance(from: BaseState, context: BaseContext): Rewriter =
    instance(from.anonymousVariableNameGenerator, context.cancellationChecker)

  @VisibleForTesting
  @inline
  def instance(
    nameGenerator: AnonymousVariableNameGenerator,
    cancellation: CancellationChecker
  ): Rewriter = {
    if (scoped) scopingRewriter(nameGenerator, cancellation)
    else bottomUp(Rewriter.lift { case c @ CaseExpression(Some(candidate), _, _) => replaceOperands(c, candidate) })
  }

  override def phaseValidation[T >: BaseState](from: BaseState, to: T): Unit = {
    DoNotAffectPrettifier.validate(from.statement(), to.asInstanceOf[BaseState].statement())
  }

  override def preConditions: Set[StepSequencer.Condition] = Set.empty

  override def invalidatedConditions: Set[StepSequencer.Condition] = SemanticInfoAvailable ++ Set(
    ExpressionsHaveComputedDependencies,
    Namespacer.completed, // We introduce vars
    NoReferenceEqualityAmongVariables // Probably not needed but added to be safe since we introduce vars.
  )

  override def postConditions: Set[StepSequencer.Condition] = Set(
    CaseExpressionsAreIsolated,
    StatementCondition(ContainsNoNodesOfType[CaseExpression.Operand]())
  )

  override def getTransformer(
    literalExtractionStrategy: LiteralExtractionStrategy,
    parameterTypeMapping: Map[String, ParameterTypeInfo],
    semanticFeatures: Seq[SemanticFeature],
    obfuscateLiterals: Boolean
  ): Transformer[BaseContext, BaseState, BaseState] =
    ScopeCaseExpressions(!semanticFeatures.contains(UnscopedCaseExpressions))

  private def replaceOperands(c: CaseExpression, operand: => Expression): CaseExpression =
    c.copy(alternatives = c.alternatives.map { case (whenExp, thenExp) =>
      whenExp.endoRewrite(bottomUp(Rewriter.lift { case CaseExpression.Operand() => operand })) -> thenExp
    })(c.position)

  private def containsOperand(f: Foldable): Boolean = f.folder.treeFindByClass[CaseExpression.Operand].isDefined

  private def scopingRewriter(
    nameGenerator: AnonymousVariableNameGenerator,
    cancellation: CancellationChecker
  ): Rewriter = bottomUp(
    rewriter = Rewriter.lift {
      case c @ CaseExpression(Some(candidate), alts, _) if containsOperand(alts) =>
        candidate match {
          // We do NOT scope cheap expressions
          case _: LogicalVariable | _: Literal | _: Parameter => replaceOperands(c, candidate)

          // We bind an anonymous scoped variable for case expressions with more complex candidate expressions.
          // - in-deterministic expressions, required for correctness.
          // - more complex expressions, as an optimisation (see CaseExpressionExpensive benchmark).
          case _ =>
            // Make copies of the following variable to not break NoReferenceEqualityAmongVariables
            val variable = Variable(nameGenerator.nextName)(candidate.position, isIsolated = false)
            AnonymousScopeExpression(
              anonVariable = variable.copyId,
              scopeVariableExpression = candidate,
              innerExpression = replaceOperands(c, variable).copy(expression = Some(variable))(c.position)
            )
        }
    },
    cancellation = cancellation
  )
}

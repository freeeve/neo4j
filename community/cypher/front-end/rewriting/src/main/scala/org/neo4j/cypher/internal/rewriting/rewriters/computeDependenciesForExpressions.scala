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
package org.neo4j.cypher.internal.rewriting.rewriters

import org.neo4j.cypher.internal.ast.semantics.Scope.DeclarationsAndDependencies
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics.SymbolUse
import org.neo4j.cypher.internal.expressions.ExpressionWithComputedDependencies
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.topDown

case class computeDependenciesForExpressions(semanticState: SemanticState) extends Rewriter {

  def apply(that: AnyRef): AnyRef = instance.apply(that)

  private val instance: Rewriter = topDown(Rewriter.lift {
    case x: ExpressionWithComputedDependencies =>
      val DeclarationsAndDependencies(declarations, dependencies) = getForExpression(x)

      x.withComputedIntroducedVariables(declarations.map(_.asVariable))
        .withComputedScopeDependencies(dependencies.map(_.asVariable))
  })

  private def getForExpression(x: ExpressionWithComputedDependencies): DeclarationsAndDependencies = {
    val DeclarationsAndDependencies(declarations, dependencyDefinitions) =
      semanticState.recordedScopes(x.subqueryAstNode)
        .declarationsAndDependenciesForExpressions

    // Because the dependencies returned by declarationsAndDependenciesForExpressions are calculated using the
    // definition in the symbol table and therefore have the position of the original definition, we need to find the
    // variables in our expression that reference these definitions to be able to report errors in the right position.
    val dependencyVariableNames = dependencyDefinitions.map(_.name)
    val dependencies =
      x.subqueryAstNode.folder.treeCollect {
        case variable: Variable if dependencyVariableNames.contains(variable.name) =>
          SymbolUse(variable)
      }
    DeclarationsAndDependencies(declarations, dependencies.toSet)
  }
}

case object computeDependenciesForExpressions {
  case object ExpressionsHaveComputedDependencies extends StepSequencer.Condition
}
